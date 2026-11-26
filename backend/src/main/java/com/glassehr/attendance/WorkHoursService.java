package com.glassehr.attendance;

import com.glassehr.attendance.DailyWorkSummaryMapper.PunchRecord;
import com.glassehr.attendance.WorkHoursCalculator.DayInput;
import com.glassehr.attendance.WorkHoursCalculator.DayKind;
import com.glassehr.attendance.WorkHoursCalculator.DayResult;
import com.glassehr.attendance.WorkHoursCalculator.Interval;
import com.glassehr.attendance.WorkHoursCalculator.Leave;
import com.glassehr.employee.EmployeeRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일별 근로시간 집계 저장·재계산(#29). 원자료(출퇴근 기록·근무유형 배정·휴일 달력·승인된 휴가)를 모아
 * {@link WorkHoursCalculator}로 계산하고 attendance_daily_summary(V023)에 다시 쓴다. 설계: docs/work-hours-design.md
 *
 * <p>1주 연장(근로기준법 제50조 제1항)이 주 전체에 걸리므로, 요청 기간을 월요일~일요일로 넓혀 계산·저장한다.
 */
@Service
public class WorkHoursService {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 한 번에 재계산할 수 있는 최대 일수(운영 보호용, 법정 수치 아님). */
    static final int MAX_RANGE_DAYS = 93;

    static final String ISSUE_NO_WORK_PATTERN = "NO_WORK_PATTERN";
    static final String ISSUE_MISSING_OUT = "MISSING_OUT";
    /** 연장·휴일근로 실적이 있는데 그날 승인된 사전 신청(#30)이 없음. 관리자 확인 대상(계산값은 그대로). */
    static final String ISSUE_OVERTIME_NOT_APPROVED = "OVERTIME_NOT_APPROVED";

    private final DailyWorkSummaryMapper mapper;
    private final EmpWorkPatternRepository assignments;
    private final WorkPatternRepository patterns;
    private final HolidayService holidays;
    private final ApprovedLeaveProvider leaves;
    private final ApprovedOvertimeProvider overtimes;
    private final WorkHoursLegalRules legalRules;
    private final EmployeeRepository employees;
    private final WorkPolicyService policies;
    private final Clock clock;

    @Autowired
    public WorkHoursService(DailyWorkSummaryMapper mapper, EmpWorkPatternRepository assignments,
            WorkPatternRepository patterns, HolidayService holidays, ApprovedLeaveProvider leaves,
            ApprovedOvertimeProvider overtimes, WorkHoursLegalRules legalRules, EmployeeRepository employees,
            WorkPolicyService policies) {
        this(mapper, assignments, patterns, holidays, leaves, overtimes, legalRules, employees, policies,
                Clock.system(SEOUL));
    }

    WorkHoursService(DailyWorkSummaryMapper mapper, EmpWorkPatternRepository assignments,
            WorkPatternRepository patterns, HolidayService holidays, ApprovedLeaveProvider leaves,
            ApprovedOvertimeProvider overtimes, WorkHoursLegalRules legalRules, EmployeeRepository employees,
            WorkPolicyService policies, Clock clock) {
        this.policies = policies;
        this.mapper = mapper;
        this.assignments = assignments;
        this.patterns = patterns;
        this.holidays = holidays;
        this.leaves = leaves;
        this.overtimes = overtimes;
        this.legalRules = legalRules;
        this.employees = employees;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DailyWorkSummaryRow> find(String enterCd, long empId, LocalDate from, LocalDate to) {
        checkRange(from, to);
        requireEmployee(enterCd, empId);
        return mapper.findRange(enterCd, empId, from, to);
    }

    /** 세션 현재 법인 사원만. 다른 법인 사원은 존재 여부를 드러내지 않고 404(#125·#126). */
    private void requireEmployee(String enterCd, long empId) {
        if (employees.findByEnterCdAndEmpId(enterCd, empId).isEmpty()) {
            throw AttendanceException.notFound("사원이 없어요: " + empId);
        }
    }

    /**
     * [from, to]를 주 단위(월~일)로 넓혀 다시 계산하고 그 기간 행을 지운 뒤 새로 넣는다. 넓힌 기간의 결과를 돌려준다.
     * 기준값이 없으면 LegalRateException(409) — 아무 행도 바꾸지 않는다.
     */
    @Transactional
    public List<DailyWorkSummaryRow> recalculate(String enterCd, long empId, LocalDate from, LocalDate to,
            String actor) {
        checkRange(from, to);
        // 휴일 달력·근무유형·출퇴근 기록·집계 행도 그 법인 것만 쓴다.
        requireEmployee(enterCd, empId);
        LocalDate weekStart = from.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = to.with(DayOfWeek.SUNDAY);
        Set<LocalDate> holidayDates = holidays.holidayDates(enterCd, weekStart, weekEnd.plusDays(2));
        Map<LocalDate, WorkHoursLegalRules.Applied> appliedByDate = new HashMap<>();
        List<DailyWorkSummaryRow> rows = compute(enterCd, empId, weekStart, weekEnd, holidayDates, appliedByDate,
                actor);
        mapper.deleteRange(enterCd, empId, weekStart, weekEnd);
        for (DailyWorkSummaryRow row : rows) {
            mapper.insert(enterCd, row);
        }
        return rows;
    }

    /**
     * 같은 법인 여러 사원의 같은 주 계산에 함께 쓰는 자료(휴일 달력·legal_rate 기준값). {@link #weekInputs}로 만든다.
     * 휴일 달력은 법인마다 다르므로 enterCd 법인 사원에게만 쓴다(#125).
     */
    public record WeekInputs(String enterCd, LocalDate monday, Set<LocalDate> holidayDates,
            Map<LocalDate, WorkHoursLegalRules.Applied> appliedByDate) {
    }

    /** {@code date}가 속한 주(월~일)의 enterCd 법인 공유 자료를 한 번 읽는다. 기준값이 없으면 LegalRateException(409). */
    @Transactional(readOnly = true)
    public WeekInputs weekInputs(String enterCd, LocalDate date) {
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        Map<LocalDate, WorkHoursLegalRules.Applied> applied = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            applied.put(monday.plusDays(i), legalRules.asOf(monday.plusDays(i)));
        }
        return new WeekInputs(enterCd, monday, holidays.holidayDates(enterCd, monday, monday.plusDays(8)),
                Map.copyOf(applied));
    }

    /**
     * 한 사원의 한 주를 저장하지 않고 계산한다(#31 주 52시간 모니터링 — 아직 재계산하지 않은 오늘까지의 기록도 반영).
     * 계산 규칙은 {@link #recalculate}와 같다. 사원은 week.enterCd() 법인 사원이어야 한다(호출하는 쪽이 확인).
     */
    @Transactional(readOnly = true)
    public List<DailyWorkSummaryRow> computeWeek(long empId, WeekInputs week) {
        return compute(week.enterCd(), empId, week.monday(), week.monday().plusDays(6), week.holidayDates(),
                new HashMap<>(week.appliedByDate()), PREVIEW_ACTOR);
    }

    /** 저장하지 않는 계산 결과의 calculatedBy 값. */
    static final String PREVIEW_ACTOR = "PREVIEW";

    private List<DailyWorkSummaryRow> compute(String enterCd, long empId, LocalDate weekStart, LocalDate weekEnd,
            Set<LocalDate> holidayDates, Map<LocalDate, WorkHoursLegalRules.Applied> appliedByDate, String actor) {
        // 다음 근무일 첫 출근까지 봐야 마지막 날 야간 근무의 퇴근을 찾는다.
        List<PunchRecord> punchList = mapper.findPunches(enterCd, empId, weekStart, weekEnd.plusDays(2));
        List<EmpWorkPattern> assignList = assignments.findByEnterCdAndEmpIdOrderByValidFromAsc(enterCd, empId);
        Map<Long, WorkPattern> patternById = new HashMap<>();
        for (EmpWorkPattern a : assignList) {
            patternById.computeIfAbsent(a.getPatternId(), id -> patterns.findByEnterCdAndPatternId(enterCd, id).orElseThrow(
                    () -> new IllegalStateException("배정된 근무유형이 없어요: " + id)));
        }
        Map<LocalDate, Leave> leaveByDate = leaves.approvedLeaves(empId, weekStart, weekEnd);
        Set<LocalDate> approvedOvertime = overtimes.approvedDates(empId, weekStart, weekEnd);
        LocalDateTime now = LocalDateTime.now(clock.withZone(SEOUL)).truncatedTo(ChronoUnit.MICROS);
        // 근무 정책(#159): 근무유형 재정의 → 법인 기본 → 시스템 기본(정책 도입 전과 같은 결과).
        WorkPolicyService.Resolved resolved = policies.resolved(enterCd);

        List<DailyWorkSummaryRow> rows = new ArrayList<>();
        for (LocalDate monday = weekStart; !monday.isAfter(weekEnd); monday = monday.plusWeeks(1)) {
            List<DayInput> inputs = new ArrayList<>(7);
            List<Meta> metas = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                LocalDate d = monday.plusDays(i);
                Optional<WorkPattern> pattern = patternOn(assignList, patternById, d);
                Set<String> issues = new LinkedHashSet<>();
                if (pattern.isEmpty()) {
                    issues.add(ISSUE_NO_WORK_PATTERN);
                }
                DayKind kind = dayKind(pattern, holidayDates, d);
                boolean scheduledDay = kind == DayKind.WORKDAY && pattern.isPresent();
                int contract = scheduledDay ? pattern.get().getDailyContractMinutes() : 0;
                WorkPolicyRules.Policy policy = resolved.forPattern(pattern.map(WorkPattern::getPatternId).orElse(null));
                Shift shift = pairShift(punchList, d);
                LocalDateTime schedStart = null;
                LocalDateTime schedEnd = null;
                if (scheduledDay) {
                    WorkPatternRules.Spec spec = pattern.get().spec();
                    schedStart = d.atStartOfDay().plusMinutes(spec.workStartMin());
                    schedEnd = schedStart.plusMinutes(
                            WorkPatternRules.spanMinutes(spec.workStartMin(), spec.workEndMin()));
                }
                // 정시 인정·이른 출근·퇴근 누락·계획 기준을 정책으로 적용한 집계 구간. 행의 출퇴근 시각은 원래 기록 그대로.
                WorkPolicyRules.Recorded rec = WorkPolicyRules.record(policy, shift.start(), shift.end(), schedStart,
                        schedEnd);
                issues.addAll(rec.issues());
                List<Interval> work = rec.end() == null ? List.of() : List.of(new Interval(rec.start(), rec.end()));
                List<Interval> breaks = pattern.map(p -> scheduledBreaks(p.spec(), d)).orElse(List.of());
                WorkHoursLegalRules.Applied applied = appliedByDate.computeIfAbsent(d, legalRules::asOf);
                inputs.add(new DayInput(d, kind, contract, work, breaks, leaveByDate.get(d),
                        applied.rules()));
                metas.add(new Meta(pattern.map(WorkPattern::getPatternId).orElse(null), shift, issues, applied,
                        policy));
            }
            List<DayResult> results = WorkHoursCalculator.calculateWeek(inputs);
            int otWeek = 0;
            int nightWeek = 0;
            int holWeek = 0;
            for (int i = 0; i < results.size(); i++) {
                Meta meta = metas.get(i);
                WorkPolicyRules.Policy policy = meta.policy();
                // 인정 단위·올림/내림·법내 초과 구분. 휴일 기준시간(근로기준법 제56조 제2항)은 legal_rate 적용값.
                DayResult r = WorkPolicyRules.recognizeDay(results.get(i), policy,
                        meta.applied().rules().holidayThresholdMinutes());
                int hol = r.holidayMinutes() + r.holidayExcessMinutes();
                otWeek += r.overtimeMinutes();
                nightWeek += r.nightMinutes();
                holWeek += hol;
                // 허용 방식 위반은 표시만 한다. 실제로 일한 시간은 줄이지 않는다(근로기준법 제43조).
                addIfPresent(meta.issues(), WorkPolicyRules.allowanceIssue("OVERTIME", policy.overtime(),
                        r.overtimeMinutes(), otWeek));
                addIfPresent(meta.issues(), WorkPolicyRules.allowanceIssue("NIGHT", policy.night(),
                        r.nightMinutes(), nightWeek));
                addIfPresent(meta.issues(), WorkPolicyRules.allowanceIssue("HOLIDAY", policy.holiday(), hol, holWeek));
                if (r.overtimeMinutes() + r.holidayMinutes() + r.holidayExcessMinutes() > 0
                        && !approvedOvertime.contains(r.workDate())) {
                    metas.get(i).issues().add(ISSUE_OVERTIME_NOT_APPROVED);
                }
                rows.add(toRow(empId, r, metas.get(i), actor, now));
            }
        }
        return rows;
    }

    private record Meta(Long patternId, Shift shift, Set<String> issues, WorkHoursLegalRules.Applied applied,
            WorkPolicyRules.Policy policy) {
    }

    private static void addIfPresent(Set<String> issues, String issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }

    /** 근무일 하루의 출근·퇴근. 퇴근을 못 찾으면 end = null. */
    record Shift(LocalDateTime start, LocalDateTime end) {
    }

    /**
     * 근무일 d 의 근무: 시작은 그날 첫 출근(docs/DECISIONS.md #28 '첫 출근·마지막 퇴근').
     * 끝은 시작 뒤의 마지막 퇴근으로, 다음 근무일의 첫 출근 전이고 시작부터 24시간 이내인 것(자정을 넘는 근무 포함).
     */
    static Shift pairShift(List<PunchRecord> sortedPunches, LocalDate d) {
        LocalDateTime start = null;
        for (PunchRecord p : sortedPunches) {
            if (p.isIn() && p.workDate().equals(d)) {
                start = p.punchedAt();
                break;
            }
        }
        if (start == null) {
            return new Shift(null, null);
        }
        LocalDateTime limit = start.plusHours(24);
        for (PunchRecord p : sortedPunches) {
            if (p.isIn() && p.workDate().isAfter(d) && p.punchedAt().isBefore(limit)) {
                limit = p.punchedAt();
            }
        }
        LocalDateTime end = null;
        for (PunchRecord p : sortedPunches) {
            if (p.isOut() && p.punchedAt().isAfter(start) && !p.punchedAt().isAfter(limit)) {
                end = p.punchedAt();
            }
        }
        LocalDateTime maxEnd = d.plusDays(2).atStartOfDay();
        if (end != null && end.isAfter(maxEnd)) {
            end = null;
        }
        return new Shift(start, end);
    }

    /** 근무유형의 휴게 한 구간을 근무일 시각으로. 휴게 시작이 시업보다 이르면 다음 날(야간조). */
    static List<Interval> scheduledBreaks(WorkPatternRules.Spec spec, LocalDate d) {
        if (spec.breakMinutes() == 0 || spec.breakStartMin() == null) {
            return List.of();
        }
        LocalDate day = spec.breakStartMin() >= spec.workStartMin() ? d : d.plusDays(1);
        LocalDateTime start = day.atStartOfDay().plusMinutes(spec.breakStartMin());
        return List.of(new Interval(start, start.plusMinutes(spec.breakMinutes())));
    }

    /**
     * 근무유형 요일 구분(W 소정근로일·O 휴무일·H 주휴일, 근로기준법 제55조 제1항)과 휴일 달력(제55조 제2항, 회사휴일 포함)으로 정한다.
     * 휴일 달력에 있으면 휴일. 근무유형이 없으면 휴일 달력에 없는 날은 소정근로일로 본다(NO_WORK_PATTERN 표시).
     */
    static DayKind dayKind(Optional<WorkPattern> pattern, Set<LocalDate> holidayDates, LocalDate d) {
        if (holidayDates.contains(d)) {
            return DayKind.HOLIDAY;
        }
        if (pattern.isEmpty()) {
            return DayKind.WORKDAY;
        }
        char c = pattern.get().spec().dayTypes().charAt(d.getDayOfWeek().getValue() - 1);
        return switch (c) {
            case 'W' -> DayKind.WORKDAY;
            case 'O' -> DayKind.OFFDAY;
            case 'H' -> DayKind.HOLIDAY;
            default -> throw new IllegalStateException("요일 구분 값이 잘못됐어요: " + c);
        };
    }

    private static Optional<WorkPattern> patternOn(List<EmpWorkPattern> assignList, Map<Long, WorkPattern> byId,
            LocalDate d) {
        return assignList.stream().filter(a -> a.isEffectiveOn(d)).findFirst().map(a -> byId.get(a.getPatternId()));
    }

    private static DailyWorkSummaryRow toRow(long empId, DayResult r, Meta m, String actor, LocalDateTime now) {
        WorkHoursLegalRules.Rates rates = m.applied().rates();
        return new DailyWorkSummaryRow(empId, r.workDate(), r.dayKind().name(), m.patternId(), r.contractMinutes(),
                r.leaveKind() == null ? null : r.leaveKind().name(), r.leaveMinutes(),
                m.shift().start(), m.shift().end(), r.breakMinutes(), r.workedMinutes(), r.regularMinutes(),
                r.extraMinutes(), r.overtimeMinutes(), r.nightMinutes(), r.holidayMinutes(), r.holidayExcessMinutes(),
                rates.overtime(), rates.night(), rates.holiday(), rates.holidayExcess(),
                m.issues().isEmpty() ? null : String.join(",", m.issues()), actor, now);
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw AttendanceException.badRequest("기간(from, to)을 보내 주세요");
        }
        if (to.isBefore(from)) {
            throw AttendanceException.badRequest("끝 날짜가 시작 날짜보다 앞이에요");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw AttendanceException.badRequest("한 번에 " + MAX_RANGE_DAYS + "일까지 볼 수 있어요");
        }
    }
}
