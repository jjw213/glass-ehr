package com.glassehr.attendance;

import com.glassehr.attendance.FlexibleWorkCalculator.ActualDay;
import com.glassehr.attendance.FlexibleWorkCalculator.Check;
import com.glassehr.attendance.FlexibleWorkCalculator.DiscretionaryJob;
import com.glassehr.attendance.FlexibleWorkCalculator.DiscretionaryResult;
import com.glassehr.attendance.FlexibleWorkCalculator.FlexSettlement;
import com.glassehr.attendance.FlexibleWorkCalculator.Limits;
import com.glassehr.attendance.FlexibleWorkCalculator.PlanDay;
import com.glassehr.attendance.FlexibleWorkCalculator.SelectiveDay;
import com.glassehr.attendance.FlexibleWorkCalculator.SelectiveJob;
import com.glassehr.attendance.FlexibleWorkCalculator.SelectiveResult;
import com.glassehr.attendance.FlexibleWorkCalculator.WorkSystem;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유연근무제 배정·근무계획·정산(#38). 판정은 {@link FlexibleWorkCalculator}, 한도는 {@link FlexibleWorkLegalRules}
 * (legal_rate, 단위기간 시작일 기준). 설계: docs/work-schedule-types-design.md
 *
 * <ul>
 *   <li>배정: 근로기준법 제51조 제2항·제51조의2 제1항·제52조 제1항·제58조 제3항은 근로자대표와의 서면합의가 전제라,
 *       기간 전체를 덮는 유효한 합의서가 없으면 {@link LaborAgreementGuard#requireValid}가 422로 막는다.
 *       제51조 제1항(2주 이내)은 취업규칙으로 정하므로 합의서를 요구하지 않는다.</li>
 *   <li>계획: 탄력적은 인사담당이 정하고(제51조 제2항 제3호 "근로일과 그 근로일별 근로시간"), 선택적은 사원 본인이
 *       시업·종업을 정한다(제52조 제1항 "시업 및 종업 시각을 근로자의 결정에 맡기기로 한 근로자").</li>
 *   <li>정산: 집계 엔진({@link WorkHoursService#computeWeek})의 날별 실근로·야간을 단위기간(정산기간)으로 모아 판정한다.
 *       유연근무 사원의 연장은 이 결과가 기준이다(일·주 단위 일반 판정 대신).</li>
 * </ul>
 */
@Service
public class FlexibleWorkService {

    private static final int MINUTES_PER_DAY = 1440;

    private final FlexWorkAssignRepository assigns;
    private final FlexWorkPlanRepository plans;
    private final EmployeeRepository employees;
    private final LaborAgreementGuard guard;
    private final FlexibleWorkLegalRules legalRules;
    private final WorkHoursService workHours;
    private final Clock clock;

    @Autowired
    public FlexibleWorkService(FlexWorkAssignRepository assigns, FlexWorkPlanRepository plans,
            EmployeeRepository employees, LaborAgreementGuard guard, FlexibleWorkLegalRules legalRules,
            WorkHoursService workHours) {
        this(assigns, plans, employees, guard, legalRules, workHours, Clock.system(WorkHoursService.SEOUL));
    }

    FlexibleWorkService(FlexWorkAssignRepository assigns, FlexWorkPlanRepository plans, EmployeeRepository employees,
            LaborAgreementGuard guard, FlexibleWorkLegalRules legalRules, WorkHoursService workHours, Clock clock) {
        this.assigns = assigns;
        this.plans = plans;
        this.employees = employees;
        this.guard = guard;
        this.legalRules = legalRules;
        this.workHours = workHours;
        this.clock = clock.withZone(WorkHoursService.SEOUL);
    }

    // ---------------------------------------------------------------- DTO

    public record AssignRequest(Long empId, WorkSystem workSystem, LocalDate periodStart, LocalDate periodEnd,
            String jobCode, String coreStart, String coreEnd, Integer deemedMinutes, String note) {
    }

    public record AssignView(long flexId, long empId, WorkSystem workSystem, String article, LocalDate periodStart,
            LocalDate periodEnd, String jobCode, String coreStart, String coreEnd, Integer deemedMinutes,
            Long agreementId, String note) {
    }

    public record PlanInput(LocalDate date, LocalDateTime start, LocalDateTime end, Integer minutes) {
    }

    public record PlanView(LocalDate date, LocalDateTime start, LocalDateTime end, int minutes) {
    }

    /** 배정과 계획. totalLimitMinutes: 단위기간(정산기간) 평균 1주 기준시간 총량(근로기준법 제51조·제52조). */
    public record Detail(AssignView assign, int totalLimitMinutes, List<PlanView> plan) {
    }

    public record SettleDay(LocalDate date, String dayKind, int planMinutes, int actualMinutes, int overtimeMinutes) {
    }

    public record Settlement(long flexId, WorkSystem workSystem, LocalDate periodStart, LocalDate periodEnd,
            LocalDate workFrom, LocalDate workTo, Check check, int totalLimitMinutes, int actualMinutes,
            int recognizedMinutes, int overtimeMinutes, int nightMinutes, int holidayMinutes, int coreTimeMissDays,
            List<SettleDay> days) {
    }

    // ---------------------------------------------------------------- 배정

    /** 인사담당 배정. 법정 단위기간 한도·대상 업무·서면합의를 검사한다. */
    @Transactional
    public AssignView assign(AssignRequest req, String actor) {
        if (req == null || req.empId() == null || req.workSystem() == null || req.periodStart() == null
                || req.periodEnd() == null) {
            throw AttendanceException.badRequest("사원·근무제·기간은 필수예요");
        }
        if (req.periodEnd().isBefore(req.periodStart())) {
            throw AttendanceException.badRequest("기간 종료일은 시작일 이후여야 해요");
        }
        Employee e = employees.findForUpdate(req.empId())
                .orElseThrow(() -> AttendanceException.notFound("사원이 없어요: " + req.empId()));
        if (e.getHireDate().isAfter(req.periodEnd())
                || (e.getRetireDate() != null && e.getRetireDate().isBefore(req.periodStart()))) {
            throw AttendanceException.badRequest("기간 중 재직하지 않는 사원이에요");
        }
        WorkSystem sys = req.workSystem();
        String job = blankToNull(req.jobCode());
        LocalTime coreStart = parseTime(req.coreStart());
        LocalTime coreEnd = parseTime(req.coreEnd());
        Integer deemed = req.deemedMinutes();
        if ((coreStart == null) != (coreEnd == null)
                || (coreStart != null && (sys != WorkSystem.SELECTIVE || !coreEnd.isAfter(coreStart)))) {
            throw AttendanceException.badRequest("코어타임은 선택적 근로시간제에서 시작·종료를 함께, 종료가 시작보다 늦게 넣어요");
        }
        if (sys == WorkSystem.DISCRETIONARY) {
            // 근로기준법 시행령 제31조: 재량근로 대상 업무
            if (!DiscretionaryJob.isEligible(job)) {
                throw unprocessable("재량근로 대상 업무가 아니에요(근로기준법 시행령 제31조): " + job);
            }
            // 근로기준법 제58조 제3항: 서면합의로 정한 시간을 근로한 것으로 본다
            if (deemed == null || deemed <= 0 || deemed > MINUTES_PER_DAY) {
                throw AttendanceException.badRequest("재량근로는 서면합의 간주근로시간(분, 1~1440)이 필요해요");
            }
        } else {
            if (deemed != null) {
                throw AttendanceException.badRequest("간주근로시간은 재량근로에서만 넣어요");
            }
            Limits l = limitsFor(sys, job, req.periodStart());
            Check check = FlexibleWorkCalculator.checkUnitPeriod(req.periodStart(), req.periodEnd(), l);
            if (check != Check.OK) {
                throw unprocessable(planMessage(check, sys, l));
            }
        }
        for (FlexWorkAssign a : assigns.findByEmpIdOrderByPeriodStartAsc(e.getEmpId())) {
            if (a.overlaps(req.periodStart(), req.periodEnd())) {
                throw AttendanceException.conflict("기간이 겹치는 유연근무 배정이 있어요: " + a.getPeriodStart() + " ~ "
                        + a.getPeriodEnd());
            }
        }
        Long agreementId = null;
        AgreementType type = sys.requiredAgreement();
        if (type != null) {
            // 사원과 같은 법인 서면합의만 인정한다(#125).
            agreementId = guard.requireValid(e.getEnterCd(), type, req.periodStart(), req.periodEnd(), e.getSiteCode())
                    .getAgreementId();
        }
        FlexWorkAssign saved = assigns.save(new FlexWorkAssign(e.getEmpId(), sys, req.periodStart(),
                req.periodEnd(), job, minuteOfDay(coreStart), minuteOfDay(coreEnd), deemed, agreementId,
                blankToNull(req.note()), actor, now()));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<AssignView> list(Long empId) {
        List<FlexWorkAssign> all = empId == null ? assigns.findAllByOrderByPeriodStartDesc()
                : assigns.findByEmpIdOrderByPeriodStartAsc(empId);
        return all.stream().map(FlexibleWorkService::view).toList();
    }

    /** 로그인 사원 본인의 배정. */
    @Transactional(readOnly = true)
    public List<AssignView> mine(AuthUser user) {
        return list(selfEmpId(user));
    }

    @Transactional(readOnly = true)
    public Detail detail(long flexId, AuthUser user) {
        FlexWorkAssign a = readable(flexId, user);
        int limit = a.getWorkSystem() == WorkSystem.DISCRETIONARY ? 0
                : FlexibleWorkCalculator.totalLimit(a.getPeriodStart(), a.getPeriodEnd(),
                        limitsFor(a.getWorkSystem(), a.getJobCode(), a.getPeriodStart()).weeklyStandard());
        List<PlanView> plan = plans.findByFlexIdOrderByWorkDateAsc(flexId).stream()
                .map(p -> new PlanView(p.getWorkDate(), p.getPlanStart(), p.getPlanEnd(), p.getPlanMinutes()))
                .toList();
        return new Detail(view(a), limit, plan);
    }

    // ---------------------------------------------------------------- 계획

    /**
     * 계획을 통째로 바꾼다. 탄력적은 인사담당만, 선택적은 본인(또는 인사담당). 재량근로는 계획이 없다.
     * 법정 한도를 어기는 계획은 422로 거부하고 아무것도 바꾸지 않는다.
     */
    @Transactional
    public Detail savePlan(long flexId, List<PlanInput> input, AuthUser user) {
        FlexWorkAssign a = assigns.findForUpdate(flexId)
                .orElseThrow(() -> AttendanceException.notFound("유연근무 배정이 없어요: " + flexId));
        WorkSystem sys = a.getWorkSystem();
        boolean hr = user.has(Permission.EMP_WRITE);
        if (sys == WorkSystem.DISCRETIONARY) {
            throw AttendanceException.badRequest("재량근로는 근무계획을 넣지 않아요(근로기준법 제58조 제3항)");
        }
        if (sys.isFlexible() && !hr) {
            throw forbidden("탄력적 근로시간제의 근무계획은 인사담당이 정해요");
        }
        if (sys == WorkSystem.SELECTIVE && !hr && !user.isSelf(a.getEmpId())) {
            throw forbidden("본인의 근무계획만 입력할 수 있어요");
        }
        requireLinkedAgreement(a, employee(a.getEmpId()));
        List<PlanDay> days = toPlanDays(a, input == null ? List.of() : input);
        Limits l = limitsFor(sys, a.getJobCode(), a.getPeriodStart());
        if (sys.isFlexible()) {
            Check c = FlexibleWorkCalculator.checkFlexiblePlan(a.getPeriodStart(), a.getPeriodEnd(), days, l);
            if (c != Check.OK) {
                throw unprocessable(planMessage(c, sys, l));
            }
        } else {
            List<SelectiveDay> sd = days.stream()
                    .map(d -> new SelectiveDay(d.date(), d.start(), d.end(), d.minutes())).toList();
            SelectiveResult r = FlexibleWorkCalculator.settleSelective(a.getPeriodStart(), a.getPeriodEnd(), sd,
                    toTime(a.getCoreStartMin()), toTime(a.getCoreEndMin()), l, l.restMinutes());
            if (r.check() != Check.OK) {
                throw unprocessable(planMessage(r.check(), sys, l));
            }
            if (r.coreTimeMissDays() > 0) {
                throw unprocessable("코어타임(" + hhmm(a.getCoreStartMin()) + "~" + hhmm(a.getCoreEndMin())
                        + ")을 덮지 않는 근로일이 " + r.coreTimeMissDays() + "일 있어요(서면합의 근로기준법 제52조 제1항 제4호)");
            }
        }
        plans.deleteAllOfAssign(flexId);
        LocalDateTime now = now();
        plans.saveAll(days.stream()
                .map(d -> new FlexWorkPlan(flexId, d.date(), d.start(), d.end(), d.minutes(), user.loginId(), now))
                .toList());
        return detail(flexId, user);
    }

    private List<PlanDay> toPlanDays(FlexWorkAssign a, List<PlanInput> input) {
        Set<LocalDate> seen = new HashSet<>();
        List<PlanDay> out = new ArrayList<>();
        for (PlanInput p : input) {
            if (p == null || p.date() == null) {
                throw AttendanceException.badRequest("계획 날짜는 필수예요");
            }
            LocalDate d = p.date();
            if (d.isBefore(a.getPeriodStart()) || d.isAfter(a.getPeriodEnd())) {
                throw AttendanceException.badRequest("기간 밖의 날짜예요: " + d);
            }
            if (!seen.add(d)) {
                throw AttendanceException.badRequest("같은 날짜가 두 번 있어요: " + d);
            }
            if ((p.start() == null) != (p.end() == null)) {
                throw AttendanceException.badRequest("시업·종업 시각은 함께 넣어요: " + d);
            }
            int minutes = p.minutes() == null ? 0 : p.minutes();
            if (p.start() == null) {
                if (minutes != 0) {
                    throw AttendanceException.badRequest("근로하는 날은 시업·종업 시각이 필요해요: " + d);
                }
                out.add(new PlanDay(d, null, null, 0));
                continue;
            }
            if (!p.start().toLocalDate().equals(d) || !p.end().isAfter(p.start())) {
                throw AttendanceException.badRequest("시업은 그날, 종업은 시업보다 늦어야 해요: " + d);
            }
            long span = Duration.between(p.start(), p.end()).toMinutes();
            if (minutes <= 0 || minutes > span || minutes > MINUTES_PER_DAY) {
                throw AttendanceException.badRequest("근로분은 1분 이상이며 시업~종업 시간과 하루 분 수를 넘을 수 없어요(휴게 제외): " + d);
            }
            out.add(new PlanDay(d, p.start().truncatedTo(ChronoUnit.MINUTES), p.end().truncatedTo(ChronoUnit.MINUTES),
                    minutes));
        }
        return out;
    }

    // ---------------------------------------------------------------- 정산

    /** 단위기간(정산기간) 정산. 집계 엔진의 날별 실근로·야간을 그대로 쓴다. 기준값이 없으면 LegalRateException(409). */
    @Transactional(readOnly = true)
    public Settlement settle(long flexId, AuthUser user) {
        FlexWorkAssign a = readable(flexId, user);
        Employee e = employee(a.getEmpId());
        requireLinkedAgreement(a, e);
        LocalDate workFrom = e.getHireDate().isAfter(a.getPeriodStart()) ? e.getHireDate() : a.getPeriodStart();
        LocalDate workTo = e.getRetireDate() != null && e.getRetireDate().isBefore(a.getPeriodEnd())
                ? e.getRetireDate() : a.getPeriodEnd();
        Map<LocalDate, DailyWorkSummaryRow> actual = actualDays(e.getEnterCd(), a.getEmpId(), workFrom, workTo);
        Map<LocalDate, Integer> planMinutes = new HashMap<>();
        for (FlexWorkPlan p : plans.findByFlexIdOrderByWorkDateAsc(flexId)) {
            planMinutes.put(p.getWorkDate(), p.getPlanMinutes());
        }
        int actualTotal = actual.values().stream().mapToInt(r -> nz(r.workedMinutes())).sum();
        int night = actual.values().stream().mapToInt(r -> nz(r.nightMinutes())).sum();
        WorkSystem sys = a.getWorkSystem();
        List<SettleDay> days = new ArrayList<>();

        if (sys == WorkSystem.DISCRETIONARY) {
            // 1일 기준시간(근로기준법 제50조 제2항)은 배정 시작일 기준 legal_rate
            WorkHoursCalculator.Rules base = workHours.weekInputs(e.getEnterCd(), a.getPeriodStart()).appliedByDate()
                    .get(a.getPeriodStart()).rules();
            int recognized = 0;
            int overtime = 0;
            int holiday = 0;
            Check check = Check.OK;
            for (DailyWorkSummaryRow r : actual.values()) {
                if (nz(r.workedMinutes()) <= 0) {
                    days.add(new SettleDay(r.workDate(), r.dayKind(), 0, 0, 0));
                    continue;
                }
                DiscretionaryResult d = FlexibleWorkCalculator.settleDiscretionary(a.getJobCode(),
                        a.getDeemedMinutes(), nz(r.nightMinutes()),
                        WorkHoursCalculator.DayKind.HOLIDAY.name().equals(r.dayKind()), base.dailyStandardMinutes());
                check = d.check();
                recognized += d.recognizedMinutes();
                overtime += d.overtimeMinutes();
                holiday += d.holidayMinutes();
                days.add(new SettleDay(r.workDate(), r.dayKind(), a.getDeemedMinutes(), nz(r.workedMinutes()),
                        d.overtimeMinutes()));
            }
            return new Settlement(flexId, sys, a.getPeriodStart(), a.getPeriodEnd(), workFrom, workTo, check, 0,
                    actualTotal, recognized, overtime, night, holiday, 0, days);
        }

        Limits l = limitsFor(sys, a.getJobCode(), a.getPeriodStart());
        if (sys == WorkSystem.SELECTIVE) {
            List<SelectiveDay> sd = actual.values().stream()
                    .map(r -> nz(r.workedMinutes()) > 0 && r.workStart() != null && r.workEnd() != null
                            && r.workEnd().isAfter(r.workStart())
                            ? new SelectiveDay(r.workDate(), r.workStart(), r.workEnd(), nz(r.workedMinutes()))
                            : new SelectiveDay(r.workDate(), null, null, nz(r.workedMinutes())))
                    .toList();
            SelectiveResult r = FlexibleWorkCalculator.settleSelective(a.getPeriodStart(), a.getPeriodEnd(), sd,
                    toTime(a.getCoreStartMin()), toTime(a.getCoreEndMin()), l, l.restMinutes());
            for (DailyWorkSummaryRow d : actual.values()) {
                days.add(new SettleDay(d.workDate(), d.dayKind(), planMinutes.getOrDefault(d.workDate(), 0),
                        nz(d.workedMinutes()), 0));
            }
            return new Settlement(flexId, sys, a.getPeriodStart(), a.getPeriodEnd(), workFrom, workTo, r.check(),
                    r.totalLimitMinutes(), actualTotal, actualTotal, r.overtimeMinutes(), night,
                    holidaySum(actual), r.coreTimeMissDays(), days);
        }

        List<ActualDay> ad = actual.values().stream()
                .map(r -> new ActualDay(r.workDate(), planMinutes.getOrDefault(r.workDate(), 0),
                        nz(r.workedMinutes())))
                .toList();
        FlexSettlement s = FlexibleWorkCalculator.settleFlexible(a.getPeriodStart(), a.getPeriodEnd(), workFrom,
                workTo, ad, l);
        Map<LocalDate, Integer> dayOt = new HashMap<>();
        s.days().forEach(d -> dayOt.put(d.date(), d.overtimeMinutes()));
        for (DailyWorkSummaryRow r : actual.values()) {
            days.add(new SettleDay(r.workDate(), r.dayKind(), planMinutes.getOrDefault(r.workDate(), 0),
                    nz(r.workedMinutes()), dayOt.getOrDefault(r.workDate(), 0)));
        }
        return new Settlement(flexId, sys, a.getPeriodStart(), a.getPeriodEnd(), workFrom, workTo, Check.OK,
                FlexibleWorkCalculator.totalLimit(workFrom, workTo, l.weeklyStandard()), actualTotal, actualTotal,
                s.periodOvertime(), night, holidaySum(actual), 0, days);
    }

    /** 집계 엔진으로 [from, to]의 날별 결과를 주 단위로 계산(저장하지 않음). 날짜 순. */
    private Map<LocalDate, DailyWorkSummaryRow> actualDays(String enterCd, long empId, LocalDate from,
            LocalDate to) {
        Map<LocalDate, DailyWorkSummaryRow> out = new java.util.TreeMap<>();
        if (to.isBefore(from)) {
            return out;
        }
        for (LocalDate d = from; !d.isAfter(to); d = d.plusWeeks(1)) {
            for (DailyWorkSummaryRow r : workHours.computeWeek(empId, workHours.weekInputs(enterCd, d))) {
                if (!r.workDate().isBefore(from) && !r.workDate().isAfter(to)) {
                    out.put(r.workDate(), r);
                }
            }
        }
        // 마지막 주가 위 반복에서 빠지는 경우(시작 요일보다 앞선 요일로 끝날 때)
        if (!out.containsKey(to)) {
            for (DailyWorkSummaryRow r : workHours.computeWeek(empId, workHours.weekInputs(enterCd, to))) {
                if (!r.workDate().isBefore(from) && !r.workDate().isAfter(to)) {
                    out.put(r.workDate(), r);
                }
            }
        }
        return out;
    }

    private static int holidaySum(Map<LocalDate, DailyWorkSummaryRow> actual) {
        // 근로기준법 제56조 제2항: 휴일근로 집계에는 가산 기준시간 이내와 초과분을 모두 포함한다.
        return actual.values().stream().mapToInt(r -> nz(r.holidayMinutes()) + nz(r.holidayExcessMinutes())).sum();
    }

    // ---------------------------------------------------------------- 공통

    private Limits limitsFor(WorkSystem sys, String job, LocalDate start) {
        if (sys.isFlexible()) {
            return legalRules.flexibleAsOf(sys, start);
        }
        SelectiveJob sj = selectiveJob(job);
        return legalRules.selectiveAsOf(sj, start);
    }

    private static SelectiveJob selectiveJob(String job) {
        try {
            return SelectiveJob.valueOf(job);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw AttendanceException.badRequest("선택적 근로시간제 업무 구분은 GENERAL 또는 RND예요: " + job);
        }
    }

    private Employee employee(long empId) {
        return employees.findById(empId)
                .orElseThrow(() -> AttendanceException.notFound("사원이 없어요: " + empId));
    }

    private void requireLinkedAgreement(FlexWorkAssign assign, Employee employee) {
        AgreementType type = assign.getWorkSystem().requiredAgreement();
        if (type != null) {
            guard.requireLinked(employee.getEnterCd(), assign.getAgreementId(), type, assign.getPeriodStart(), assign.getPeriodEnd(),
                    employee.getSiteCode());
        }
    }

    private FlexWorkAssign readable(long flexId, AuthUser user) {
        FlexWorkAssign a = assigns.findById(flexId)
                .orElseThrow(() -> AttendanceException.notFound("유연근무 배정이 없어요: " + flexId));
        if (!user.has(Permission.EMP_READ_ALL) && !user.isSelf(a.getEmpId())) {
            throw forbidden("본인 배정만 볼 수 있어요");
        }
        return a;
    }

    private static long selfEmpId(AuthUser user) {
        if (user.empId() == null) {
            throw forbidden("사원과 연결된 계정만 볼 수 있어요");
        }
        return user.empId();
    }

    private static String periodMessage(WorkSystem sys, Limits l) {
        String limit = l.unitWeeks() > 0 ? l.unitWeeks() + "주" : l.unitMonths() + "개월";
        return switch (sys) {
            case FLEXIBLE_SHORT -> "단위기간이 " + limit + "을 넘어요(근로기준법 제51조 제1항)";
            case FLEXIBLE_WRITTEN -> "단위기간이 " + limit + "을 넘어요(근로기준법 제51조 제2항)";
            case FLEXIBLE_LONG -> "단위기간이 " + limit + "을 넘어요(근로기준법 제51조의2 제1항)";
            default -> "정산기간이 " + limit + "을 넘어요(근로기준법 제52조 제1항)";
        };
    }

    private static String planMessage(Check c, WorkSystem sys, Limits l) {
        String article = switch (sys) {
            case FLEXIBLE_SHORT -> "근로기준법 제51조 제1항";
            case FLEXIBLE_WRITTEN -> "근로기준법 제51조 제2항";
            case FLEXIBLE_LONG -> "근로기준법 제51조의2";
            default -> "근로기준법 제52조";
        };
        return switch (c) {
            case UNIT_PERIOD_EXCEEDED, SETTLEMENT_EXCEEDED -> periodMessage(sys, l);
            case UNIT_PERIOD_TOO_SHORT -> "장기 탄력적 근로시간제 단위기간은 " + l.unitMonthsMinimumExclusive()
                    + "개월을 초과해야 해요(근로기준법 제51조의2 제1항)";
            case DAY_CAP_EXCEEDED -> "특정한 날 계획이 " + l.dayCap() + "분을 넘어요(" + article + ")";
            case WEEK_CAP_EXCEEDED -> "특정한 주 계획이 " + l.weekCap() + "분을 넘어요(" + article + ")";
            case AVG_EXCEEDED -> "단위기간 평균이 1주 " + l.weeklyStandard() + "분을 넘어요(" + article + ")";
            case REST_SHORT -> "근로일 종료 후 다음 근로일 시작까지 연속 " + l.restMinutes() + "분 휴식이 안 돼요("
                    + (sys == WorkSystem.SELECTIVE ? "근로기준법 제52조 제2항 제1호" : "근로기준법 제51조의2 제2항 제1호") + ")";
            default -> c.name();
        };
    }

    static AssignView view(FlexWorkAssign a) {
        AgreementType t = a.getWorkSystem().requiredAgreement();
        String article = t == null ? "근로기준법 제51조 제1항" : t.article();
        return new AssignView(a.getFlexId(), a.getEmpId(), a.getWorkSystem(), article, a.getPeriodStart(),
                a.getPeriodEnd(), a.getJobCode(), hhmm(a.getCoreStartMin()), hhmm(a.getCoreEndMin()),
                a.getDeemedMinutes(), a.getAgreementId(), a.getNote());
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(s.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw AttendanceException.badRequest("시각은 HH:MM 형식이에요: " + s);
        }
    }

    private static Integer minuteOfDay(LocalTime t) {
        return t == null ? null : t.getHour() * 60 + t.getMinute();
    }

    private static LocalTime toTime(Integer m) {
        return m == null ? null : LocalTime.of(m / 60, m % 60);
    }

    private static String hhmm(Integer m) {
        return m == null ? null : String.format("%02d:%02d", m / 60, m % 60);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static AttendanceException unprocessable(String message) {
        return new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, message, List.of());
    }

    private static AttendanceException forbidden(String message) {
        return new AttendanceException(HttpStatus.FORBIDDEN, message, List.of());
    }
}
