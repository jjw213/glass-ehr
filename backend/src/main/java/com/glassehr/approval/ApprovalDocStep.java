package com.glassehr.approval;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;

/** 결재 문서 단계(신청 시점 스냅샷). 테이블: V024 approval_doc_step·approval_doc_step_approver. */
@Entity
@Table(name = "approval_doc_step")
public class ApprovalDocStep {

    /** 단계 상태. V024 ck_approval_dstep_status 와 같다. */
    public enum Status { WAITING, APPROVED, REJECTED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "step_id", nullable = false, updatable = false)
    private Long stepId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false, updatable = false)
    private ApprovalDoc doc;

    @Column(name = "step_no", nullable = false, updatable = false)
    private int stepNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_kind", nullable = false, updatable = false)
    private ApproverKind approverKind;

    @Column(name = "approver_ref", updatable = false)
    private String approverRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_status", nullable = false)
    private Status stepStatus;

    @Column(name = "acted_emp_id")
    private Long actedEmpId;

    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    @Column(name = "step_comment")
    private String stepComment;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "approval_doc_step_approver", joinColumns = @JoinColumn(name = "step_id"))
    @Column(name = "emp_id", nullable = false)
    private Set<Long> approverEmpIds = new TreeSet<>();

    protected ApprovalDocStep() {
    }

    ApprovalDocStep(ApprovalDoc doc, ApproverResolver.ResolvedStep r) {
        this.doc = doc;
        this.stepNo = r.stepNo();
        this.approverKind = r.kind();
        this.approverRef = r.ref();
        this.approverEmpIds = new TreeSet<>(r.approverEmpIds());
        this.stepStatus = r.skipped() ? Status.SKIPPED : Status.WAITING;
    }

    boolean canAct(long empId) {
        return stepStatus == Status.WAITING && approverEmpIds.contains(empId);
    }

    void act(Status result, long empId, String comment, LocalDateTime now) {
        this.stepStatus = result;
        this.actedEmpId = empId;
        this.actedAt = now;
        this.stepComment = comment;
    }

    public Long getStepId() {
        return stepId;
    }

    public int getStepNo() {
        return stepNo;
    }

    public ApproverKind getApproverKind() {
        return approverKind;
    }

    public String getApproverRef() {
        return approverRef;
    }

    public Status getStepStatus() {
        return stepStatus;
    }

    public Long getActedEmpId() {
        return actedEmpId;
    }

    public LocalDateTime getActedAt() {
        return actedAt;
    }

    public String getStepComment() {
        return stepComment;
    }

    public Set<Long> getApproverEmpIds() {
        return Set.copyOf(approverEmpIds);
    }
}
