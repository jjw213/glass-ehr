package com.glassehr.employee;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회 로그 기록. 별도 트랜잭션(REQUIRES_NEW)으로 커밋해, 권한 거부·대상 없음으로 호출한 쪽이 예외로 끝나도 로그는 남긴다.
 */
@Component
public class RrnAccessLogger {

    private final RrnAccessLogRepository logs;

    public RrnAccessLogger(RrnAccessLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(RequestActor actor, long empId, RrnAccessLog.Purpose purpose, RrnAccessLog.Result result,
            LocalDateTime at) {
        logs.save(new RrnAccessLog(actor, empId, purpose, result, at));
    }
}
