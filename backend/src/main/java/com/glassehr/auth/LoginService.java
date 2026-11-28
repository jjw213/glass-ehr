package com.glassehr.auth;

import com.glassehr.auth.LoginAccessLog.Event;
import com.glassehr.auth.LoginAccessLog.FailReason;
import com.glassehr.tenant.EnterCompanyGate;
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
        LOCKED,
        /** 비밀번호는 맞지만 고객사가 정지·해지·체험 종료(#151, docs/saas-design.md 2.2) */
        TENANT_UNAVAILABLE
    }

    /**
     * @param tenantId    SUCCESS 일 때 로그인한 법인의 고객사
     * @param lockedUntil LOCKED 일 때 잠금이 풀리는 시각(서울)
     */
    public record Result(Outcome outcome, String enterCd, String loginId, String tenantId, LocalDateTime lockedUntil) {
    }

    private final AppUserRepository users;
    private final LoginAccessLogRepository logs;
    private final EnterCompanyGate companies;
    private final int maxFailures;
    private final Duration lockFor;
    private final Clock clock;

    @Autowired
    public LoginService(AppUserRepository users, LoginAccessLogRepository logs, EnterCompanyGate companies,
            @Value("${glassehr.auth.max-failed-attempts:5}") int maxFailures,
            @Value("${glassehr.auth.lock-duration:30m}") Duration lockFor) {
        this(users, logs, companies, maxFailures, lockFor, Clock.system(SEOUL));
    }

    LoginService(AppUserRepository users, LoginAccessLogRepository logs, EnterCompanyGate companies, int maxFailures,
            Duration lockFor, Clock clock) {
        if (maxFailures < 1 || lockFor.isNegative() || lockFor.isZero()) {
            throw new IllegalArgumentException("glassehr.auth.max-failed-attempts 는 1 이상, lock-duration 은 0보다 커야 해요");
        }
        this.users = users;
        this.logs = logs;
        this.companies = companies;
        this.maxFailures = maxFailures;
        this.lockFor = lockFor;
        this.clock = clock;
    }

    /** 데모 계정 시드 등에서 비밀번호를 해시할 때 쓴다. */
    public static String hash(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 회사(법인) + 아이디 + 비밀번호 확인(#119). 실패 횟수·잠금은 (법인, 아이디) 행마다 따로 센다.
     * 없는 법인·사용 중지 법인·다법인 배포 잠금으로 막힌 법인은 그 법인에 없는 아이디와 같은 결과(UNKNOWN_USER)·같은
     * 처리 시간으로 거부해 법인 존재 여부를 드러내지 않는다(docs/tenant-design.md 4.1).
     */
    @Transactional
    public Result login(String enterCd, String loginId, String rawPassword, String clientIp) {
        String ent = enterCd.strip();
        String id = loginId.strip();
        LocalDateTime now = LocalDateTime.now(clock);
        if (!companies.allows(ent)) {
            ENCODER.matches(rawPassword, DUMMY_HASH);
            return fail(ent, id, FailReason.UNKNOWN_USER, clientIp, now);
        }
        // BCrypt 비교가 끝나고 실패 횟수를 저장할 때까지 행 잠금을 유지해 동시 실패 갱신 유실을 막는다.
        Optional<AppUser> found = users.findForUpdate(ent, id);
        if (found.isEmpty()) {
            ENCODER.matches(rawPassword, DUMMY_HASH);
            return fail(ent, id, FailReason.UNKNOWN_USER, clientIp, now);
        }
        AppUser u = found.get();
        if (!u.isActive()) {
            ENCODER.matches(rawPassword, DUMMY_HASH);
            return fail(ent, id, FailReason.INACTIVE, clientIp, now);
        }
        if (u.isLockedAt(now)) {
            log(ent, id, Event.LOGIN_FAILURE, FailReason.LOCKED, false, clientIp, now);
            return new Result(Outcome.LOCKED, ent, id, null, u.getLockedUntil());
        }
        if (u.getPasswordHash() == null) {
            ENCODER.matches(rawPassword, DUMMY_HASH);
            return fail(ent, id, FailReason.NO_PASSWORD, clientIp, now);
        }
        if (!ENCODER.matches(rawPassword, u.getPasswordHash())) {
            boolean lockedNow = u.recordFailure(now, maxFailures, lockFor);
            users.save(u);
            log(ent, id, Event.LOGIN_FAILURE, FailReason.BAD_PASSWORD, lockedNow, clientIp, now);
            return lockedNow ? new Result(Outcome.LOCKED, ent, id, null, u.getLockedUntil())
                    : new Result(Outcome.BAD_CREDENTIALS, ent, id, null, null);
        }
        // 고객사 상태는 비밀번호가 맞은 뒤에만 알린다(모르는 사람에게 고객사 정지 여부를 드러내지 않게).
        // 정지·해지·체험 종료면 로그인을 거부하고 안내한다(#151, docs/saas-design.md 2.2). 실패 횟수는 건드리지 않는다.
        String tenantId = companies.tenantOf(ent);
        if (!companies.tenantUsable(tenantId)) {
            log(ent, id, Event.LOGIN_FAILURE, FailReason.TENANT_UNAVAILABLE, false, clientIp, now);
            return new Result(Outcome.TENANT_UNAVAILABLE, ent, id, null, null);
        }
        u.recordSuccess();
        users.save(u);
        log(ent, id, Event.LOGIN_SUCCESS, null, false, clientIp, now);
        return new Result(Outcome.SUCCESS, u.getEnterCd(), u.getLoginId(), tenantId, null);
    }

    @Transactional
    public void logout(String enterCd, String loginId, String clientIp) {
        log(enterCd, loginId, Event.LOGOUT, null, false, clientIp, LocalDateTime.now(clock));
    }

    /**
     * 그룹 관리자 법인 전환(#120, docs/tenant-design.md 4.3). 권한(ENTER_SWITCH)은 API 가 이미 검사했다.
     * 대상 법인은 로그인과 같은 규칙(형식·사용 중·다법인 배포 잠금)으로 확인하고, 통과하면 접속기록에
     * 주체 법인·아이디와 대상 법인을 남긴다(개인정보 보호법 제29조·안전성 확보조치 기준의 접속기록).
     *
     * @return 대상 법인으로 바꿀 수 있으면 true. 없거나 사용 안 함·잠금이면 false(기록하지 않음)
     */
    @Transactional
    public boolean switchEnter(AuthUser user, String targetEnterCd, String clientIp) {
        String target = targetEnterCd == null ? null : targetEnterCd.strip();
        // 같은 고객사의 법인으로만 바꾼다. 다른 고객사 법인은 없는 법인과 같이 404(docs/saas-design.md 2.3).
        if (!companies.allows(target) || !user.tenantId().equals(companies.tenantOf(target))) {
            return false;
        }
        logs.save(new LoginAccessLog(user.homeEnterCd(), user.loginId(), Event.ENTER_SWITCH, null, false, clientIp,
                LocalDateTime.now(clock), target));
        return true;
    }

    private Result fail(String ent, String id, FailReason reason, String ip, LocalDateTime now) {
        log(ent, id, Event.LOGIN_FAILURE, reason, false, ip, now);
        return new Result(Outcome.BAD_CREDENTIALS, ent, id, null, null);
    }

    private void log(String ent, String id, Event event, FailReason reason, boolean locked, String ip,
            LocalDateTime now) {
        logs.save(new LoginAccessLog(ent, id, event, reason, locked, ip, now));
    }
}
