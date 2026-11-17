package com.glassehr.retirement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용증명서 발급 기록. 테이블: V022 employment_certificate. 발급 당시 기재 값을 그대로 남기고 고치지 않는다.
 * 근로기준법 제39조 제2항: 근로자가 요구한 사항만 적는다 — 요구하지 않은 항목 칼럼은 비어 있다.
 */
@Entity
@Table(name = "employment_certificate")
public class EmploymentCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cert_id", nullable = false, updatable = false)
    private Long certId;

    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Column(name = "requested_on", nullable = false, updatable = false)
    private LocalDate requestedOn;

    @Column(name = "requested_items", nullable = false, updatable = false)
    private String requestedItems;

    @Column(name = "purpose", updatable = false)
    private String purpose;

    @Column(name = "emp_name", nullable = false, updatable = false)
    private String empName;

    @Column(name = "use_from", updatable = false)
    private LocalDate useFrom;

    @Column(name = "use_to", updatable = false)
    private LocalDate useTo;

    @Column(name = "job_type", updatable = false)
    private String jobType;

    @Column(name = "position_text", updatable = false)
    private String positionText;

    @Column(name = "wage_amount", updatable = false)
    private Long wageAmount;

    @Column(name = "wage_unit_code", updatable = false)
    private String wageUnitCode;

    @Column(name = "retire_reason", updatable = false)
    private String retireReason;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private String issuedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    protected EmploymentCertificate() {
    }

    /** 기재 값. 요구하지 않은 항목은 null. */
    record Content(String empName, LocalDate useFrom, LocalDate useTo, String jobType, String positionText,
            Long wageAmount, String wageUnitCode, String retireReason) {
    }

    EmploymentCertificate(long empId, LocalDate requestedOn, String requestedItems, String purpose, Content c,
            String issuedBy, LocalDateTime issuedAt) {
        this.empId = empId;
        this.requestedOn = requestedOn;
        this.requestedItems = requestedItems;
        this.purpose = purpose;
        this.empName = c.empName();
        this.useFrom = c.useFrom();
        this.useTo = c.useTo();
        this.jobType = c.jobType();
        this.positionText = c.positionText();
        this.wageAmount = c.wageAmount();
        this.wageUnitCode = c.wageUnitCode();
        this.retireReason = c.retireReason();
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt;
    }

    public Long getCertId() {
        return certId;
    }

    public Long getEmpId() {
        return empId;
    }

    public LocalDate getRequestedOn() {
        return requestedOn;
    }

    public String getRequestedItems() {
        return requestedItems;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getEmpName() {
        return empName;
    }

    public LocalDate getUseFrom() {
        return useFrom;
    }

    public LocalDate getUseTo() {
        return useTo;
    }

    public String getJobType() {
        return jobType;
    }

    public String getPositionText() {
        return positionText;
    }

    public Long getWageAmount() {
        return wageAmount;
    }

    public String getWageUnitCode() {
        return wageUnitCode;
    }

    public String getRetireReason() {
        return retireReason;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }
}
