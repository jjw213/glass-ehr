package com.glassehr.overtime;

import com.glassehr.approval.ApprovalCompletionHandler;
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
    private final OvertimeLimitChecker limits;
    private final Clock clock;

    @Autowired
    public OvertimeApprovalHandler(OvertimeRequestRepository requests, OvertimeLimitChecker limits) {
        this(requests, limits, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    OvertimeApprovalHandler(OvertimeRequestRepository requests, OvertimeLimitChecker limits, Clock clock) {
        this.requests = requests;
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
    public void onApproved(long requestRef) {
        OvertimeRequest r = lock(requestRef);
        limits.check(r);
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
}
