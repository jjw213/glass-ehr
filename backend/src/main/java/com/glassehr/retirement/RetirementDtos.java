package com.glassehr.retirement;

import com.glassehr.retirement.DismissalNoticeChecker.Exemption;
import com.glassehr.retirement.DismissalNoticeChecker.NoticeCheck;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 퇴직 처리·사용증명서 API 요청·응답. */
public final class RetirementDtos {

    private RetirementDtos() {
    }

    /**
     * 퇴직 처리 등록. 해고(DISMISSAL)면 예고일·서면통지일 필수(근로기준법 제26조·제27조).
     * 해고가 아니면 예고 항목을 보내지 않는다.
     */
    public record RetirementRequest(
            @NotNull(message = "사원을 선택해 주세요")
            Long empId,
            @NotNull(message = "퇴직일을 입력해 주세요")
            LocalDate retireDate,
            @NotBlank(message = "퇴직 유형을 선택해 주세요")
            String reasonCode,
            @Size(max = 1000, message = "퇴직 사유 상세는 1000자 이내로 써 주세요")
            String reasonDetail,
            LocalDate dismissalNoticeDate,
            LocalDate writtenNoticeDate,
            Exemption noticeExempt) {
    }

    public record ChecklistItemView(String itemCode, String label, String legalBasis, boolean done,
            LocalDateTime doneAt, String doneBy, String note) {

        static ChecklistItemView of(RetirementChecklistItem i) {
            return new ChecklistItemView(i.getItemCode(), i.getLabel(), i.getLegalBasis(), i.getDoneAt() != null,
                    i.getDoneAt(), i.getDoneBy(), i.getNote());
        }
    }

    /** 체크리스트 미리보기(등록 전 화면용). */
    public record ChecklistTemplateItem(String itemCode, String label, String legalBasis) {
    }

    public record NoticeView(NoticeCheck check, LocalDate dismissalNoticeDate, LocalDate writtenNoticeDate,
            Exemption noticeExempt, Integer noticeDays, Integer shortfallDays) {
    }

    /** {@code warnings}는 등록 응답에만 담긴다(예고기간 미달·기준값 없음 등). */
    public record RetirementView(long caseId, long empId, String empNo, String empName, String reasonCode,
            LocalDate retireDate, LocalDate recordRetentionUntil, NoticeView notice, List<ChecklistItemView> checklist,
            List<String> warnings, String createdBy, LocalDateTime createdAt) {
    }

    public record ChecklistUpdateRequest(
            @NotNull(message = "완료 여부를 보내 주세요")
            Boolean done,
            @Size(max = 500, message = "메모는 500자 이내로 써 주세요")
            String note) {
    }

    /** 보존기한이 지난 퇴직자(근로기준법 제42조). */
    public record RetentionExpiredRow(long empId, String empNo, String empName, LocalDate retireDate,
            String retireReasonCode, LocalDate recordRetentionUntil, long daysOverdue) {
    }

    /**
     * 사용증명서 기재 항목(근로기준법 제39조 제1항: 사용 기간, 업무 종류, 지위와 임금, 그 밖에 필요한 사항).
     * 퇴직 사유는 '그 밖에 필요한 사항'으로 근로자가 요구할 때만 적는다.
     */
    public enum CertificateItem { USE_PERIOD, JOB_TYPE, POSITION, WAGE, RETIRE_REASON }

    public record CertificateRequest(
            @NotNull(message = "청구일을 입력해 주세요")
            LocalDate requestedOn,
            @NotEmpty(message = "근로자가 요구한 기재 항목을 하나 이상 골라 주세요")
            Set<CertificateItem> items,
            @Size(max = 200, message = "용도는 200자 이내로 써 주세요")
            String purpose) {
    }

    /** 요구하지 않은 항목 값은 null. */
    public record CertificateView(long certId, long empId, String empNo, LocalDate requestedOn,
            List<CertificateItem> items, String purpose, String empName, LocalDate useFrom, LocalDate useTo,
            String jobType, String positionText, Long wageAmount, String wageUnitCode, String retireReason,
            String issuedBy, LocalDateTime issuedAt) {
    }
}
