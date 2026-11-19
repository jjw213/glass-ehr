package com.glassehr.overtime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class OvertimeDtos {

    private OvertimeDtos() {
    }

    public record CreateRequest(
            @NotNull(message = "근로 구분을 골라 주세요") OvertimeRequest.Kind workKind,
            @NotNull(message = "시작 시각을 적어 주세요") LocalDateTime startAt,
            @NotNull(message = "종료 시각을 적어 주세요") LocalDateTime endAt,
            @Min(value = 0, message = "휴게시간은 0분 이상이어야 해요")
            @Max(value = 1440, message = "휴게시간이 너무 길어요") Integer breakMinutes,
            @NotBlank(message = "사유를 적어 주세요") @Size(max = 500, message = "사유는 500자까지예요") String reason) {
    }

    public record RequestView(long requestId, long empId, OvertimeRequest.Kind workKind, LocalDate workDate,
            LocalDateTime startAt, LocalDateTime endAt, int breakMinutes, int plannedMinutes, String reason,
            OvertimeRequest.Status status, LocalDateTime requestedAt, LocalDateTime decidedAt, Long approvalId) {
    }

    public record AuthRequest(
            @NotNull(message = "인가 구분을 골라 주세요") SpecialOvertimeAuth.Kind authKind,
            @NotBlank(message = "인가 번호를 적어 주세요") @Size(max = 50, message = "인가 번호는 50자까지예요") String authNo,
            @NotBlank(message = "관할 고용노동관서를 적어 주세요") @Size(max = 100, message = "관서명은 100자까지예요")
            String laborOffice,
            @NotNull(message = "인가(승인)일을 적어 주세요") LocalDate authorizedOn,
            @NotNull(message = "시작일을 적어 주세요") LocalDate periodFrom,
            @NotNull(message = "종료일을 적어 주세요") LocalDate periodTo,
            @NotBlank(message = "사유를 적어 주세요") @Size(max = 500, message = "사유는 500자까지예요") String reason,
            @NotEmpty(message = "대상 사원을 한 명 이상 골라 주세요") Set<Long> empIds,
            @NotNull(message = "근로자 동의 여부를 확인해 주세요") Boolean workerConsent) {
    }

    public record AuthView(long authId, SpecialOvertimeAuth.Kind authKind, String authNo, String laborOffice,
            LocalDate authorizedOn, LocalDate periodFrom, LocalDate periodTo, String reason, List<Long> empIds,
            String createdBy, LocalDateTime createdAt) {
    }
}
