package com.glassehr.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 서면합의서 API 요청·응답(#27). */
public final class LaborAgreementDtos {

    private LaborAgreementDtos() {
    }

    /** 등록·수정 요청. 합의 유형은 등록 후 바꿀 수 없다(수정 요청의 유형이 다르면 400). */
    public record AgreementRequest(
            @NotNull(message = "합의 유형을 골라 주세요") AgreementType agreementType,
            @NotNull(message = "합의서 제목을 입력해 주세요") @Size(max = 200, message = "제목은 200자 이내로 써 주세요") String title,
            @NotNull(message = "체결일을 입력해 주세요") @JsonFormat(pattern = "yyyy-MM-dd") LocalDate signedDate,
            @NotNull(message = "유효기간 시작일을 입력해 주세요") @JsonFormat(pattern = "yyyy-MM-dd") LocalDate validFrom,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate validUntil,
            @NotNull(message = "대상 범위를 골라 주세요") LaborAgreement.Scope scopeType,
            String scopeSiteCode,
            @NotNull(message = "대상 근로자 범위를 입력해 주세요") @Size(max = 1000, message = "대상 근로자 범위는 1000자 이내로 써 주세요") String targetDesc,
            @NotNull(message = "근로자대표 구분을 골라 주세요") LaborAgreement.RepType repType,
            @Size(max = 200, message = "노동조합 이름은 200자 이내로 써 주세요") String repUnionName,
            Long repEmpId,
            @Size(max = 1000, message = "비고는 1000자 이내로 써 주세요") String note) {

        /** 공백 정리 후 저장 값. 형식 오류는 {@link LaborAgreementRules#validate}가 잡는다. */
        public Fields fields() {
            return new Fields(trim(title), signedDate, validFrom, validUntil, scopeType,
                    scopeType == LaborAgreement.Scope.SITE ? trim(scopeSiteCode) : null, trim(targetDesc), repType,
                    repType == LaborAgreement.RepType.MAJORITY_UNION ? trim(repUnionName) : null,
                    repType == LaborAgreement.RepType.MAJORITY_PERSON ? repEmpId : null, trim(note));
        }

        private static String trim(String s) {
            return s == null || s.isBlank() ? null : s.trim();
        }
    }

    /** 정리된 저장 값. */
    public record Fields(String title, LocalDate signedDate, LocalDate validFrom, LocalDate validUntil,
            LaborAgreement.Scope scopeType, String scopeSiteCode, String targetDesc, LaborAgreement.RepType repType,
            String repUnionName, Long repEmpId, String note) {
    }

    /** 합의서 상태(기준일 대비). */
    public enum Status { UPCOMING, ACTIVE, EXPIRED }

    public record AgreementView(long agreementId, AgreementType agreementType, String typeLabel, String article,
            String title, LocalDate signedDate, LocalDate validFrom, LocalDate validUntil,
            LaborAgreement.Scope scopeType, String scopeSiteCode, String targetDesc,
            LaborAgreement.RepType repType, String repUnionName, Long repEmpId, String repEmpName, String repEmpNo,
            String note, Status status, Long daysUntilExpiry, long fileCount, String updatedBy, LocalDateTime updatedAt) {

        static AgreementView of(LaborAgreement a, String repEmpName, String repEmpNo, long fileCount, LocalDate today) {
            Status status = a.getValidFrom().isAfter(today) ? Status.UPCOMING
                    : a.getValidUntil() != null && a.getValidUntil().isBefore(today) ? Status.EXPIRED : Status.ACTIVE;
            Long days = a.getValidUntil() == null ? null : ChronoUnit.DAYS.between(today, a.getValidUntil());
            return new AgreementView(a.getAgreementId(), a.getAgreementType(), a.getAgreementType().label(),
                    a.getAgreementType().article(), a.getTitle(), a.getSignedDate(), a.getValidFrom(), a.getValidUntil(),
                    a.getScopeType(), a.getScopeSiteCode(), a.getTargetDesc(), a.getRepType(), a.getRepUnionName(),
                    a.getRepEmpId(), repEmpName, repEmpNo, a.getNote(), status, days, fileCount, a.getUpdatedBy(),
                    a.getUpdatedAt());
        }
    }

    public record AgreementDetail(AgreementView agreement, List<AgreementFileView> files) {
    }

    /** 유효 합의서 확인 결과. ok=false 이면 message 에 이유. */
    public record CheckResult(boolean ok, AgreementType agreementType, Long agreementId, String title,
            LocalDate validFrom, LocalDate validUntil, String message) {
    }

    /** 합의 유형 목록(화면 선택지). */
    public record TypeView(AgreementType code, String label, String article) {
    }

    /** 만료 예정 알림. days = 오늘부터 며칠 안에 만료되는 합의서를 모았는지. */
    public record ExpiringView(int days, List<AgreementView> agreements) {
    }
}
