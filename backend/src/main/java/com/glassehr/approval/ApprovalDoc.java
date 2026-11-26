package com.glassehr.approval;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 결재 문서(#30). 테이블: V024 approval_doc. 상태 흐름은 docs/approval-line-design.md §4.
 * <ul>
 *   <li>순차 결재: 현재 단계(currentStepNo)의 결재자 후보 중 한 명만 처리할 수 있다.</li>
 *   <li>반려: 한 단계라도 반려하면 문서 전체가 반려된다.</li>
 *   <li>회수: 신청자가, 아무도 승인하기 전에만.</li>
 * </ul>
 * 상태 변경은 이 클래스 메서드로만 한다. 처리 이력 저장은 {@link ApprovalService}가 한다.
 */
@Entity
@Table(name = "approval_doc")
public class ApprovalDoc {

    /** 문서 상태. V024 ck_approval_doc_status 와 같다. */
    public enum Status { IN_PROGRESS, APPROVED, REJECTED, WITHDRAWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id", nullable = false, updatable = false)
    private Long approvalId;

    /** 문서 법인(신청자 법인, #127). 결재선·이력·핸들러 처리는 이 법인 안에서만 한다. */
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Column(name = "request_type", nullable = false, updatable = false)
    private String requestType;

    @Column(name = "request_ref", nullable = false, updatable = false)
    private Long requestRef;

    @Column(name = "title", nullable = false, updatable = false)
    private String title;

    @Column(name = "requester_emp_id", nullable = false, updatable = false)
    private Long requesterEmpId;

    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "current_step_no")
    private Integer currentStepNo;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "doc", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("stepNo")
    private List<ApprovalDocStep> steps = new ArrayList<>();

    protected ApprovalDoc() {
    }

    /** 신청. 건너뛴 단계 다음의 첫 대기 단계부터 시작하고, 모두 건너뛰었으면 바로 승인된다. */
    ApprovalDoc(String enterCd, String requestType, long requestRef, String title, long requesterEmpId,
            long templateId, List<ApproverResolver.ResolvedStep> resolved, LocalDateTime now) {
        this.enterCd = enterCd;
        this.requestType = requestType;
        this.requestRef = requestRef;
        this.title = title;
        this.requesterEmpId = requesterEmpId;
        this.templateId = templateId;
        this.submittedAt = now;
        this.status = Status.IN_PROGRESS;
        for (ApproverResolver.ResolvedStep r : resolved) {
            steps.add(new ApprovalDocStep(this, r));
        }
        moveToNextWaiting(now);
    }

    /** 승인. 마지막 단계였으면 true(문서 승인). */
    boolean approve(long empId, String comment, LocalDateTime now) {
        ApprovalDocStep step = requireActionable(empId);
        step.act(ApprovalDocStep.Status.APPROVED, empId, comment, now);
        moveToNextWaiting(now);
        return status == Status.APPROVED;
    }

    void reject(long empId, String comment, LocalDateTime now) {
        ApprovalDocStep step = requireActionable(empId);
        step.act(ApprovalDocStep.Status.REJECTED, empId, comment, now);
        finish(Status.REJECTED, now);
    }

    void withdraw(long empId, LocalDateTime now) {
        if (requesterEmpId != empId) {
            throw ApprovalException.forbidden("본인이 올린 결재만 회수할 수 있어요");
        }
        requireInProgress();
        if (steps.stream().anyMatch(s -> s.getStepStatus() == ApprovalDocStep.Status.APPROVED)) {
            throw ApprovalException.conflict("이미 승인한 결재자가 있어 회수할 수 없어요");
        }
        finish(Status.WITHDRAWN, now);
    }

    /** 지금 결재 차례인 단계(진행 중일 때만). */
    public Optional<ApprovalDocStep> currentStep() {
        if (currentStepNo == null) {
            return Optional.empty();
        }
        return steps.stream().filter(s -> s.getStepNo() == currentStepNo).findFirst();
    }

    public boolean isCurrentApprover(long empId) {
        return currentStep().map(s -> s.canAct(empId)).orElse(false);
    }

    /** 신청자이거나 어느 단계든 결재자 후보이면 볼 수 있다. */
    public boolean involves(long empId) {
        return requesterEmpId == empId || steps.stream().anyMatch(s -> s.getApproverEmpIds().contains(empId));
    }

    private ApprovalDocStep requireActionable(long empId) {
        requireInProgress();
        ApprovalDocStep step = currentStep().orElseThrow();
        if (!step.canAct(empId)) {
            throw ApprovalException.forbidden("지금 결재 차례가 아니에요(" + currentStepNo + "단계 결재자만 처리할 수 있어요)");
        }
        return step;
    }

    private void requireInProgress() {
        if (status != Status.IN_PROGRESS) {
            throw ApprovalException.conflict("이미 끝난 결재예요(" + status + ")");
        }
    }

    private void moveToNextWaiting(LocalDateTime now) {
        Optional<ApprovalDocStep> next = steps.stream()
                .filter(s -> s.getStepStatus() == ApprovalDocStep.Status.WAITING)
                .findFirst();
        if (next.isPresent()) {
            currentStepNo = next.get().getStepNo();
        } else {
            finish(Status.APPROVED, now);
        }
    }

    private void finish(Status result, LocalDateTime now) {
        this.status = result;
        this.currentStepNo = null;
        this.completedAt = now;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public String getEnterCd() {
        return enterCd;
    }

    public String getRequestType() {
        return requestType;
    }

    public Long getRequestRef() {
        return requestRef;
    }

    public String getTitle() {
        return title;
    }

    public Long getRequesterEmpId() {
        return requesterEmpId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public Status getStatus() {
        return status;
    }

    public Integer getCurrentStepNo() {
        return currentStepNo;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public List<ApprovalDocStep> getSteps() {
        return List.copyOf(steps);
    }
}
