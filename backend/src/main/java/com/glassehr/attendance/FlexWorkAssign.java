package com.glassehr.attendance;

import com.glassehr.attendance.FlexibleWorkCalculator.WorkSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사원별 유연근무제 적용 단위(단위기간·정산기간 한 번). 테이블: V029__flex_work_assign.sql.
 * 만든 뒤 바꾸지 않는다(계획은 {@link FlexWorkPlan}).
 */
@Entity
@Table(name = "flex_work_assign")
public class FlexWorkAssign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "flex_id", nullable = false, updatable = false)
    private Long flexId;

    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_system", nullable = false, updatable = false)
    private WorkSystem workSystem;

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(name = "job_code", updatable = false)
    private String jobCode;

    @Column(name = "core_start_min", updatable = false)
    private Integer coreStartMin;

    @Column(name = "core_end_min", updatable = false)
    private Integer coreEndMin;

    @Column(name = "deemed_minutes", updatable = false)
    private Integer deemedMinutes;

    @Column(name = "agreement_id", updatable = false)
    private Long agreementId;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected FlexWorkAssign() {
    }

    FlexWorkAssign(long empId, WorkSystem workSystem, LocalDate periodStart, LocalDate periodEnd, String jobCode,
            Integer coreStartMin, Integer coreEndMin, Integer deemedMinutes, Long agreementId, String note,
            String createdBy, LocalDateTime now) {
        this.empId = empId;
        this.workSystem = workSystem;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.jobCode = jobCode;
        this.coreStartMin = coreStartMin;
        this.coreEndMin = coreEndMin;
        this.deemedMinutes = deemedMinutes;
        this.agreementId = agreementId;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public boolean overlaps(LocalDate from, LocalDate to) {
        return !periodStart.isAfter(to) && !periodEnd.isBefore(from);
    }

    public Long getFlexId() {
        return flexId;
    }

    public Long getEmpId() {
        return empId;
    }

    public WorkSystem getWorkSystem() {
        return workSystem;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getJobCode() {
        return jobCode;
    }

    public Integer getCoreStartMin() {
        return coreStartMin;
    }

    public Integer getCoreEndMin() {
        return coreEndMin;
    }

    public Integer getDeemedMinutes() {
        return deemedMinutes;
    }

    public Long getAgreementId() {
        return agreementId;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
