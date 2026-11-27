package com.glassehr.approval;

import com.glassehr.code.YnConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 결재선 템플릿(신청 유형별, 유효기간 [validFrom, validTo)). 테이블: V024 approval_template·approval_template_step.
 * 단계는 만든 뒤 바꾸지 않는다. 설정을 바꿀 때는 종료일을 넣고({@link #closeAt}) 새 템플릿을 만든다.
 */
@Entity
@Table(name = "approval_template")
public class ApprovalTemplate {

    /** 결재 단계 설정. */
    @Embeddable
    public static class Step {
        /** 템플릿 법인(#127). 복합 FK (enter_cd, template_id) 로 다른 법인 템플릿에 붙지 않는다. */
        @Column(name = "enter_cd", nullable = false, updatable = false)
        private String enterCd;

        @Column(name = "step_no", nullable = false)
        private int stepNo;

        @Enumerated(EnumType.STRING)
        @Column(name = "approver_kind", nullable = false)
        private ApproverKind approverKind;

        @Column(name = "approver_ref")
        private String approverRef;

        @Convert(converter = YnConverter.class)
        // V024 DDL의 CHAR(1)과 Hibernate 스키마 검증의 JDBC 타입을 맞춘다.
        @JdbcTypeCode(SqlTypes.CHAR)
        @Column(name = "skip_if_self", nullable = false, length = 1)
        private boolean skipIfSelf;

        protected Step() {
        }

        public Step(int stepNo, ApproverKind approverKind, String approverRef, boolean skipIfSelf) {
            this.stepNo = stepNo;
            this.approverKind = approverKind;
            this.approverRef = approverRef;
            this.skipIfSelf = skipIfSelf;
        }

        private Step in(String enterCd) {
            Step s = new Step(stepNo, approverKind, approverRef, skipIfSelf);
            s.enterCd = enterCd;
            return s;
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

        public boolean isSkipIfSelf() {
            return skipIfSelf;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id", nullable = false, updatable = false)
    private Long templateId;

    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Column(name = "request_type", nullable = false, updatable = false)
    private String requestType;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "approval_template_step", joinColumns = @JoinColumn(name = "template_id"))
    @OrderBy("stepNo")
    private List<Step> steps = new ArrayList<>();

    protected ApprovalTemplate() {
    }

    public ApprovalTemplate(String enterCd, String requestType, LocalDate validFrom, List<Step> steps,
            String createdBy) {
        this.enterCd = enterCd;
        this.requestType = requestType;
        this.validFrom = validFrom;
        this.steps = new ArrayList<>(steps.stream().map(s -> s.in(enterCd)).toList());
        this.createdBy = createdBy;
    }

    /** 새 템플릿 시작일 전날까지만 쓰게 종료일(배타)을 넣는다. */
    void closeAt(LocalDate nextValidFrom) {
        this.validTo = nextValidFrom;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public String getEnterCd() {
        return enterCd;
    }

    public String getRequestType() {
        return requestType;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Step> getSteps() {
        return List.copyOf(steps);
    }
}
