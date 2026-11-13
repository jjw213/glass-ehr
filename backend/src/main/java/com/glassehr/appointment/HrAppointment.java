package com.glassehr.appointment;

import com.glassehr.code.YnConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * 인사발령 1건. 테이블: V014__hr_appointment.sql. 설계: docs/hr-appointment-design.md.
 *
 * <p>근로기준법 제23조 제1항(정당한 이유 없는 전직·휴직 등 금지): 사유를 반드시 적는다.
 * 확정 전에만 고치거나 지울 수 있다. 확정된 발령은 이력으로 남긴다(바뀐 내용은 새 발령으로 등록).
 */
@Entity
@Table(name = "hr_appointment")
public class HrAppointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appt_id", nullable = false, updatable = false)
    private Long apptId;

    @Column(name = "appt_no", nullable = false, updatable = false)
    private String apptNo;

    @Column(name = "appt_type_code", nullable = false)
    private String apptTypeCode;

    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "before_dept_id")
    private Long beforeDeptId;

    @Column(name = "before_job_grade_code")
    private String beforeJobGradeCode;

    @Column(name = "before_job_title_code")
    private String beforeJobTitleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_emp_status")
    private EmpStatus beforeEmpStatus;

    @Column(name = "after_dept_id")
    private Long afterDeptId;

    @Column(name = "after_job_grade_code")
    private String afterJobGradeCode;

    @Column(name = "after_job_title_code")
    private String afterJobTitleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_emp_status", nullable = false)
    private EmpStatus afterEmpStatus;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "leave_start_date")
    private LocalDate leaveStartDate;

    @Column(name = "leave_end_date")
    private LocalDate leaveEndDate;

    @Convert(converter = YnConverter.class)
    @Column(name = "confirmed_yn", nullable = false)
    private boolean confirmed;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected HrAppointment() {
    }

    /** 발령 시점의 소속·직급·직책·재직상태. 입사 발령의 이전 값은 모두 null. */
    public record Snapshot(Long deptId, String jobGradeCode, String jobTitleCode, EmpStatus status) {

        static final Snapshot NONE = new Snapshot(null, null, null, null);
    }

    /** 등록·수정 API가 정하는 내용(이전 값은 서비스가 사원 현재 값으로 채운다). */
    public record Content(AppointmentType type, LocalDate effectiveDate, Snapshot before, Snapshot after,
            String reason, LocalDate leaveStartDate, LocalDate leaveEndDate) {
    }

    HrAppointment(String apptNo, long empId, Content c, LocalDateTime now) {
        this.apptNo = apptNo;
        this.empId = empId;
        this.confirmed = false;
        this.createdAt = now;
        apply(c, now);
    }

    void update(Content c, LocalDateTime now) {
        requireUnconfirmed();
        apply(c, now);
    }

    /** 확정. 확정할 때 이전·이후 값을 다시 채운 내용을 함께 받는다. */
    void confirm(Content c, LocalDateTime now) {
        requireUnconfirmed();
        apply(c, now);
        this.confirmed = true;
        this.confirmedAt = now;
    }

    private void requireUnconfirmed() {
        if (confirmed) {
            throw new IllegalStateException("확정된 발령은 바꿀 수 없어요: " + apptNo);
        }
    }

    private void apply(Content c, LocalDateTime now) {
        this.apptTypeCode = c.type().code();
        this.effectiveDate = c.effectiveDate();
        this.beforeDeptId = c.before().deptId();
        this.beforeJobGradeCode = c.before().jobGradeCode();
        this.beforeJobTitleCode = c.before().jobTitleCode();
        this.beforeEmpStatus = c.before().status();
        this.afterDeptId = c.after().deptId();
        this.afterJobGradeCode = c.after().jobGradeCode();
        this.afterJobTitleCode = c.after().jobTitleCode();
        this.afterEmpStatus = c.after().status();
        this.reason = c.reason();
        this.leaveStartDate = c.leaveStartDate();
        this.leaveEndDate = c.leaveEndDate();
        this.updatedAt = now;
    }

    public Snapshot before() {
        return new Snapshot(beforeDeptId, beforeJobGradeCode, beforeJobTitleCode, beforeEmpStatus);
    }

    public Snapshot after() {
        return new Snapshot(afterDeptId, afterJobGradeCode, afterJobTitleCode, afterEmpStatus);
    }

    public Long getApptId() {
        return apptId;
    }

    public String getApptNo() {
        return apptNo;
    }

    public String getApptTypeCode() {
        return apptTypeCode;
    }

    public Long getEmpId() {
        return empId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public EmpStatus getAfterEmpStatus() {
        return afterEmpStatus;
    }

    public String getReason() {
        return reason;
    }

    public LocalDate getLeaveStartDate() {
        return leaveStartDate;
    }

    public LocalDate getLeaveEndDate() {
        return leaveEndDate;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
