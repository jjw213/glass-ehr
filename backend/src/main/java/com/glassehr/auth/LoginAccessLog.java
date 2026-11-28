package com.glassehr.auth;

import com.glassehr.code.YnConverter;
import com.glassehr.tenant.EnterCompany;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 로그인·로그아웃 접속기록(추가만). 테이블: V027 login_access_log. 비밀번호는 남기지 않는다.
 * 개인정보 보호법 제29조·개인정보의 안전성 확보조치 기준(접속기록 보관): 누가(loginId), 언제(occurredAt),
 * 어디서(clientIp), 무엇을(event·failReason).
 */
@Entity
@Immutable
@Table(name = "login_access_log")
public class LoginAccessLog {

    /** V033 ck_login_log_event 와 같아야 함. */
    public enum Event {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        /** 그룹 관리자 법인 전환(V033). enterCd = 주체 법인, targetEnterCd = 전환 대상 법인 */
        ENTER_SWITCH
    }

    /** V027 ck_login_log_reason 과 같아야 함. */
    public enum FailReason {
        UNKNOWN_USER,
        BAD_PASSWORD,
        LOCKED,
        INACTIVE,
        NO_PASSWORD,
        /** 고객사 정지·해지·체험 종료(V051, #151) */
        TENANT_UNAVAILABLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false, updatable = false)
    private Long logId;

    /** 로그인 시도 법인(V031·V032, NULL 허용·FK 없음). 형식이 맞지 않는 입력이면 NULL(tenant-design.md 4.4). */
    @Column(name = "enter_cd", updatable = false)
    private String enterCd;

    /** 법인 전환 대상 법인(V033). ENTER_SWITCH 일 때만 있다. */
    @Column(name = "target_enter_cd", updatable = false)
    private String targetEnterCd;

    @Column(name = "login_id", nullable = false, updatable = false)
    private String loginId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_code", nullable = false, updatable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_reason", updatable = false)
    private FailReason failReason;

    @Convert(converter = YnConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "locked_yn", nullable = false, updatable = false, length = 1)
    private boolean locked;

    @Column(name = "client_ip", updatable = false)
    private String clientIp;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    protected LoginAccessLog() {
    }

    LoginAccessLog(String enterCd, String loginId, Event event, FailReason failReason, boolean locked,
            String clientIp, LocalDateTime occurredAt) {
        this(enterCd, loginId, event, failReason, locked, clientIp, occurredAt, null);
    }

    LoginAccessLog(String enterCd, String loginId, Event event, FailReason failReason, boolean locked,
            String clientIp, LocalDateTime occurredAt, String targetEnterCd) {
        this.enterCd = EnterCompany.isValidCode(enterCd) ? enterCd : null;
        this.targetEnterCd = targetEnterCd;
        this.loginId = cut(loginId, 50);
        this.event = event;
        this.failReason = failReason;
        this.locked = locked;
        this.clientIp = cut(clientIp, 45);
        this.occurredAt = occurredAt;
    }

    private static String cut(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    public Long getLogId() {
        return logId;
    }

    public String getEnterCd() {
        return enterCd;
    }

    public String getTargetEnterCd() {
        return targetEnterCd;
    }

    public String getLoginId() {
        return loginId;
    }

    public Event getEvent() {
        return event;
    }

    public FailReason getFailReason() {
        return failReason;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getClientIp() {
        return clientIp;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
