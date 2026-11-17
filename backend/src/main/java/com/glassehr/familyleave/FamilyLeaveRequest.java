package com.glassehr.familyleave;

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
 * 육아휴직·육아기 근로시간 단축·가족돌봄휴직 신청 1건(#37). 테이블: V023 family_leave_request.
 * 신청 내용은 고치지 않고 상태만 REQUESTED → APPROVED / REJECTED / CANCELLED 로 바꾼다.
 */
@Entity
@Table(name = "family_leave_request")
public class FamilyLeaveRequest {

    /** 신청 종류와 근거 조문(남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률). */
    public enum Kind {
        /** 제19조 육아휴직 — 휴직(LEAVE) 발령. */
        PARENTAL_LEAVE,
        /** 제19조의2 육아기 근로시간 단축 — 단축 근무유형 배정. */
        CHILDCARE_REDUCED_HOURS,
        /** 제22조의2 가족돌봄휴직 — 휴직(LEAVE) 발령. */
        FAMILY_CARE_LEAVE;

        boolean isLeaveOfAbsence() {
            return this != CHILDCARE_REDUCED_HOURS;
        }

        String label() {
            return switch (this) {
                case PARENTAL_LEAVE -> "육아휴직";
                case CHILDCARE_REDUCED_HOURS -> "육아기 근로시간 단축";
                case FAMILY_CARE_LEAVE -> "가족돌봄휴직";
            };
        }
    }

    public enum Status { REQUESTED, APPROVED, REJECTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "req_id", nullable = false, updatable = false)
    private Long reqId;

    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_kind", nullable = false, updatable = false)
    private Kind kind;

    @Column(name = "child_label", updatable = false)
    private String childLabel;

    @Column(name = "child_birth_date", updatable = false)
    private LocalDate childBirthDate;

    @Column(name = "family_relation", updatable = false)
    private String familyRelation;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(name = "work_pattern_id", updatable = false)
    private Long workPatternId;

    @Convert(converter = YnConverter.class)
    @Column(name = "extension_yn", nullable = false, updatable = false, length = 1)
    private Boolean extension;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "warnings", updatable = false)
    private String warnings;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "leave_appt_id")
    private Long leaveApptId;

    @Column(name = "return_appt_id")
    private Long returnApptId;

    @Column(name = "reduced_assign_id")
    private Long reducedAssignId;

    @Column(name = "restore_assign_id")
    private Long restoreAssignId;

    protected FamilyLeaveRequest() {
    }

    FamilyLeaveRequest(long empId, Kind kind, String childLabel, LocalDate childBirthDate, String familyRelation,
            LocalDate startDate, LocalDate endDate, Long workPatternId, boolean extension, String reason,
            String warnings, String requestedBy, LocalDateTime requestedAt) {
        this.empId = empId;
        this.kind = kind;
        this.childLabel = childLabel;
        this.childBirthDate = childBirthDate;
        this.familyRelation = familyRelation;
        this.startDate = startDate;
        this.endDate = endDate;
        this.workPatternId = workPatternId;
        this.extension = extension;
        this.reason = reason;
        this.warnings = warnings;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.status = Status.REQUESTED;
    }

    void approveLeave(long leaveApptId, long returnApptId, String actor, LocalDateTime now) {
        this.leaveApptId = leaveApptId;
        this.returnApptId = returnApptId;
        decide(Status.APPROVED, actor, now, null);
    }

    void approveReduced(long reducedAssignId, long restoreAssignId, String actor, LocalDateTime now) {
        this.reducedAssignId = reducedAssignId;
        this.restoreAssignId = restoreAssignId;
        decide(Status.APPROVED, actor, now, null);
    }

    void decide(Status status, String actor, LocalDateTime now, String note) {
        this.status = status;
        this.decidedBy = actor;
        this.decidedAt = now;
        this.decisionNote = note;
    }

    /** 테스트용: 저장된 것처럼 ID를 채운다. */
    FamilyLeaveRequest withId(long id) {
        this.reqId = id;
        return this;
    }

    public Long getReqId() {
        return reqId;
    }

    public Long getEmpId() {
        return empId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getChildLabel() {
        return childLabel;
    }

    public LocalDate getChildBirthDate() {
        return childBirthDate;
    }

    public String getFamilyRelation() {
        return familyRelation;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Long getWorkPatternId() {
        return workPatternId;
    }

    public boolean isExtension() {
        return Boolean.TRUE.equals(extension);
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public String getWarnings() {
        return warnings;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Long getLeaveApptId() {
        return leaveApptId;
    }

    public Long getReturnApptId() {
        return returnApptId;
    }

    public Long getReducedAssignId() {
        return reducedAssignId;
    }

    public Long getRestoreAssignId() {
        return restoreAssignId;
    }
}
