package com.glassehr.auth;

import com.glassehr.auth.LoginAccessLog.Event;
import com.glassehr.auth.LoginAccessLog.FailReason;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이디·비밀번호 확인, 로그인 실패 횟수 제한·잠금, 접속기록(#76, docs/auth-design.md 8장).
 *
 * <ul>
 *   <li>비밀번호는 BCrypt 해시로만 비교한다. 원문은 저장·기록하지 않는다.</li>
 *   <li>틀린 비밀번호가 {@code glassehr.auth.max-failed-attempts}번 이어지면 {@code glassehr.auth.lock-duration} 동안 잠근다.
 *       잠긴 동안은 맞는 비밀번호도 거부하고 횟수는 더 세지 않는다. 두 값은 법정 수치가 아니라 운영 설정이다.</li>
 *   <li>모든 시도(성공·실패)와 로그아웃을 login_access_log 에 남긴다. 없는 아이디도 남긴다.</li>
 * </ul>
 * 개인정보 보호법 제29조·개인정보의 안전성 확보조치 기준: 정당한 권한자만 접근하게 인증하고, 일정 횟수 이상 인증에 실패하면
 * 접근을 제한하며, 접속기록을 보관한다.
 */
@Service
public class LoginService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    /** 없는 아이디도 같은 시간이 걸리게 비교할 가짜 해시(아이디 존재 여부를 응답 시간으로 알 수 없게). */
    private static final String DUMMY_HASH = ENCODER.encode(UUID.randomUUID().toString());

    public enum Outcome {
        SUCCESS,
        /** 아이디 없음·비밀번호 틀림·사용 중지·비밀번호 미설정(응답에서는 구분하지 않는다) */
        BAD_CREDENTIALS,
        LOCKED
    }

    /** @param lockedUntil LOCKED 일 때 잠금이 풀리는 시각(서울) */
    public record Result(Outcome outcome, String loginId, LocalDateTime lockedUntil) {
    }

    private final AppUserRepository users;
    private final LoginAccessLogRepository logs;
    private final int maxFailures;
    private final Duration lockFor;
    private final Clock clock;

    @Autowired
    public LoginService(AppUserRepository users, LoginAccessLogRepository logs,
            @Value("${glassehr.auth.max-failed-attempts:5}") int maxFailures,
            @Value("${glassehr.auth.lock-duration:30m}") Duration lockFor) {
        this(users, logs, maxFailures, lockFor, Clock.system(SEOUL));
    }

    LoginService(AppUserRepository users, LoginAccessLogRepository logs, int maxFailures, Duration lockFor, Clock clock) {
        if (maxFailures < 1 || lockFor.isNegative() || lockFor.isZero()) {
            throw new IllegalArgumentException("glassehr.auth.max-failed-attempts 는 1 이상, lock-duration 은 0보다 커야 해요");
        }
        this.users = users;
        this.logs = logs;
        this.maxFailures = maxFailures;
        this.lockFor = lockFor;
        this.clock = clock;
    }

    /** 데모 계정 시드 등에서 비밀번호를 해시할 때 쓴다. */
    public static String hash(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    @Transactional
    public Result login(String loginId, String rawPassword, String clientIp) {
        String id = loginId.strip();
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<AppUser> found = users.findById(id);
        if (found.isEmpty()) {
            ENCODER.matches(rawPassword, DUMMY_HASH);
            return fail(id, FailReason.UNKNOWN_USER, false, clientIp, now);
        }
        AppUser u = found.get();
        if (!u.isActive()) {
            ENCODER.matches(rawPassword, DUMMY_HASH);
            return fail(id, FailReason.INACTIVE, false, clientIp, now);
        }
        if (u.isLockedAt(now)) {
            log(id, Event.LOGIN_FAILURE, FailReason.LOCKED, false, clientIp, now);
            return new Result(Outcome.LOCKED, id, u.getLockedUntil());
        }
        if (u.getPasswordHash() == null) {
            ENCODER.matches(rawPassword, DUMMY_HASH);
            return fail(id, FailReason.NO_PASSWORD, false, clientIp, now);
        }
        if (!ENCODER.matches(rawPassword, u.getPasswordHash())) {
            boolean lockedNow = u.recordFailure(now, maxFailures, lockFor);
            users.save(u);
            log(id, Event.LOGIN_FAILURE, FailReason.BAD_PASSWORD, lockedNow, clientIp, now);
            return lockedNow ? new Result(Outcome.LOCKED, id, u.getLockedUntil())
                    : new Result(Outcome.BAD_CREDENTIALS, id, null);
        }
        u.recordSuccess();
        users.save(u);
        log(id, Event.LOGIN_SUCCESS, null, false, clientIp, now);
        return new Result(Outcome.SUCCESS, u.getLoginId(), null);
    }

    @Transactional
    public void logout(String loginId, String clientIp) {
        log(loginId, Event.LOGOUT, null, false, clientIp, LocalDateTime.now(clock));
    }

    private Result fail(String id, FailReason reason, boolean locked, String ip, LocalDateTime now) {
        log(id, Event.LOGIN_FAILURE, reason, locked, ip, now);
        return new Result(Outcome.BAD_CREDENTIALS, id, null);
    }

    private void log(String id, Event event, FailReason reason, boolean locked, String ip, LocalDateTime now) {
        logs.save(new LoginAccessLog(id, event, reason, locked, ip, now));
    }
}
