package com.glassehr.auth;

import com.glassehr.code.YnConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 로그인 사용자와 부여 역할. 테이블: V013 app_user, app_user_role. 관리 화면은 후속 이슈(지금은 SQL로 넣음).
 * V032(#119): 키는 (enter_cd, login_id). 같은 아이디가 법인마다 따로 있을 수 있다(docs/tenant-design.md 4.1).
 */
@Entity
@Table(name = "app_user")
@IdClass(AppUser.Key.class)
public class AppUser {

    /** 복합 키(법인, 로그인 아이디). */
    public record Key(String enterCd, String loginId) implements Serializable {
    }

    @Id
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Id
    @Column(name = "login_id", nullable = false, updatable = false)
    private String loginId;

    @Column(name = "emp_id")
    private Long empId;

    // V013 DDL이 CHAR(1)이므로 JDBC 타입을 CHAR로 맞춘다(ddl-auto=validate 통과용).
    // 다른 *_yn 컬럼(V006·V009 use_yn)은 VARCHAR2(1 CHAR)라 기본 매핑을 그대로 둔다.
    @Convert(converter = YnConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "active_yn", nullable = false, length = 1)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = {
            @JoinColumn(name = "enter_cd", referencedColumnName = "enter_cd"),
            @JoinColumn(name = "login_id", referencedColumnName = "login_id")})
    @Column(name = "role_code", nullable = false)
    private Set<String> roles = new HashSet<>();

    // V027(#76). 비밀번호 해시(BCrypt). 원문은 저장하지 않고, 이 값은 응답·로그로 내보내지 않는다.
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    protected AppUser() {
    }

    AppUser(String enterCd, String loginId, Long empId, boolean active, Set<String> roles) {
        this.enterCd = enterCd;
        this.loginId = loginId;
        this.empId = empId;
        this.active = active;
        this.roles = new HashSet<>(roles);
    }

    String getPasswordHash() {
        return passwordHash;
    }

    LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    int getFailedLoginCount() {
        return failedLoginCount;
    }

    LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    /** 지금 잠겨 있는가(잠금 시각이 지나면 풀린 것으로 본다). */
    boolean isLockedAt(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    void changePassword(String hash, LocalDateTime now) {
        this.passwordHash = hash;
        this.passwordChangedAt = now;
    }

    /**
     * 비밀번호가 틀렸을 때. 지난 잠금이 풀린 뒤의 첫 실패면 횟수를 다시 센다.
     *
     * @return 이번 실패로 잠겼으면 true
     */
    boolean recordFailure(LocalDateTime now, int maxFailures, java.time.Duration lockFor) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            failedLoginCount = 0;
            lockedUntil = null;
        }
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = now.plus(lockFor);
            return true;
        }
        return false;
    }

    void recordSuccess() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public String getEnterCd() {
        return enterCd;
    }

    public String getLoginId() {
        return loginId;
    }

    public Long getEmpId() {
        return empId;
    }

    public boolean isActive() {
        return active;
    }

    public Set<String> getRoles() {
        return Set.copyOf(roles);
    }
}
