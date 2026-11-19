package com.glassehr.overtime;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.Set;

/**
 * 특별연장근로 인가 정보(근로기준법 제53조 제4항: 고용노동부장관의 인가와 근로자의 동의). 테이블: V025.
 * 이 이슈에서는 입력·조회만 한다. 한도 검사와 연결하지 않는다.
 */
@Entity
@Table(name = "special_overtime_auth")
public class SpecialOvertimeAuth {

    /** PRIOR 사전 인가, POST 사후 승인(제53조 제4항 단서). V025 ck_sot_auth_kind 와 같다. */
    public enum Kind { PRIOR, POST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_id", nullable = false, updatable = false)
    private Long authId;

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

    @Column(name = "worker_consent", nullable = false, updatable = false)
    private String workerConsent;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "special_overtime_auth_emp", joinColumns = @JoinColumn(name = "auth_id"))
    @Column(name = "emp_id", nullable = false)
    private Set<Long> empIds = new LinkedHashSet<>();

    protected SpecialOvertimeAuth() {
    }

    SpecialOvertimeAuth(Kind authKind, String authNo, String laborOffice, LocalDate authorizedOn,
            LocalDate periodFrom, LocalDate periodTo, String reason, Set<Long> empIds, String createdBy,
            LocalDateTime createdAt) {
        this.authKind = authKind;
        this.authNo = authNo;
        this.laborOffice = laborOffice;
        this.authorizedOn = authorizedOn;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.reason = reason;
        this.workerConsent = "Y";
        this.empIds.addAll(empIds);
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
        return empIds;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
