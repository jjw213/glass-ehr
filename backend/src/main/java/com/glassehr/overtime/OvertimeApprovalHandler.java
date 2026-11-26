package com.glassehr.overtime;

import com.glassehr.approval.ApprovalCompletionHandler;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.overtime.OvertimeDtos.RequestView;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 결재 결과를 연장·휴일근로 신청에 반영한다. 결재 처리와 같은 트랜잭션에서 돌고,
 * 승인 시 1주 연장근로 한도({@link OvertimeLimitChecker})를 넘으면 예외로 승인 자체를 되돌린다.
 */
@Component
public class OvertimeApprovalHandler implements ApprovalCompletionHandler {

    public static final String REQUEST_TYPE = "OVERTIME";

    private final OvertimeRequestRepository requests;
    private final EmployeeRepository employees;
    private final OvertimeLimitChecker limits;
    private final Clock clock;

    @Autowired
    public OvertimeApprovalHandler(OvertimeRequestRepository requests, EmployeeRepository employees,
            OvertimeLimitChecker limits) {
        this(requests, employees, limits, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    OvertimeApprovalHandler(OvertimeRequestRepository requests, EmployeeRepository employees,
            OvertimeLimitChecker limits, Clock clock) {
        this.requests = requests;
        this.employees = employees;
        this.limits = limits;
        this.clock = clock;
    }

    @Override
    public String requestType() {
        return REQUEST_TYPE;
    }

    @Override
    public String label() {
        return "연장·휴일근로";
    }

    @Override
    public RequestView requestDetail(long requestRef) {
        OvertimeRequest r = requests.findById(requestRef)
                .orElseThrow(() -> new IllegalStateException("연장·휴일근로 신청이 없어요: " + requestRef));
        return view(r);
    }

    @Override
    public void onApproved(long requestRef) {
        OvertimeRequest r = lock(requestRef);
        // 같은 사원의 최종 승인들을 공통 사원 행 잠금으로 직렬화한 뒤 승인 합계를 조회한다.
        String enterCd = employees.findForUpdate(r.getEmpId())
                .orElseThrow(() -> new IllegalStateException("사원이 없어요: " + r.getEmpId())).getEnterCd();
        // 집계 행은 사원 법인 것만 읽는다(#126).
        limits.check(enterCd, r);
        r.decide(OvertimeRequest.Status.APPROVED, LocalDateTime.now(clock));
    }

    @Override
    public void onRejected(long requestRef) {
        lock(requestRef).decide(OvertimeRequest.Status.REJECTED, LocalDateTime.now(clock));
    }

    @Override
    public void onWithdrawn(long requestRef) {
        lock(requestRef).decide(OvertimeRequest.Status.WITHDRAWN, LocalDateTime.now(clock));
    }

    private OvertimeRequest lock(long id) {
        return requests.findForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("연장·휴일근로 신청이 없어요: " + id));
    }

    private static RequestView view(OvertimeRequest r) {
        return new RequestView(r.getRequestId(), r.getEmpId(), r.getWorkKind(), r.getWorkDate(), r.getStartAt(),
                r.getEndAt(), r.getBreakMinutes(), r.getPlannedMinutes(), r.getReason(), r.getStatus(),
                r.getRequestedAt(), r.getDecidedAt(), null);
    }
}
