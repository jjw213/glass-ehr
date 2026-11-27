package com.glassehr.overtime;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 특별연장근로 인가 정보(근로기준법 제53조 제4항: 고용노동부장관의 인가와 근로자의 동의). 테이블: V025.
 * 이 이슈에서는 입력·조회만 한다. 한도 검사와 연결하지 않는다.
 */
@Entity
@Table(name = "special_overtime_auth")
public class SpecialOvertimeAuth {

    /** PRIOR 사전 인가, POST 사후 승인(제53조 제4항 단서). V025 ck_sot_auth_kind 와 같다. */
    public enum Kind { PRIOR, POST }

    /**
     * 인가 대상 사원(special_overtime_auth_emp 한 행). 법인을 함께 저장해 복합 FK (enter_cd, emp_id)·(enter_cd, auth_id)로
     * 다른 법인 사원을 대상에 넣을 수 없게 한다(#127, V036·V046).
     */
    @Embeddable
    public static class Target {
        @Column(name = "enter_cd", nullable = false, updatable = false)
        private String enterCd;

        @Column(name = "emp_id", nullable = false, updatable = false)
        private long empId;

        protected Target() {
        }

        Target(String enterCd, long empId) {
            this.enterCd = enterCd;
            this.empId = empId;
        }

        public long getEmpId() {
            return empId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Target t && empId == t.empId && Objects.equals(enterCd, t.enterCd);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enterCd, empId);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_id", nullable = false, updatable = false)
    private Long authId;

    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_kind", nullable = false, updatable = false)
    private Kind authKind;

    @Column(name = "auth_no", nullable = false, updatable = false)
    private String authNo;

    @Column(name = "labor_office", nullable = false, updatable = false)
    private String laborOffice;

    @Column(name = "authorized_on", nullable = false, updatable = false)
    private LocalDate authorizedOn;

    @Column(name = "period_from", nullable = false, updatable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false, updatable = false)
    private LocalDate periodTo;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    // V025 DDL의 CHAR(1)과 Hibernate 스키마 검증의 JDBC 타입을 맞춘다.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "worker_consent", nullable = false, updatable = false, length = 1)
    private String workerConsent;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "special_overtime_auth_emp", joinColumns = @JoinColumn(name = "auth_id"))
    private Set<Target> targets = new LinkedHashSet<>();

    protected SpecialOvertimeAuth() {
    }

    SpecialOvertimeAuth(String enterCd, Kind authKind, String authNo, String laborOffice, LocalDate authorizedOn,
            LocalDate periodFrom, LocalDate periodTo, String reason, Set<Long> empIds, String createdBy,
            LocalDateTime createdAt) {
        this.enterCd = enterCd;
        this.authKind = authKind;
        this.authNo = authNo;
        this.laborOffice = laborOffice;
        this.authorizedOn = authorizedOn;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.reason = reason;
        this.workerConsent = "Y";
        for (Long e : empIds) {
            this.targets.add(new Target(enterCd, e));
        }
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getAuthId() {
        return authId;
    }

    public Kind getAuthKind() {
        return authKind;
    }

    public String getAuthNo() {
        return authNo;
    }

    public String getLaborOffice() {
        return laborOffice;
    }

    public LocalDate getAuthorizedOn() {
        return authorizedOn;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public String getReason() {
        return reason;
    }

    public Set<Long> getEmpIds() {
        return targets.stream().map(Target::getEmpId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
