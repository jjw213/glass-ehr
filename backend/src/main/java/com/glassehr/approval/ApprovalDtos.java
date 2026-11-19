package com.glassehr.approval;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 결재 API 요청·응답. */
public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    public record RequestTypeView(String requestType, String label) {
    }

    public record TemplateStepRequest(@NotNull(message = "결재자 지정 방식을 골라 주세요") ApproverKind approverKind,
            String approverRef, boolean skipIfSelf) {
    }

    public record TemplateRequest(@NotNull(message = "신청 유형을 골라 주세요") String requestType,
            @NotNull(message = "적용 시작일을 넣어 주세요") LocalDate validFrom,
            @NotEmpty(message = "결재 단계를 하나 이상 넣어 주세요")
            @Size(max = 10, message = "결재 단계는 10개까지예요") List<@Valid TemplateStepRequest> steps) {
    }

    public record TemplateStepView(int stepNo, ApproverKind approverKind, String approverRef, boolean skipIfSelf) {
        static TemplateStepView of(ApprovalTemplate.Step s) {
            return new TemplateStepView(s.getStepNo(), s.getApproverKind(), s.getApproverRef(), s.isSkipIfSelf());
        }
    }

    public record TemplateView(long templateId, String requestType, LocalDate validFrom, LocalDate validTo,
            String createdBy, List<TemplateStepView> steps) {
        static TemplateView of(ApprovalTemplate t) {
            return new TemplateView(t.getTemplateId(), t.getRequestType(), t.getValidFrom(), t.getValidTo(),
                    t.getCreatedBy(), t.getSteps().stream().map(TemplateStepView::of).toList());
        }
    }

    public record ActionRequest(@Size(max = 500, message = "의견은 500자까지예요") String comment) {
    }

    public record PersonView(long empId, String name) {
    }

    public record StepView(int stepNo, ApproverKind approverKind, String approverRef, ApprovalDocStep.Status status,
            List<PersonView> approvers, PersonView actedBy, LocalDateTime actedAt, String comment) {
    }

    public record LogView(long actionId, Integer stepNo, ApprovalActionLog.Action action, PersonView actor,
            LocalDateTime actedAt, String comment) {
    }

    /** 목록 한 줄. */
    public record DocSummary(long approvalId, String requestType, String requestTypeLabel, long requestRef,
            String title, PersonView requester, ApprovalDoc.Status status, Integer currentStepNo, int stepCount,
            LocalDateTime submittedAt, LocalDateTime completedAt) {
    }

    /** 상세. 내가 지금 처리·회수할 수 있는지도 알려 준다. */
    public record DocDetail(DocSummary summary, Object requestDetail, List<StepView> steps, List<LogView> history,
            boolean canAct, boolean canWithdraw) {
    }
}
