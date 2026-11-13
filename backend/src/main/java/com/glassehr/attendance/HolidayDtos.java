package com.glassehr.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 휴일 달력 API 요청·응답(#26). */
public final class HolidayDtos {

    private HolidayDtos() {
    }

    /** 회사휴일 등록. paid 를 비우면 유급. */
    public record CompanyHolidayRequest(
            @NotNull(message = "날짜를 입력해 주세요") LocalDate date,
            @NotBlank(message = "휴일 이름을 입력해 주세요") @Size(max = 100, message = "휴일 이름은 100자 이내로 써 주세요") String name,
            Boolean paid,
            @Size(max = 500, message = "비고는 500자 이내로 써 주세요") String note) {
    }

    /** 휴일 대체 등록(근로기준법 제55조 제2항 단서). 서면합의 문서는 필수. */
    public record SubstitutionRequest(
            @NotNull(message = "대체할 공휴일을 골라 주세요") Long originalHolidayId,
            @NotNull(message = "대신 쉴 근로일을 입력해 주세요") LocalDate substituteDate,
            @NotNull(message = "근로자대표 서면합의 문서를 연결해 주세요(근로기준법 제55조 제2항 단서)") Long agreementId,
            @Size(max = 500, message = "비고는 500자 이내로 써 주세요") String note) {
    }

    /** 근로자대표 서면합의 등록. 합의서 문서 참조(document_ref)는 필수. */
    public record AgreementRequest(
            @NotBlank(message = "합의서 제목을 입력해 주세요") @Size(max = 200, message = "합의서 제목은 200자 이내로 써 주세요") String title,
            @NotNull(message = "근로자대표 구분을 골라 주세요") WorkerRepAgreement.RepType repType,
            @NotNull(message = "체결일을 입력해 주세요") LocalDate signedDate,
            @NotNull(message = "합의 효력 시작일을 입력해 주세요") LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotBlank(message = "합의서 문서(파일 번호·보관 위치)를 입력해 주세요") @Size(max = 500, message = "문서 참조는 500자 이내로 써 주세요") String documentRef,
            @Size(max = 500, message = "비고는 500자 이내로 써 주세요") String note) {
    }

    public record AgreementView(long agreementId, String kind, String title, String repType, LocalDate signedDate,
            LocalDate effectiveFrom, LocalDate effectiveTo, String documentRef, String note) {
        static AgreementView of(WorkerRepAgreement a) {
            return new AgreementView(a.getAgreementId(), a.getKind().name(), a.getTitle(), a.getRepType().name(),
                    a.getSignedDate(), a.getEffectiveFrom(), a.getEffectiveTo(), a.getDocumentRef(), a.getNote());
        }
    }

    /** 다른 휴일 행을 가리킬 때(대체 관계). */
    public record HolidayRef(long holidayId, LocalDate date, String name) {
        static HolidayRef of(Holiday h) {
            return h == null ? null : new HolidayRef(h.getHolidayId(), h.getHolidayDate(), h.getHolidayName());
        }
    }

    /**
     * 휴일 한 행.
     *
     * @param effective  실제로 쉬는 날이면 true. 휴일 대체로 근로일이 된 원래 공휴일은 false
     * @param replacedBy 이 공휴일을 대신하는 날(대체됐을 때)
     * @param replaces   대체된 휴일이 대신하는 원래 공휴일
     */
    public record HolidayView(long holidayId, LocalDate date, String name, String kind, String kindLabel, boolean paid,
            boolean effective, HolidayRef replacedBy, HolidayRef replaces, Long agreementId, String agreementTitle,
            String sourceRef, LocalDate verifiedDate, String note) {
    }

    public record ImportResult(int inserted, int skipped, List<HolidayView> rows) {
    }
}
