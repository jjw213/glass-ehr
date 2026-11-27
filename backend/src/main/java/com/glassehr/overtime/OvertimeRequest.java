package com.glassehr.overtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 연장·휴일근로 사전 신청(#30). 테이블: V025 overtime_request.
 * 상태는 공통 결재 결과를 따른다. 상태 변경은 {@link OvertimeApprovalHandler}만 한다.
 */
@Entity
@Table(name = "overtime_request")
public class OvertimeRequest {

    /** V025 ck_overtime_request_kind 와 같다. */
    public enum Kind { OVERTIME, HOLIDAY }

    /** V025 ck_overtime_request_status 와 같다. */
    public enum Status { IN_PROGRESS, APPROVED, REJECTED, WITHDRAWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    /** 신청자 법인(#127). 복합 FK (enter_cd, emp_id) 로 다른 법인 사원 신청을 만들 수 없다. */
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_kind", nullable = false, updatable = false)
    private Kind workKind;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "start_at", nullable = false, updatable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private LocalDateTime endAt;

    @Column(name = "break_minutes", nullable = false, updatable = false)
    private Integer breakMinutes;

    @Column(name = "planned_minutes", nullable = false, updatable = false)
    private Integer plannedMinutes;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    protected OvertimeRequest() {
    }

    OvertimeRequest(String enterCd, long empId, Kind workKind, LocalDateTime startAt, LocalDateTime endAt,
            int breakMinutes, String reason, LocalDateTime requestedAt) {
        this.enterCd = enterCd;
        this.empId = empId;
        this.workKind = workKind;
        this.workDate = startAt.toLocalDate();
        this.startAt = startAt;
        this.endAt = endAt;
        this.breakMinutes = breakMinutes;
        this.plannedMinutes = plannedMinutes(startAt, endAt, breakMinutes);
        this.reason = reason;
        this.status = Status.IN_PROGRESS;
        this.requestedAt = requestedAt;
    }

    /** 예정 근로시간(분) = 종료 − 시작 − 휴게. V025 ck_overtime_request_minutes 와 같은 식. */
    static int plannedMinutes(LocalDateTime startAt, LocalDateTime endAt, int breakMinutes) {
        return Math.toIntExact(Duration.between(startAt, endAt).toMinutes()) - breakMinutes;
    }

    void decide(Status result, LocalDateTime now) {
        if (status != Status.IN_PROGRESS) {
            throw new IllegalStateException("이미 처리된 신청이에요: " + requestId);
        }
        if (result == Status.IN_PROGRESS) {
            throw new IllegalArgumentException("결과 상태가 필요해요");
        }
        this.status = result;
        this.decidedAt = now;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getEmpId() {
        return empId;
    }

    public Kind getWorkKind() {
        return workKind;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public Integer getBreakMinutes() {
        return breakMinutes;
    }

    public Integer getPlannedMinutes() {
        return plannedMinutes;
    }

    public String getEnterCd() {
        return enterCd;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }
}
