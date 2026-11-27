package com.glassehr.overtime;

import com.glassehr.approval.ApprovalDoc;
import com.glassehr.approval.ApprovalException;
import com.glassehr.approval.ApprovalService;
import com.glassehr.auth.AuthUser;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.overtime.OvertimeDtos.AuthRequest;
import com.glassehr.overtime.OvertimeDtos.AuthView;
import com.glassehr.overtime.OvertimeDtos.CreateRequest;
import com.glassehr.overtime.OvertimeDtos.RequestView;
import com.glassehr.attendance.WorkPolicyRules;
import com.glassehr.attendance.WorkPolicyService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연장·휴일근로 사전 신청(#30). 신청을 저장하고 공통 결재로 올린다. 결과 반영은 {@link OvertimeApprovalHandler}.
 * 특별연장근로 인가 정보(근로기준법 제53조 제4항)는 입력·조회만 한다.
 */
@Service
public class OvertimeService {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 내 신청 목록 최대 건수(운영 보호용). */
    static final int LIST_LIMIT = 200;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<OvertimeRequest.Status> ACTIVE =
            List.of(OvertimeRequest.Status.IN_PROGRESS, OvertimeRequest.Status.APPROVED);

    private final OvertimeRequestRepository requests;
    private final SpecialOvertimeAuthRepository auths;
    private final ApprovalService approvals;
    private final EmployeeRepository employees;
    private final WorkPolicyService policies;
    private final Clock clock;

    @Autowired
    public OvertimeService(OvertimeRequestRepository requests, SpecialOvertimeAuthRepository auths,
            ApprovalService approvals, EmployeeRepository employees, WorkPolicyService policies) {
        this(requests, auths, approvals, employees, policies, Clock.system(SEOUL));
    }

    OvertimeService(OvertimeRequestRepository requests, SpecialOvertimeAuthRepository auths,
            ApprovalService approvals, EmployeeRepository employees, WorkPolicyService policies, Clock clock) {
        this.policies = policies;
        this.requests = requests;
        this.auths = auths;
        this.approvals = approvals;
        this.employees = employees;
        this.clock = clock;
    }

    /** 사전 신청: 시작 시각이 지금(서울) 이후여야 한다. 같은 시간대에 결재 중·승인 신청이 있으면 409. */
    @Transactional
    public RequestView create(CreateRequest req, AuthUser user) {
        long emp = requireEmp(user);
        LocalDateTime now = LocalDateTime.now(clock.withZone(SEOUL));
        LocalDateTime start = req.startAt().withSecond(0).withNano(0);
        LocalDateTime end = req.endAt().withSecond(0).withNano(0);
        int breaks = req.breakMinutes() == null ? 0 : req.breakMinutes();
        if (!end.isAfter(start)) {
            throw bad("종료 시각은 시작 시각보다 뒤여야 해요");
        }
        if (start.isBefore(now)) {
            throw bad("사전 신청이라 이미 시작한 시간은 신청할 수 없어요");
        }
        int planned = OvertimeRequest.plannedMinutes(start, end, breaks);
        if (planned <= 0) {
            throw bad("휴게시간을 빼면 근로시간이 남지 않아요");
        }
        // 신청·결재는 로그인 세션의 현재 법인으로만 만들고, 같은 사원의 신청 합계 조회와 저장을
        // 직렬화해 동시 신청이 정책 한도를 우회하지 못하게 한다.
        String enterCd = user.enterCd();
        employees.findForUpdate(enterCd, emp)
                .orElseThrow(() -> new ApprovalException(HttpStatus.NOT_FOUND, "사원을 찾을 수 없어요"));
        checkPolicy(enterCd, emp, req.workKind(), start.toLocalDate(), planned);
        if (!requests.findOverlapping(enterCd, emp, start, end, ACTIVE).isEmpty()) {
            throw new ApprovalException(HttpStatus.CONFLICT, "같은 시간대에 결재 중이거나 승인된 신청이 있어요");
        }
        OvertimeRequest r = requests.saveAndFlush(
                new OvertimeRequest(enterCd, emp, req.workKind(), start, end, breaks, req.reason().strip(), now));
        String title = (r.getWorkKind() == OvertimeRequest.Kind.OVERTIME ? "연장근로" : "휴일근로") + " "
                + r.getWorkDate() + " " + start.format(HM) + "~" + end.format(HM);
        long approvalId = approvals.submit(enterCd, OvertimeApprovalHandler.REQUEST_TYPE, r.getRequestId(), title,
                emp);
        return view(r, approvalId);
    }

    @Transactional(readOnly = true)
    public List<RequestView> mine(AuthUser user) {
        long emp = requireEmp(user);
        String enterCd = user.enterCd();
        return requests.findByEnterCdAndEmpIdOrderByWorkDateDescStartAtDesc(enterCd, emp,
                        PageRequest.of(0, LIST_LIMIT)).stream()
                .map(r -> {
                    ApprovalDoc d = approvals.findByRequest(enterCd, OvertimeApprovalHandler.REQUEST_TYPE,
                            r.getRequestId());
                    return view(r, d == null ? null : d.getApprovalId());
                })
                .toList();
    }

    @Transactional
    public AuthView createAuth(AuthRequest req, AuthUser user) {
        if (!Boolean.TRUE.equals(req.workerConsent())) {
            // 근로기준법 제53조 제4항: 고용노동부장관의 인가와 근로자의 동의를 받아야 한다.
            throw bad("근로자 동의를 받은 경우에만 등록할 수 있어요(근로기준법 제53조 제4항)");
        }
        if (req.periodTo().isBefore(req.periodFrom())) {
            throw bad("종료일은 시작일과 같거나 뒤여야 해요");
        }
        String authNo = req.authNo().strip();
        String enterCd = user.enterCd();
        if (auths.existsByEnterCdAndAuthNo(enterCd, authNo)) {
            throw new ApprovalException(HttpStatus.CONFLICT, "이미 등록된 인가 번호예요: " + authNo);
        }
        // 대상은 현재 법인 사원만(#127). 다른 법인 사원은 없는 사원과 같이 답한다.
        if (employees.findByEnterCdAndEmpIdIn(enterCd, req.empIds()).size() != req.empIds().size()) {
            throw bad("없는 사원이 대상에 들어 있어요");
        }
        SpecialOvertimeAuth a = auths.save(new SpecialOvertimeAuth(enterCd, req.authKind(), authNo, req.laborOffice().strip(),
                req.authorizedOn(), req.periodFrom(), req.periodTo(), req.reason().strip(), req.empIds(),
                user.loginId(), LocalDateTime.now(clock.withZone(SEOUL))));
        return authView(a);
    }

    @Transactional(readOnly = true)
    public List<AuthView> auths(AuthUser user) {
        return auths.findByEnterCdOrderByPeriodFromDesc(user.enterCd()).stream().map(OvertimeService::authView).toList();
    }

    /**
     * 근무 정책(#159)의 허용 방식으로 신청을 거른다. 허용 안 함이면 거부, 한도 방식이면 같은 종류의 결재 중·승인 신청과
     * 합친 예정 시간이 일·주(월~일)·월 한도를 넘으면 거부. 정책 한도는 회사가 정한 값이고, 법정 1주 한도 검사는
     * 승인 때 {@link OvertimeLimitChecker}가 따로 한다.
     */
    private void checkPolicy(String enterCd, long emp, OvertimeRequest.Kind kind, LocalDate workDate, int planned) {
        WorkPolicyRules.Policy p = policies.forEmployee(enterCd, emp, workDate);
        WorkPolicyRules.Allowance a = kind == OvertimeRequest.Kind.OVERTIME ? p.overtime() : p.holiday();
        String label = kind == OvertimeRequest.Kind.OVERTIME ? "연장근로" : "휴일근로";
        if (a.mode() == WorkPolicyRules.AllowMode.DENY) {
            throw bad("근무 정책에서 " + label + " 신청을 허용하지 않아요");
        }
        if (a.mode() != WorkPolicyRules.AllowMode.LIMIT) {
            return;
        }
        LocalDate monday = workDate.with(DayOfWeek.MONDAY);
        LocalDate monthStart = workDate.withDayOfMonth(1);
        LocalDate from = monday.isBefore(monthStart) ? monday : monthStart;
        LocalDate to = monday.plusDays(6).isAfter(workDate.withDayOfMonth(workDate.lengthOfMonth()))
                ? monday.plusDays(6) : workDate.withDayOfMonth(workDate.lengthOfMonth());
        int day = planned;
        int week = planned;
        int month = planned;
        for (OvertimeRequest r : requests.findByEnterCdAndEmpIdAndWorkDateBetweenAndStatusIn(
                enterCd, emp, from, to, ACTIVE)) {
            if (r.getWorkKind() != kind) {
                continue;
            }
            LocalDate d = r.getWorkDate();
            if (d.equals(workDate)) {
                day += r.getPlannedMinutes();
            }
            if (!d.isBefore(monday) && !d.isAfter(monday.plusDays(6))) {
                week += r.getPlannedMinutes();
            }
            if (d.getMonth() == workDate.getMonth() && d.getYear() == workDate.getYear()) {
                month += r.getPlannedMinutes();
            }
        }
        if (over(day, a.dayLimit()) || over(week, a.weekLimit()) || over(month, a.monthLimit())) {
            throw bad("근무 정책의 " + label + " 한도(일 " + limitText(a.dayLimit()) + "·주 " + limitText(a.weekLimit())
                    + "·월 " + limitText(a.monthLimit()) + ")를 넘어요");
        }
    }

    private static boolean over(int minutes, Integer limit) {
        return limit != null && minutes > limit;
    }

    private static String limitText(Integer limit) {
        return limit == null ? "없음" : OvertimeLimitChecker.hm(limit);
    }

    private static RequestView view(OvertimeRequest r, Long approvalId) {
        return new RequestView(r.getRequestId(), r.getEmpId(), r.getWorkKind(), r.getWorkDate(), r.getStartAt(),
                r.getEndAt(), r.getBreakMinutes(), r.getPlannedMinutes(), r.getReason(), r.getStatus(),
                r.getRequestedAt(), r.getDecidedAt(), approvalId);
    }

    private static AuthView authView(SpecialOvertimeAuth a) {
        return new AuthView(a.getAuthId(), a.getAuthKind(), a.getAuthNo(), a.getLaborOffice(), a.getAuthorizedOn(),
                a.getPeriodFrom(), a.getPeriodTo(), a.getReason(), a.getEmpIds().stream().sorted().toList(),
                a.getCreatedBy(), a.getCreatedAt());
    }

    private static long requireEmp(AuthUser user) {
        if (user.empId() == null) {
            throw new ApprovalException(HttpStatus.FORBIDDEN, "사원과 연결된 계정만 신청할 수 있어요");
        }
        return user.empId();
    }

    private static ApprovalException bad(String msg) {
        return new ApprovalException(HttpStatus.BAD_REQUEST, msg);
    }
}
