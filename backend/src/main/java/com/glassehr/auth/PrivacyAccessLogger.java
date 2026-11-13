package com.glassehr.auth;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인정보 조회 로그 기록. 별도 트랜잭션(REQUIRES_NEW)으로 커밋해 호출한 쪽이 예외로 끝나도 로그는 남는다.
 * 원문을 돌려주기 전에 부른다(로그를 못 남기면 예외가 나서 원문도 나가지 않음).
 */
@Component
public class PrivacyAccessLogger {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final PrivacyAccessLogRepository logs;
    private final Clock clock;

    @Autowired
    public PrivacyAccessLogger(PrivacyAccessLogRepository logs) {
        this(logs, Clock.system(SEOUL));
    }

    PrivacyAccessLogger(PrivacyAccessLogRepository logs, Clock clock) {
        this.logs = logs;
        this.clock = clock.withZone(SEOUL);
    }

    /** 로그인한 사용자의 접근 한 건. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuthUser user, Long targetEmpId, PrivacyAccessLog.Item item, PrivacyAccessLog.Action action,
            PrivacyAccessLog.Result result, String requestPath) {
        logs.save(new PrivacyAccessLog(user.loginId(), user.rolesText(), targetEmpId, item, action, result,
                user.clientIp(), requestPath, LocalDateTime.now(clock)));
    }

    /** 다운로드처럼 대상이 여럿이면 대상마다 한 행(같은 시각). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEach(AuthUser user, List<Long> targetEmpIds, PrivacyAccessLog.Item item,
            PrivacyAccessLog.Action action, String requestPath) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long id : targetEmpIds) {
            logs.save(new PrivacyAccessLog(user.loginId(), user.rolesText(), id, item, action,
                    PrivacyAccessLog.Result.SUCCESS, user.clientIp(), requestPath, now));
        }
    }

    /** 로그인하지 않았거나 등록되지 않은 사용자의 시도(거부). accessorId 는 보낸 값(없으면 anonymous). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAnonymousDenied(String accessorId, String clientIp, Long targetEmpId, PrivacyAccessLog.Item item,
            PrivacyAccessLog.Action action, String requestPath) {
        String id = accessorId == null || accessorId.isBlank() ? "anonymous" : accessorId.strip();
        logs.save(new PrivacyAccessLog(id, null, targetEmpId, item, action, PrivacyAccessLog.Result.DENIED, clientIp,
                requestPath, LocalDateTime.now(clock)));
    }
}
