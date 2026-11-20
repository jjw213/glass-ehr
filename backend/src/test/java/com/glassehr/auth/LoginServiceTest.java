package com.glassehr.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.auth.LoginAccessLog.Event;
import com.glassehr.auth.LoginAccessLog.FailReason;
import com.glassehr.auth.LoginService.Outcome;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 로그인 판정·잠금·접속기록(DB 없이). 실패 3회·잠금 10분으로 설정한다. */
class LoginServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 19, 9, 0);
    private static final String PW = "correct-horse-1";

    private AppUserRepository users;
    private LoginAccessLogRepository logs;
    private final List<LoginAccessLog> saved = new ArrayList<>();
    private AppUser user;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        logs = mock(LoginAccessLogRepository.class);
        when(logs.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        user = new AppUser("u1", null, true, Set.of());
        user.changePassword(LoginService.hash(PW), T0.minusDays(1));
        when(users.findByIdForUpdate("u1")).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate("nobody")).thenReturn(Optional.empty());
    }

    private LoginService at(LocalDateTime now) {
        return new LoginService(users, logs, 3, Duration.ofMinutes(10), Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL));
    }

    private LoginAccessLog last() {
        return saved.get(saved.size() - 1);
    }

    @Test
    void 맞는_비밀번호면_성공하고_실패_횟수를_지운다() {
        at(T0).login("u1", "wrong", "10.0.0.1");
        LoginService.Result r = at(T0).login("  u1 ", PW, "10.0.0.1");
        assertThat(r.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(r.loginId()).isEqualTo("u1");
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(last().getEvent()).isEqualTo(Event.LOGIN_SUCCESS);
        assertThat(last().getFailReason()).isNull();
        assertThat(last().getClientIp()).isEqualTo("10.0.0.1");
        assertThat(last().getOccurredAt()).isEqualTo(T0);
    }

    @Test
    void 없는_아이디_사용중지_비밀번호_미설정은_같은_응답이고_사유만_기록한다() {
        assertThat(at(T0).login("nobody", PW, null).outcome()).isEqualTo(Outcome.BAD_CREDENTIALS);
        assertThat(last().getFailReason()).isEqualTo(FailReason.UNKNOWN_USER);
        assertThat(last().getLoginId()).isEqualTo("nobody");

        AppUser off = new AppUser("off", null, false, Set.of());
        off.changePassword(LoginService.hash(PW), T0);
        when(users.findByIdForUpdate("off")).thenReturn(Optional.of(off));
        assertThat(at(T0).login("off", PW, null).outcome()).isEqualTo(Outcome.BAD_CREDENTIALS);
        assertThat(last().getFailReason()).isEqualTo(FailReason.INACTIVE);

        when(users.findByIdForUpdate("nopw")).thenReturn(Optional.of(new AppUser("nopw", null, true, Set.of())));
        assertThat(at(T0).login("nopw", PW, null).outcome()).isEqualTo(Outcome.BAD_CREDENTIALS);
        assertThat(last().getFailReason()).isEqualTo(FailReason.NO_PASSWORD);
        verify(users, never()).save(any());
    }

    @Test
    void 설정한_횟수만큼_틀리면_잠기고_잠긴_동안은_맞는_비밀번호도_거부한다() {
        assertThat(at(T0).login("u1", "x1", null).outcome()).isEqualTo(Outcome.BAD_CREDENTIALS);
        assertThat(at(T0).login("u1", "x2", null).outcome()).isEqualTo(Outcome.BAD_CREDENTIALS);
        assertThat(last().isLocked()).isFalse();
        LoginService.Result third = at(T0).login("u1", "x3", null);
        assertThat(third.outcome()).isEqualTo(Outcome.LOCKED);
        assertThat(third.lockedUntil()).isEqualTo(T0.plusMinutes(10));
        assertThat(last().getFailReason()).isEqualTo(FailReason.BAD_PASSWORD);
        assertThat(last().isLocked()).isTrue();

        LoginService.Result during = at(T0.plusMinutes(9).plusSeconds(59)).login("u1", PW, null);
        assertThat(during.outcome()).isEqualTo(Outcome.LOCKED);
        assertThat(last().getFailReason()).isEqualTo(FailReason.LOCKED);
        assertThat(user.getFailedLoginCount()).isEqualTo(3);
    }

    @Test
    void 잠금_시각이_지나면_풀리고_실패_횟수를_다시_센다() {
        for (int i = 0; i < 3; i++) {
            at(T0).login("u1", "bad", null);
        }
        // 잠금 종료 시각 정각에는 풀린 것으로 본다.
        assertThat(at(T0.plusMinutes(10)).login("u1", "bad", null).outcome()).isEqualTo(Outcome.BAD_CREDENTIALS);
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        assertThat(at(T0.plusMinutes(10)).login("u1", PW, null).outcome()).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void 로그아웃을_기록한다() {
        at(T0).logout("u1", "10.0.0.2");
        assertThat(last().getEvent()).isEqualTo(Event.LOGOUT);
        assertThat(last().isLocked()).isFalse();
    }

    @Test
    void 해시는_BCrypt_형식이고_원문과_다르다() {
        String h = LoginService.hash(PW);
        assertThat(h).startsWith("$2").hasSize(60).doesNotContain(PW);
    }

    @Test
    void 잘못된_설정값은_거부한다() {
        assertThatThrownBy(() -> new LoginService(users, logs, 0, Duration.ofMinutes(1), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginService(users, logs, 3, Duration.ZERO, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
