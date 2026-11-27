package com.glassehr.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/** 결재 처리 이력(추가만). 테이블: V024 approval_action_log. */
@Entity
@Immutable
@Table(name = "approval_action_log")
public class ApprovalActionLog {

    /** V024 ck_approval_log_action 과 같다. */
    public enum Action { SUBMIT, SKIP, APPROVE, REJECT, WITHDRAW }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_id", nullable = false, updatable = false)
    private Long actionId;

    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Column(name = "approval_id", nullable = false, updatable = false)
    private Long approvalId;

    @Column(name = "step_no", updatable = false)
    private Integer stepNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private Action action;

    @Column(name = "actor_emp_id", nullable = false, updatable = false)
    private Long actorEmpId;

    @Column(name = "acted_at", nullable = false, updatable = false)
    private LocalDateTime actedAt;

    @Column(name = "action_comment", updatable = false)
    private String actionComment;

    protected ApprovalActionLog() {
    }

    ApprovalActionLog(String enterCd, long approvalId, Integer stepNo, Action action, long actorEmpId,
            LocalDateTime actedAt, String comment) {
        this.enterCd = enterCd;
        this.approvalId = approvalId;
        this.stepNo = stepNo;
        this.action = action;
        this.actorEmpId = actorEmpId;
        this.actedAt = actedAt;
        this.actionComment = comment;
    }

    public Long getActionId() {
        return actionId;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public Integer getStepNo() {
        return stepNo;
    }

    public Action getAction() {
        return action;
    }

    public Long getActorEmpId() {
        return actorEmpId;
    }

    public LocalDateTime getActedAt() {
        return actedAt;
    }

    public String getActionComment() {
        return actionComment;
    }
}
