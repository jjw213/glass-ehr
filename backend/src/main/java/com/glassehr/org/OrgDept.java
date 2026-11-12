package com.glassehr.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 부서 정체성. 테이블: V008__org_dept.sql.
 * {@code dept_id}는 바뀌지 않는다. 이름·상위부서 등 시점별 속성은 {@link OrgDeptHist}에 있다.
 * 기간은 [validFrom, validTo), validTo == null 이면 존속 중.
 */
@Entity
@Table(name = "org_dept")
public class OrgDept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dept_id", nullable = false, updatable = false)
    private Long deptId;

    @Column(name = "dept_code", nullable = false, updatable = false)
    private String deptCode;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected OrgDept() {
    }

    OrgDept(String deptCode, LocalDate validFrom, LocalDateTime now) {
        this.deptCode = deptCode;
        this.validFrom = validFrom;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 테스트용: id 를 지정해 만든다. */
    static OrgDept withId(long deptId, String deptCode, LocalDate validFrom, LocalDate validTo) {
        OrgDept d = new OrgDept(deptCode, validFrom, LocalDateTime.of(2026, 1, 1, 0, 0));
        d.deptId = deptId;
        d.validTo = validTo;
        return d;
    }

    void close(LocalDate closeDate, String reason, LocalDateTime now) {
        this.validTo = closeDate;
        this.closeReason = reason;
        this.updatedAt = now;
    }

    /** 폐지일이 정해지지 않았으면 true. 폐지일이 미래로 잡혀 있어도 false. */
    boolean isOpenEnded() {
        return validTo == null;
    }

    public Long getDeptId() {
        return deptId;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getCloseReason() {
        return closeReason;
    }
}
