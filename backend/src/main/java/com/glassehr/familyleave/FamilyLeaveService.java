package com.glassehr.familyleave;

import com.glassehr.appointment.AppointmentDtos.AppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.appointment.AppointmentDtos.LeavePeriodView;
import com.glassehr.appointment.AppointmentService;
import com.glassehr.appointment.AppointmentType;
import com.glassehr.attendance.WorkPattern;
import com.glassehr.attendance.WorkPatternDtos.AssignRequest;
import com.glassehr.attendance.WorkPatternDtos.AssignmentView;
import com.glassehr.attendance.WorkPatternRepository;
import com.glassehr.attendance.WorkPatternService;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.familyleave.FamilyLeaveDtos.BalanceView;
import com.glassehr.familyleave.FamilyLeaveDtos.ChildBalance;
import com.glassehr.familyleave.FamilyLeaveDtos.CreateRequest;
import com.glassehr.familyleave.FamilyLeaveDtos.MonthUsage;
import com.glassehr.familyleave.FamilyLeaveDtos.RequestView;
import com.glassehr.familyleave.FamilyLeaveDtos.YearUsage;
import com.glassehr.familyleave.FamilyLeavePeriods.MonthsDays;
import com.glassehr.familyleave.FamilyLeaveRequest.Kind;
import com.glassehr.familyleave.FamilyLeaveRequest.Status;
import com.glassehr.legalrate.LegalRateException;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import com.glassehr.org.OrgWriteLock;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 육아휴직·육아기 근로시간 단축·가족돌봄휴직 신청 → 승인 → 인사발령·근무유형 연결(#37). 설계: docs/family-leave-design.md.
 * 근거: 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률(이하 "법") 제19조·제19조의2·제19조의3·제19조의4·제22조의2.
 * 기간·횟수·시간·나이 기준은 모두 legal_rate 에서 시작일 기준으로 읽는다.
 * <ul>
 *   <li>한도(기간·분할 횟수·주 근로시간·1회 최소 기간) 위반은 거부한다. 기준값이 없으면 409.</li>
 *   <li>자격(자녀 나이, 계속근로기간)은 사업주 판단·서류 확인이 남아 있어 거부하지 않고 경고로 남긴다.</li>
 *   <li>승인 시 휴직은 확정 휴직 발령 + 종료일 다음 날 미확정 복직 발령, 단축은 단축 근무유형 배정 + 종료일 다음 날
 *       원래 근무유형 배정을 만든다. 근태 집계는 {@link WorkPatternService#effective}로 날짜별 근무유형을 읽으므로
 *       단축 기간에는 단축 근무유형이 쓰인다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class FamilyLeaveService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FamilyLeaveRequestRepository requests;
    private final EmployeeRepository employees;
    private final WorkPatternRepository patterns;
    private final WorkPatternService workPatterns;
    private final AppointmentService appointments;
    private final LegalRateService rates;
    private final OrgWriteLock orgLock;
    private final Clock clock;

    @Autowired
    public FamilyLeaveService(FamilyLeaveRequestRepository requests, EmployeeRepository employees,
            WorkPatternRepository patterns, WorkPatternService workPatterns, AppointmentService appointments,
            LegalRateService rates, OrgWriteLock orgLock) {
        this(requests, employees, patterns, workPatterns, appointments, rates, orgLock, Clock.system(SEOUL));
    }

    FamilyLeaveService(FamilyLeaveRequestRepository requests, EmployeeRepository employees,
            WorkPatternRepository patterns, WorkPatternService workPatterns, AppointmentService appointments,
            LegalRateService rates, OrgWriteLock orgLock, Clock clock) {
        this.requests = requests;
        this.employees = employees;
        this.patterns = patterns;
        this.workPatterns = workPatterns;
        this.appointments = appointments;
        this.rates = rates;
        this.orgLock = orgLock;
        this.clock = clock.withZone(SEOUL);
    }

    /** 신청 또는 저장된 신청에서 온, 다듬은 내용. */
    private record Draft(Kind kind, String childLabel, LocalDate childBirthDate, String familyRelation,
            LocalDate startDate, LocalDate endDate, Long workPatternId, boolean extension) {

        static Draft of(FamilyLeaveRequest r) {
            return new Draft(r.getKind(), r.getChildLabel(), r.getChildBirthDate(), r.getFamilyRelation(),
                    r.getStartDate(), r.getEndDate(), r.getWorkPatternId(), r.isExtension());
        }
    }

    // ---------------------------------------------------------------- 조회

    public List<RequestView> listByEmployee(long empId) {
        loadEmployee(empId);
        return requests.findByEmpIdOrderByStartDateAscReqIdAsc(empId).stream().map(RequestView::of).toList();
    }

    public List<RequestView> search(Status status) {
        List<FamilyLeaveRequest> list = status == null ? requests.findAllByOrderByRequestedAtAscReqIdAsc()
                : requests.findByStatusOrderByRequestedAtAscReqIdAsc(status);
        return list.stream().map(RequestView::of).toList();
    }

    public RequestView get(long reqId) {
        return RequestView.of(load(reqId));
    }

    // ---------------------------------------------------------------- 신청·승인·반려·취소

    @Transactional
    public RequestView create(long empId, CreateRequest req, String actor) {
        Employee e = employees.findForUpdate(empId)
                .orElseThrow(() -> FamilyLeaveException.notFound("사원을 찾을 수 없어요: " + empId));
        Draft d = normalize(req);
        validate(e, d, null);
        List<String> warnings = warnings(e, d);
        FamilyLeaveRequest saved = requests.saveAndFlush(new FamilyLeaveRequest(empId, d.kind(), d.childLabel(),
                d.childBirthDate(), d.familyRelation(), d.startDate(), d.endDate(), d.workPatternId(), d.extension(),
                req.reason().strip(), warnings.isEmpty() ? null : String.join("\n", warnings), actor, now()));
        return RequestView.of(saved);
    }

    /**
     * 승인. 한도를 다시 검사한 뒤 휴직 발령(확정)·복직 발령(미확정, 종료일 다음 날) 또는 단축·원래 근무유형 배정을 만든다.
     * 발령 확정과 같은 순서로 조직 잠금 → 사원 잠금 → 신청 잠금을 잡는다.
     */
    @Transactional
    public RequestView approve(long reqId, String actor) {
        long empId = requests.findEmpIdByReqId(reqId)
                .orElseThrow(() -> FamilyLeaveException.notFound("신청을 찾을 수 없어요: " + reqId));
        orgLock.acquire();
        Employee e = employees.findForUpdate(empId)
                .orElseThrow(() -> FamilyLeaveException.notFound("사원을 찾을 수 없어요: " + empId));
        FamilyLeaveRequest r = lockRequested(reqId, "승인할");
        Draft d = Draft.of(r);
        validate(e, d, reqId);
        LocalDate next = d.endDate().plusDays(1);
        String label = d.kind().label() + "(신청 " + reqId + ")";
        if (d.kind().isLeaveOfAbsence()) {
            // 법 제19조·제22조의2: 휴직 기간은 휴직(LEAVE) 발령으로 남긴다. 복직은 종료일 다음 날 예정(미확정)으로 만든다.
            AppointmentView leave = appointments.create(empId, new AppointmentRequest(AppointmentType.LEAVE.code(),
                    d.startDate(), null, null, null, label + " 승인", d.startDate(), d.endDate()));
            appointments.confirm(leave.apptId());
            AppointmentView ret = appointments.create(empId, new AppointmentRequest(AppointmentType.RETURN.code(),
                    next, null, null, null, label + " 종료 복직 예정", null, null));
            r.approveLeave(leave.apptId(), ret.apptId(), actor, now());
        } else {
            // 법 제19조의2·제19조의3: 단축 기간만 단축 근무유형을 쓰고, 끝나면 원래 근무유형으로 돌아간다.
            AssignmentView before = currentPattern(e.getEnterCd(), empId, d.startDate());
            Optional<FamilyLeaveRequest> contiguous = approved(empId, reqId).stream()
                    .filter(o -> o.getKind() == Kind.CHILDCARE_REDUCED_HOURS
                            && o.getEndDate().plusDays(1).equals(d.startDate()) && o.getRestoreAssignId() != null)
                    .findFirst();
            AssignmentView reduced = contiguous.isPresent()
                    ? workPatterns.replaceLatest(e.getEnterCd(), empId, contiguous.get().getRestoreAssignId(),
                            new AssignRequest(d.workPatternId(), d.startDate(), label + " 시작"))
                    : workPatterns.assign(e.getEnterCd(), empId,
                            new AssignRequest(d.workPatternId(), d.startDate(), label + " 시작"), actor);
            AssignmentView restore = workPatterns.assign(e.getEnterCd(), empId,
                    new AssignRequest(before.patternId(), next, label + " 종료, 원래 근무유형"), actor);
            r.approveReduced(reduced.assignId(), restore.assignId(), actor, now());
        }
        return RequestView.of(requests.saveAndFlush(r));
    }

    @Transactional
    public RequestView reject(long reqId, String note, String actor) {
        String n = blankToNull(note);
        if (n == null) {
            throw FamilyLeaveException.badRequest("반려 사유를 입력해 주세요");
        }
        FamilyLeaveRequest r = lockRequested(reqId, "반려할");
        r.decide(Status.REJECTED, actor, now(), n);
        return RequestView.of(requests.saveAndFlush(r));
    }

    /** 승인 전 신청 취소. 승인된 신청은 인사발령 취소·근무유형 재배정으로 되돌린다(후속 이슈). */
    @Transactional
    public RequestView cancel(long reqId, String note, String actor) {
        FamilyLeaveRequest r = lockRequested(reqId, "취소할");
        r.decide(Status.CANCELLED, actor, now(), blankToNull(note));
        return RequestView.of(requests.saveAndFlush(r));
    }

    // ---------------------------------------------------------------- 남은 기간

    /** 기준일(기본 오늘)의 자녀별 육아휴직·단축, 그해 가족돌봄휴직 사용·남은 기간. 승인된 신청만 센다. */
    public BalanceView balance(long empId, LocalDate asOf) {
        loadEmployee(empId);
        LocalDate date = asOf == null ? today() : asOf;
        List<FamilyLeaveRequest> approved = approved(empId, null);
        List<String> notes = new ArrayList<>();
        // 승인 대기 신청의 자녀도 넣어, 승인 전에 그 자녀의 남은 기간을 볼 수 있게 한다(사용 기간에는 승인된 것만 센다).
        Set<String> labels = new LinkedHashSet<>();
        requests.findByEmpIdOrderByStartDateAscReqIdAsc(empId).stream()
                .filter(r -> r.getKind() != Kind.FAMILY_CARE_LEAVE
                        && (r.getStatus() == Status.APPROVED || r.getStatus() == Status.REQUESTED))
                .forEach(r -> labels.add(r.getChildLabel()));

        List<ChildBalance> children = new ArrayList<>();
        for (String child : labels) {
            List<MonthsDays> parental = usedPeriods(approved, Kind.PARENTAL_LEAVE, child);
            List<MonthsDays> reduced = usedPeriods(approved, Kind.CHILDCARE_REDUCED_HOURS, child);
            boolean extension = approved.stream().anyMatch(r -> r.getKind() == Kind.PARENTAL_LEAVE
                    && child.equals(r.getChildLabel()) && r.isExtension());
            Optional<Integer> split = opt(LegalRateItem.PARENTAL_LEAVE_SPLIT_COUNT, date, notes);
            MonthUsage p = usage(parentalLimit(extension, date, notes), sum(parental), parental.size(),
                    split.map(s -> s + 1).orElse(null));
            MonthUsage c = usage(reducedLimit(sum(parental), date, notes), sum(reduced), reduced.size(), null);
            if (p.usedDays() > 0 || c.usedDays() > 0) {
                notes.add(child + ": 나누어 쓴 기간의 남는 일수는 개월로 합치지 않고 따로 표시했어요(기준 확인 중)");
            }
            children.add(new ChildBalance(child, p, c));
        }

        int year = date.getYear();
        Optional<Integer> careLimit = opt(LegalRateItem.FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR, date, notes);
        int careUsed = familyCareDays(approved, year);
        YearUsage care = new YearUsage(year, careLimit.orElse(null), careUsed,
                careLimit.map(l -> Math.max(0, l - careUsed)).orElse(null));
        // 법 제22조의2 제4항 제3호: 가족돌봄휴가 기간은 가족돌봄휴직 기간에 포함된다. 휴가 사용 기록은 아직 없다.
        notes.add("가족돌봄휴가 사용일은 휴가 기능이 생기면 가족돌봄휴직 연간 한도에 함께 셉니다");
        return new BalanceView(empId, date, children, care, notes.stream().distinct().toList());
    }

    // ---------------------------------------------------------------- 검사

    private Draft normalize(CreateRequest req) {
        Kind kind = req.kind();
        String child = blankToNull(req.childLabel());
        String family = blankToNull(req.familyRelation());
        boolean extension = Boolean.TRUE.equals(req.extension());
        if (req.endDate().isBefore(req.startDate())) {
            throw FamilyLeaveException.badRequest("종료일은 시작일 이후여야 해요");
        }
        if (kind == Kind.FAMILY_CARE_LEAVE) {
            if (family == null) {
                throw FamilyLeaveException.badRequest("가족돌봄휴직은 돌볼 가족의 관계를 입력해 주세요");
            }
            if (child != null || req.childBirthDate() != null) {
                throw FamilyLeaveException.badRequest("자녀 정보는 육아휴직·육아기 근로시간 단축에만 입력해요");
            }
        } else {
            if (child == null) {
                throw FamilyLeaveException.badRequest("자녀 구분(예: 첫째)을 입력해 주세요. 실명은 넣지 않아요");
            }
            if (family != null) {
                throw FamilyLeaveException.badRequest("가족 관계는 가족돌봄휴직에만 입력해요");
            }
        }
        if (kind == Kind.CHILDCARE_REDUCED_HOURS) {
            if (req.workPatternId() == null) {
                throw FamilyLeaveException.badRequest("단축 기간에 쓸 근무유형을 골라 주세요");
            }
            // 법 제19조의2 제1항: 자녀를 양육하기 위한 단축이라 출생한 자녀만 대상(임신기 단축은 근로기준법 제74조 제7항)
            if (req.childBirthDate() == null || req.childBirthDate().isAfter(req.startDate())) {
                throw FamilyLeaveException.badRequest("육아기 근로시간 단축은 시작일 전에 태어난 자녀의 생년월일을 입력해 주세요");
            }
        } else if (req.workPatternId() != null) {
            throw FamilyLeaveException.badRequest("근무유형은 육아기 근로시간 단축에만 골라요");
        }
        if (extension && kind != Kind.PARENTAL_LEAVE) {
            throw FamilyLeaveException.badRequest("추가 육아휴직 요건 확인은 육아휴직에만 표시해요");
        }
        return new Draft(kind, child, req.childBirthDate(), family, req.startDate(), req.endDate(),
                req.workPatternId(), extension);
    }

    /** 날짜·겹침·근무유형·한도 검사. excludeId 는 승인 중인 신청 자신. */
    private void validate(Employee e, Draft d, Long excludeId) {
        if (d.startDate().isBefore(e.getHireDate())) {
            throw FamilyLeaveException.badRequest("시작일이 입사일(" + e.getHireDate() + ")보다 빨라요");
        }
        if (e.getRetireDate() != null && !d.endDate().isBefore(e.getRetireDate())) {
            throw FamilyLeaveException.badRequest("종료일이 퇴직일(" + e.getRetireDate() + ") 전이어야 해요");
        }
        for (FamilyLeaveRequest o : requests.findByEmpIdOrderByStartDateAscReqIdAsc(e.getEmpId())) {
            boolean active = o.getStatus() == Status.REQUESTED || o.getStatus() == Status.APPROVED;
            if (!active || o.getReqId().equals(excludeId)) {
                continue;
            }
            Optional<LeavePeriodView> period = overlapPeriod(o);
            if (period.isPresent()
                    && !period.get().startDate().isAfter(d.endDate()) && !period.get().endDate().isBefore(d.startDate())) {
                throw FamilyLeaveException.conflict("기간이 겹치는 " + o.getKind().label() + " 신청(" + o.getReqId()
                        + ", " + period.get().startDate() + "~" + period.get().endDate() + ")이 있어요");
            }
        }
        List<FamilyLeaveRequest> approved = approved(e.getEmpId(), excludeId);
        switch (d.kind()) {
            case PARENTAL_LEAVE -> checkParental(d, approved);
            case CHILDCARE_REDUCED_HOURS -> checkReduced(e, d, approved);
            case FAMILY_CARE_LEAVE -> checkFamilyCare(d, approved);
        }
    }

    private void checkParental(Draft d, List<FamilyLeaveRequest> approved) {
        LocalDate on = d.startDate();
        List<MonthsDays> used = usedPeriods(approved, Kind.PARENTAL_LEAVE, d.childLabel());
        boolean extension = d.extension() || approved.stream().anyMatch(r -> r.getKind() == Kind.PARENTAL_LEAVE
                && d.childLabel().equals(r.getChildLabel()) && r.isExtension());
        // 법 제19조의4 제1항: legal_rate 값은 "n회에 한정하여 나누어 사용"의 n이라 전체 사용은 n+1번까지다.
        int split = req(LegalRateItem.PARENTAL_LEAVE_SPLIT_COUNT, on);
        if (used.size() + 1 > split + 1) {
            throw FamilyLeaveException.conflict("육아휴직은 같은 자녀에 대해 " + split + "회까지 나누어 쓸 수 있어요(법 제19조의4 제1항). "
                    + "이미 " + used.size() + "번 썼어요");
        }
        // 법 제19조 제2항: 육아휴직 기간(요건 충족 시 추가 기간 포함)
        int limit = parentalLimit(extension, on, null).orElseThrow(() -> missing("육아휴직 기간"));
        MonthsDays total = sum(used).plus(FamilyLeavePeriods.length(d.startDate(), d.endDate()));
        if (total.exceeds(limit)) {
            throw FamilyLeaveException.conflict("같은 자녀의 육아휴직이 " + limit + "개월을 넘어요(법 제19조 제2항). 합계 "
                    + total.months() + "개월 " + total.days() + "일");
        }
        // 법 제19조의2 제4항: 육아휴직 사용으로 미사용 기간 가산분이 줄어도 이미 쓴 단축 기간이 새 한도를 넘을 수 없다.
        MonthsDays reduced = sum(usedPeriods(approved, Kind.CHILDCARE_REDUCED_HOURS, d.childLabel()));
        int reducedLimit = reducedLimit(total, on, null).orElseThrow(() -> missing("육아기 근로시간 단축 기간"));
        if (reduced.exceeds(reducedLimit)) {
            throw FamilyLeaveException.conflict("육아휴직 사용 후 같은 자녀의 육아기 근로시간 단축 한도 " + reducedLimit
                    + "개월보다 이미 쓴 단축 기간이 길어요(법 제19조의2 제4항)");
        }
    }

    private void checkReduced(Employee e, Draft d, List<FamilyLeaveRequest> approved) {
        LocalDate on = d.startDate();
        // 사원과 같은 법인 근무유형만 고를 수 있다(#125).
        WorkPattern p = patterns.findByEnterCdAndPatternId(e.getEnterCd(), d.workPatternId())
                .orElseThrow(() -> FamilyLeaveException.notFound("근무유형이 없어요: " + d.workPatternId()));
        if (!p.isActive()) {
            throw FamilyLeaveException.badRequest("사용 중지된 근무유형은 고를 수 없어요: " + p.getPatternCode());
        }
        // 법 제19조의2 제3항: 단축 후 근로시간은 주당 하한 이상, 상한 이하(시간 → 분)
        int min = req(LegalRateItem.CHILDCARE_REDUCED_WEEKLY_HOURS_MIN, on) * 60;
        int max = req(LegalRateItem.CHILDCARE_REDUCED_WEEKLY_HOURS_MAX, on) * 60;
        int weekly = p.getWeeklyContractMinutes();
        if (weekly < min || weekly > max) {
            throw FamilyLeaveException.badRequest("단축 후 주 소정근로시간은 " + min / 60 + "시간 이상 " + max / 60
                    + "시간 이하여야 해요(법 제19조의2 제3항). 고른 근무유형은 주 " + minutes(weekly));
        }
        AssignmentView before = currentPattern(e.getEnterCd(), e.getEmpId(), on);
        WorkPattern current = patterns.findByEnterCdAndPatternId(e.getEnterCd(), before.patternId())
                .orElseThrow(() -> FamilyLeaveException.notFound("근무유형이 없어요: " + before.patternId()));
        if (weekly >= current.getWeeklyContractMinutes()) {
            throw FamilyLeaveException.badRequest("단축 근무유형의 주 소정근로시간(" + minutes(weekly) + ")이 지금 근무유형("
                    + minutes(current.getWeeklyContractMinutes()) + ")보다 짧아야 해요");
        }
        // 법 제19조의4 제2항: 나누어 쓸 때 1회 최소 기간. 개정 최소 기간(법률 제20521호, 2025-02-23)은 시행 당시 사용 중인
        // 단축에도 적용되므로(같은 법률 부칙 제4조, docs/legal-rate-seed-sources.md 336행) 기간 마지막 날의 기준값을 쓴다.
        // 정답: tests/legal-fixtures/family-leave-balance.csv 월말1개월_1월31일시작(2025-01-31~02-28 허용)
        int unit = req(LegalRateItem.CHILDCARE_REDUCED_MIN_UNIT_MONTHS, d.endDate());
        MonthsDays len = FamilyLeavePeriods.length(d.startDate(), d.endDate());
        if (len.months() < unit) {
            throw FamilyLeaveException.badRequest("육아기 근로시간 단축은 한 번에 " + unit + "개월 이상 써야 해요(법 제19조의4 제2항)");
        }
        // 법 제19조의2 제4항: 기본 기간 + 미사용 육아휴직 기간 × 배수
        MonthsDays parental = sum(usedPeriods(approved, Kind.PARENTAL_LEAVE, d.childLabel()));
        int limit = reducedLimit(parental, on, null).orElseThrow(() -> missing("육아기 근로시간 단축 기간"));
        MonthsDays total = sum(usedPeriods(approved, Kind.CHILDCARE_REDUCED_HOURS, d.childLabel())).plus(len);
        if (total.exceeds(limit)) {
            throw FamilyLeaveException.conflict("같은 자녀의 육아기 근로시간 단축이 " + limit + "개월을 넘어요(법 제19조의2 제4항). 합계 "
                    + total.months() + "개월 " + total.days() + "일");
        }
    }

    private void checkFamilyCare(Draft d, List<FamilyLeaveRequest> approved) {
        // 법 제22조의2 제4항 제1호: 휴직 1회 최소 일수
        int unit = req(LegalRateItem.FAMILY_CARE_LEAVE_MIN_UNIT_DAYS, d.startDate());
        int days = (int) ChronoUnit.DAYS.between(d.startDate(), d.endDate()) + 1;
        if (days < unit) {
            throw FamilyLeaveException.badRequest("가족돌봄휴직은 한 번에 " + unit + "일 이상 써야 해요(법 제22조의2 제4항 제1호)");
        }
        // 법 제22조의2 제4항 제1호: 연간 최장 일수. 연간은 1월 1일~12월 31일로 센다(docs/family-leave-design.md 7장).
        for (int year = d.startDate().getYear(); year <= d.endDate().getYear(); year++) {
            LocalDate on = year == d.startDate().getYear() ? d.startDate() : LocalDate.of(year, 1, 1);
            int limit = req(LegalRateItem.FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR, on);
            int total = familyCareDays(approved, year) + FamilyLeavePeriods.daysInYear(d.startDate(), d.endDate(), year);
            if (total > limit) {
                throw FamilyLeaveException.conflict(year + "년 가족돌봄휴직이 " + limit + "일을 넘어요(법 제22조의2 제4항 제1호). 합계 "
                        + total + "일");
            }
        }
    }

    /** 자격 확인 경고. 거부 사유가 아니라 인사 담당자가 확인할 항목이다. */
    private List<String> warnings(Employee e, Draft d) {
        List<String> w = new ArrayList<>();
        LocalDate on = d.startDate();
        if (d.kind() != Kind.FAMILY_CARE_LEAVE) {
            LocalDate birth = d.childBirthDate();
            if (birth == null || birth.isAfter(on)) {
                // 법 제19조 제1항: 임신 중인 여성 근로자도 육아휴직을 신청할 수 있다
                w.add("자녀가 시작일에 아직 태어나지 않았어요. 임신 중 육아휴직(법 제19조 제1항)인지 확인해 주세요");
            } else {
                LegalRateItem item = d.kind() == Kind.PARENTAL_LEAVE ? LegalRateItem.PARENTAL_LEAVE_CHILD_MAX_AGE
                        : LegalRateItem.CHILDCARE_REDUCED_CHILD_MAX_AGE;
                String article = d.kind() == Kind.PARENTAL_LEAVE ? "법 제19조 제1항" : "법 제19조의2 제1항";
                Optional<Integer> max = opt(item, on, w);
                int age = Period.between(birth, on).getYears();
                if (max.isPresent() && age > max.get()) {
                    w.add("시작일에 자녀가 만 " + age + "세예요. 만 " + max.get() + "세를 넘으면 초등학교 학년 기준을 충족하는지 서류로 확인해 주세요("
                            + article + ")");
                }
            }
        }
        LegalRateItem serviceItem = switch (d.kind()) {
            case PARENTAL_LEAVE -> LegalRateItem.PARENTAL_LEAVE_MIN_SERVICE_MONTHS;
            case CHILDCARE_REDUCED_HOURS -> LegalRateItem.CHILDCARE_REDUCED_MIN_SERVICE_MONTHS;
            case FAMILY_CARE_LEAVE -> LegalRateItem.FAMILY_CARE_LEAVE_MIN_SERVICE_MONTHS;
        };
        Optional<Integer> minService = opt(serviceItem, on, w);
        // 시행령 제10조·제15조의2·제16조의3: 시작하려는 날의 전날까지 계속근로기간
        int months = FamilyLeavePeriods.length(e.getHireDate(), on.minusDays(1)).months();
        if (minService.isPresent() && months < minService.get()) {
            w.add("시작일 전날까지 계속근로가 " + months + "개월이라 사업주가 허용하지 않을 수 있어요(" + minService.get()
                    + "개월 미만, " + d.kind().label() + " 거부 가능 사유)");
        }
        return w;
    }

    // ---------------------------------------------------------------- 사용 기간·한도

    private List<FamilyLeaveRequest> approved(long empId, Long excludeId) {
        return requests.findByEmpIdOrderByStartDateAscReqIdAsc(empId).stream()
                .filter(r -> r.getStatus() == Status.APPROVED && !r.getReqId().equals(excludeId)).toList();
    }

    /**
     * 승인된 같은 종류·자녀 신청의 실제 사용 기간. 휴직은 휴직 발령 이력(복직 발령으로 앞당겨진 종료일 반영, 취소된 발령은 제외),
     * 단축은 신청 기간이다.
     */
    private List<MonthsDays> usedPeriods(List<FamilyLeaveRequest> approved, Kind kind, String child) {
        List<MonthsDays> out = new ArrayList<>();
        for (FamilyLeaveRequest r : approved) {
            if (r.getKind() != kind || !Objects.equals(child, r.getChildLabel())) {
                continue;
            }
            if (kind.isLeaveOfAbsence()) {
                actualLeave(r).ifPresent(p -> out.add(FamilyLeavePeriods.length(p.startDate(), p.endDate())));
            } else {
                out.add(FamilyLeavePeriods.length(r.getStartDate(), r.getEndDate()));
            }
        }
        return out;
    }

    private int familyCareDays(List<FamilyLeaveRequest> approved, int year) {
        return approved.stream().filter(r -> r.getKind() == Kind.FAMILY_CARE_LEAVE)
                .map(this::actualLeave).flatMap(Optional::stream)
                .mapToInt(p -> FamilyLeavePeriods.daysInYear(p.startDate(), p.endDate(), year)).sum();
    }

    private Optional<LeavePeriodView> actualLeave(FamilyLeaveRequest r) {
        return appointments.leavePeriods(r.getEmpId(), r.getStartDate(), r.getEndDate()).stream()
                .filter(p -> r.getLeaveApptId() != null && p.leaveApptId() == r.getLeaveApptId())
                .findFirst();
    }

    private Optional<LeavePeriodView> overlapPeriod(FamilyLeaveRequest r) {
        if (r.getStatus() == Status.APPROVED && r.getKind().isLeaveOfAbsence()) {
            return actualLeave(r);
        }
        return Optional.of(new LeavePeriodView(r.getEmpId(), 0, r.getStartDate(), r.getEndDate(), r.getEndDate(),
                null, null));
    }

    /** 법 제19조 제2항: 육아휴직 기간 + (추가 요건 확인 시) 추가 기간. */
    private Optional<Integer> parentalLimit(boolean extension, LocalDate on, List<String> notes) {
        Optional<Integer> base = opt(LegalRateItem.PARENTAL_LEAVE_MAX_MONTHS, on, notes);
        if (!extension) {
            return base;
        }
        Optional<Integer> extra = opt(LegalRateItem.PARENTAL_LEAVE_EXTENSION_MONTHS, on, notes);
        return base.flatMap(b -> extra.map(x -> b + x));
    }

    /**
     * 법 제19조의2 제4항: 단축 기간 = 기본 기간 + 미사용 육아휴직 기간 × 배수. 미사용 기간은 추가 육아휴직을 뺀 기본 기간에서
     * 쓴 기간을 뺀 개월 수이며, 남는 일수가 있으면 그 달은 쓴 것으로 본다(넘치게 허용하지 않도록).
     */
    private Optional<Integer> reducedLimit(MonthsDays parentalUsed, LocalDate on, List<String> notes) {
        Optional<Integer> base = opt(LegalRateItem.CHILDCARE_REDUCED_HOURS_MAX_MONTHS, on, notes);
        Optional<Integer> multiplier = opt(LegalRateItem.CHILDCARE_REDUCED_UNUSED_LEAVE_MULTIPLIER, on, notes);
        Optional<Integer> parentalMax = opt(LegalRateItem.PARENTAL_LEAVE_MAX_MONTHS, on, notes);
        if (base.isEmpty() || multiplier.isEmpty() || parentalMax.isEmpty()) {
            return Optional.empty();
        }
        int unused = Math.max(0, parentalMax.get() - parentalUsed.months() - (parentalUsed.days() > 0 ? 1 : 0));
        return Optional.of(base.get() + multiplier.get() * unused);
    }

    private static MonthUsage usage(Optional<Integer> limit, MonthsDays used, int uses, Integer usesMax) {
        return new MonthUsage(limit.orElse(null), used.months(), used.days(),
                limit.map(l -> Math.max(0, l - used.months())).orElse(null), used.days(), uses, usesMax);
    }

    private static MonthsDays sum(List<MonthsDays> list) {
        return list.stream().reduce(MonthsDays.ZERO, MonthsDays::plus);
    }

    private AssignmentView currentPattern(String enterCd, long empId, LocalDate on) {
        return workPatterns.effective(enterCd, empId, on).orElseThrow(() -> FamilyLeaveException.conflict(
                "시작일(" + on + ")에 배정된 근무유형이 없어 단축이 끝난 뒤 돌아갈 근무유형을 알 수 없어요. 먼저 근무유형을 배정해 주세요"));
    }

    // ---------------------------------------------------------------- legal_rate

    /** 정수 기준값. 없으면 빈 값이고, notes 가 있으면 안내를 남긴다. 겹침·단위 오류·정수가 아닌 값은 409. */
    private Optional<Integer> opt(LegalRateItem item, LocalDate on, List<String> notes) {
        Optional<LegalRateRow> row;
        try {
            row = rates.findEffective(item.code(), on);
        } catch (LegalRateException ex) {
            throw FamilyLeaveException.conflict("법령 기준값(" + item.code() + ")을 확인해 주세요: " + ex.getMessage());
        }
        if (row.isEmpty()) {
            if (notes != null) {
                notes.add("법령 기준값(" + item.code() + ", " + on + " 기준)이 없어 확인하지 못했어요");
            }
            return Optional.empty();
        }
        if (!item.unit().equals(row.get().unit())) {
            throw FamilyLeaveException.conflict("법령 기준값(" + item.code() + ") 단위가 " + item.unit() + "가 아니에요");
        }
        try {
            return Optional.of(row.get().rateValue().intValueExact());
        } catch (ArithmeticException ex) {
            throw FamilyLeaveException.conflict("법령 기준값(" + item.code() + ")이 정수가 아니에요");
        }
    }

    private int req(LegalRateItem item, LocalDate on) {
        return opt(item, on, null).orElseThrow(() -> FamilyLeaveException.conflict(
                "법령 기준값(" + item.code() + ", " + on + " 기준)이 없어 처리할 수 없어요"));
    }

    private static FamilyLeaveException missing(String what) {
        return FamilyLeaveException.conflict(what + " 법령 기준값이 없어 처리할 수 없어요");
    }

    // ---------------------------------------------------------------- 공통

    private FamilyLeaveRequest load(long reqId) {
        return requests.findById(reqId).orElseThrow(() -> FamilyLeaveException.notFound("신청을 찾을 수 없어요: " + reqId));
    }

    private FamilyLeaveRequest lockRequested(long reqId, String verb) {
        FamilyLeaveRequest r = requests.findForUpdate(reqId)
                .orElseThrow(() -> FamilyLeaveException.notFound("신청을 찾을 수 없어요: " + reqId));
        if (r.getStatus() != Status.REQUESTED) {
            throw FamilyLeaveException.conflict("이미 처리된 신청(" + r.getStatus() + ")은 " + verb + " 수 없어요");
        }
        return r;
    }

    private void loadEmployee(long empId) {
        if (!employees.existsById(empId)) {
            throw FamilyLeaveException.notFound("사원을 찾을 수 없어요: " + empId);
        }
    }

    private static String minutes(int m) {
        return m / 60 + "시간" + (m % 60 == 0 ? "" : " " + m % 60 + "분");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
