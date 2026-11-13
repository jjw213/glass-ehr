package com.glassehr.auth;

import com.glassehr.code.YnConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** 로그인 사용자와 부여 역할. 테이블: V011 app_user, app_user_role. 관리 화면은 후속 이슈(지금은 SQL로 넣음). */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(name = "login_id", nullable = false, updatable = false)
    private String loginId;

    @Column(name = "emp_id")
    private Long empId;

    @Convert(converter = YnConverter.class)
    @Column(name = "active_yn", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "login_id"))
    @Column(name = "role_code", nullable = false)
    private Set<String> roles = new HashSet<>();

    protected AppUser() {
    }

    AppUser(String loginId, Long empId, boolean active, Set<String> roles) {
        this.loginId = loginId;
        this.empId = empId;
        this.active = active;
        this.roles = new HashSet<>(roles);
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
