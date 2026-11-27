package com.glassehr.approval;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
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
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** 결재 문서 단계(신청 시점 스냅샷). 테이블: V024 approval_doc_step·approval_doc_step_approver. */
@Entity
@Table(name = "approval_doc_step")
public class ApprovalDocStep {

    /** 단계 상태. V024 ck_approval_dstep_status 와 같다. */
    public enum Status { WAITING, APPROVED, REJECTED, SKIPPED }

    /**
     * 단계 결재자 후보(approval_doc_step_approver 한 행). 법인을 함께 저장해 복합 FK (enter_cd, emp_id)·(enter_cd, step_id)로
     * 다른 법인 사원을 결재선에 넣을 수 없게 한다(#127, V036·V046).
     */
    @Embeddable
    public static class Approver {
        @Column(name = "enter_cd", nullable = false, updatable = false)
        private String enterCd;

        @Column(name = "emp_id", nullable = false, updatable = false)
        private long empId;

        protected Approver() {
        }

        Approver(String enterCd, long empId) {
            this.enterCd = enterCd;
            this.empId = empId;
        }

        public long getEmpId() {
            return empId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Approver a && empId == a.empId && Objects.equals(enterCd, a.enterCd);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enterCd, empId);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "step_id", nullable = false, updatable = false)
    private Long stepId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false, updatable = false)
    private ApprovalDoc doc;

    /** 문서 법인과 같다. 복합 FK (enter_cd, approval_id) 로 다른 법인 문서에 붙지 않는다(#127). */
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

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
    private Set<Approver> approvers = new LinkedHashSet<>();

    protected ApprovalDocStep() {
    }

    ApprovalDocStep(ApprovalDoc doc, ApproverResolver.ResolvedStep r) {
        this.doc = doc;
        this.enterCd = doc.getEnterCd();
        this.stepNo = r.stepNo();
        this.approverKind = r.kind();
        this.approverRef = r.ref();
        for (Long e : new TreeSet<>(r.approverEmpIds())) {
            approvers.add(new Approver(enterCd, e));
        }
        this.stepStatus = r.skipped() ? Status.SKIPPED : Status.WAITING;
    }

    boolean canAct(long empId) {
        return stepStatus == Status.WAITING && getApproverEmpIds().contains(empId);
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
        return approvers.stream().map(Approver::getEmpId).collect(Collectors.toCollection(TreeSet::new));
    }
}
