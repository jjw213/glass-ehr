package com.glassehr.retirement;

import com.glassehr.retirement.DismissalNoticeChecker.Exemption;
import com.glassehr.retirement.DismissalNoticeChecker.NoticeCheck;
import jakarta.persistence.Column;
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
 * 퇴직 처리 1건(사원 1명). 테이블: V022 retirement_case. 등록 당시 값을 그대로 남기고 고치지 않는다
 * (정정은 후속 이슈). 해고면 예고일(근로기준법 제26조)·서면통지일(제27조)이 반드시 있다.
 */
@Entity
@Table(name = "retirement_case")
public class RetirementCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id", nullable = false, updatable = false)
    private Long caseId;

    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Column(name = "retire_reason_code", nullable = false, updatable = false)
    private String retireReasonCode;

    @Column(name = "retire_date", nullable = false, updatable = false)
    private LocalDate retireDate;

    @Column(name = "dismissal_notice_date", updatable = false)
    private LocalDate dismissalNoticeDate;

    @Column(name = "written_notice_date", updatable = false)
    private LocalDate writtenNoticeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_exempt_code", updatable = false)
    private Exemption noticeExempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_check", nullable = false, updatable = false)
    private NoticeCheck noticeCheck;

    @Column(name = "notice_days", updatable = false)
    private Integer noticeDays;

    @Column(name = "notice_shortfall_days", updatable = false)
    private Integer noticeShortfallDays;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RetirementCase() {
    }

    RetirementCase(long empId, String retireReasonCode, LocalDate retireDate, LocalDate dismissalNoticeDate,
            LocalDate writtenNoticeDate, Exemption noticeExempt, NoticeCheck noticeCheck, Integer noticeDays,
            Integer noticeShortfallDays, String createdBy, LocalDateTime createdAt) {
        this.empId = empId;
        this.retireReasonCode = retireReasonCode;
        this.retireDate = retireDate;
        this.dismissalNoticeDate = dismissalNoticeDate;
        this.writtenNoticeDate = writtenNoticeDate;
        this.noticeExempt = noticeExempt;
        this.noticeCheck = noticeCheck;
        this.noticeDays = noticeDays;
        this.noticeShortfallDays = noticeShortfallDays;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getCaseId() {
        return caseId;
    }

    public Long getEmpId() {
        return empId;
    }

    public String getRetireReasonCode() {
        return retireReasonCode;
    }

    public LocalDate getRetireDate() {
        return retireDate;
    }

    public LocalDate getDismissalNoticeDate() {
        return dismissalNoticeDate;
    }

    public LocalDate getWrittenNoticeDate() {
        return writtenNoticeDate;
    }

    public Exemption getNoticeExempt() {
        return noticeExempt;
    }

    public NoticeCheck getNoticeCheck() {
        return noticeCheck;
    }

    public Integer getNoticeDays() {
        return noticeDays;
    }

    public Integer getNoticeShortfallDays() {
        return noticeShortfallDays;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
