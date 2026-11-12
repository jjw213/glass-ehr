package com.glassehr.org;

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
import java.util.Objects;

/**
 * 부서의 시점별 속성(이름·상위부서·부서장·정렬순서). 테이블: V008__org_dept.sql.
 * 추가로만 쌓는다. 바꿀 수 있는 것은 마지막 행의 종료일(validTo)뿐이다.
 */
@Entity
@Table(name = "org_dept_hist")
public class OrgDeptHist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hist_id", nullable = false, updatable = false)
    private Long histId;

    @Column(name = "dept_id", nullable = false, updatable = false)
    private Long deptId;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "dept_name", nullable = false, updatable = false)
    private String deptName;

    @Column(name = "parent_dept_id", updatable = false)
    private Long parentDeptId;

    @Column(name = "head_emp_id", updatable = false)
    private Long headEmpId;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, updatable = false)
    private DeptChangeType changeType;

    @Column(name = "change_reason", updatable = false)
    private String changeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OrgDeptHist() {
    }

    OrgDeptHist(long deptId, LocalDate validFrom, DeptAttrs attrs, DeptChangeType changeType, String changeReason,
            LocalDateTime now) {
        this.deptId = deptId;
        this.validFrom = validFrom;
        this.deptName = attrs.name();
        this.parentDeptId = attrs.parentDeptId();
        this.headEmpId = attrs.headEmpId();
        this.sortOrder = attrs.sortOrder();
        this.changeType = changeType;
        this.changeReason = changeReason;
        this.createdAt = now;
    }

    /** 이 행을 {@code date}(제외)까지로 닫는다. */
    void closeAt(LocalDate date) {
        this.validTo = date;
    }

    /** 반개구간 [validFrom, validTo)에 date 가 들어가면 true. */
    boolean isEffectiveOn(LocalDate date) {
        return !validFrom.isAfter(date) && (validTo == null || date.isBefore(validTo));
    }

    DeptAttrs attrs() {
        return new DeptAttrs(deptName, parentDeptId, headEmpId, sortOrder);
    }

    public Long getHistId() {
        return histId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getDeptName() {
        return deptName;
    }

    public Long getParentDeptId() {
        return parentDeptId;
    }

    public Long getHeadEmpId() {
        return headEmpId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public DeptChangeType getChangeType() {
        return changeType;
    }

    public String getChangeReason() {
        return changeReason;
    }

    /** 시점별 속성 묶음. */
    record DeptAttrs(String name, Long parentDeptId, Long headEmpId, int sortOrder) {
        boolean sameParent(DeptAttrs o) {
            return Objects.equals(parentDeptId, o.parentDeptId);
        }
    }
}
