package com.glassehr.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사원별 근무유형 배정 이력 [validFrom, validTo). 테이블: V012__work_pattern.sql.
 * 추가로만 쌓는다. 바꿀 수 있는 것은 마지막 행의 종료일(validTo)뿐이다.
 */
@Entity
@Table(name = "emp_work_pattern")
public class EmpWorkPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assign_id", nullable = false, updatable = false)
    private Long assignId;

    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Column(name = "pattern_id", nullable = false, updatable = false)
    private Long patternId;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "assign_reason", updatable = false)
    private String assignReason;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected EmpWorkPattern() {
    }

    public EmpWorkPattern(long empId, long patternId, LocalDate validFrom, String assignReason, String createdBy,
            LocalDateTime now) {
        this.empId = empId;
        this.patternId = patternId;
        this.validFrom = validFrom;
        this.assignReason = assignReason;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    void closeAt(LocalDate date) {
        this.validTo = date;
    }

    public boolean isEffectiveOn(LocalDate d) {
        return !validFrom.isAfter(d) && (validTo == null || d.isBefore(validTo));
    }

    public Long getAssignId() {
        return assignId;
    }

    public Long getEmpId() {
        return empId;
    }

    public Long getPatternId() {
        return patternId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getAssignReason() {
        return assignReason;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
