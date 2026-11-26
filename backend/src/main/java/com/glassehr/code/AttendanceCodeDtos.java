package com.glassehr.code;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 근태코드 API 요청·응답 형식(#143). 프론트 타입: frontend/src/api/attendanceCodes.ts */
public final class AttendanceCodeDtos {

    private AttendanceCodeDtos() {
    }

    /**
     * 법정 기준값 하나(legal_rate, 조회일 기준). 화면에서 고치지 않는다.
     *
     * @param value null 이면 아직 기준값이 등록되지 않았다(법령 데이터 작업 대기)
     */
    public record LegalValue(String itemCode, String label, BigDecimal value, String unit, LocalDate effectiveFrom) {
    }

    /**
     * 휴가 종류 상세 설정.
     *
     * @param paid            null = 법정휴가(유급 기간은 legalValues)
     * @param annualLimitDays null = 한도 없음
     * @param genderLimit     F/M/null. 기본 코드(법정휴가)에서만 값이 있다
     * @param legalValues     기본 코드의 법정 일수·유급 일수(legal_rate). 약정·무급휴가는 빈 목록
     */
    public record LeaveDetail(boolean unitDay, boolean unitHalfDay, boolean unitHour, Boolean paid,
            boolean proofRequired, BigDecimal annualLimitDays, String genderLimit, String legalBasis,
            List<LegalValue> legalValues) {
    }

    /**
     * 근태코드 한 줄.
     *
     * @param system     기본 코드(삭제 불가)
     * @param leave      휴가 종류(LEAVE_TYPE)의 상세 설정. 근태 구분은 null
     */
    public record AttendanceCodeView(String groupCode, String code, String name, String description, int sortOrder,
            boolean active, boolean system, String processType, boolean inUse, long usageCount, LeaveDetail leave) {
    }

    /** 휴가 종류 상세 설정 입력. 성별 제한·법정 근거는 마이그레이션으로만 정한다. */
    public record LeaveDetailRequest(
            @NotNull(message = "종일 사용 여부를 정해 주세요") Boolean unitDay,
            @NotNull(message = "반차 사용 여부를 정해 주세요") Boolean unitHalfDay,
            @NotNull(message = "시간 단위 사용 여부를 정해 주세요") Boolean unitHour,
            Boolean paid,
            @NotNull(message = "증빙 필요 여부를 정해 주세요") Boolean proofRequired,
            @DecimalMin(value = "0.5", message = "연간 한도는 0.5일 이상이어야 해요")
            @DecimalMax(value = "366", message = "연간 한도는 366일 이하여야 해요")
            @Digits(integer = 3, fraction = 1, message = "연간 한도는 소수 첫째 자리까지 써 주세요")
            BigDecimal annualLimitDays) {
    }

    /** 근태코드 추가. 코드값은 한 번 정하면 바꿀 수 없다. 휴가 종류는 leave 가 꼭 있어야 한다. */
    public record AttendanceCodeCreateRequest(
            @NotNull(message = "코드값을 입력해 주세요")
            @Pattern(regexp = CodeGroups.CODE_PATTERN,
                    message = "코드값은 영문 대문자로 시작하고 영문 대문자·숫자·밑줄만 30자 이내로 써 주세요")
            String code,
            @NotBlank(message = "코드명을 입력해 주세요")
            @Size(max = 100, message = "코드명은 100자 이내로 써 주세요")
            String name,
            @Size(max = 500, message = "설명은 500자 이내로 써 주세요")
            String description,
            @NotNull(message = "정렬 순서를 입력해 주세요")
            @Min(value = 0, message = "정렬 순서는 0 이상이어야 해요")
            @Max(value = 99999, message = "정렬 순서는 99999 이하여야 해요")
            Integer sortOrder,
            @NotNull(message = "사용 여부를 정해 주세요")
            Boolean active,
            @NotBlank(message = "처리방식을 골라 주세요")
            String processType,
            @Valid LeaveDetailRequest leave) {
    }

    /** 근태코드 수정. 코드값은 바꾸지 않는다. 기본 코드는 처리방식·유급·한도를 바꿀 수 없다. */
    public record AttendanceCodeUpdateRequest(
            @NotBlank(message = "코드명을 입력해 주세요")
            @Size(max = 100, message = "코드명은 100자 이내로 써 주세요")
            String name,
            @Size(max = 500, message = "설명은 500자 이내로 써 주세요")
            String description,
            @NotNull(message = "정렬 순서를 입력해 주세요")
            @Min(value = 0, message = "정렬 순서는 0 이상이어야 해요")
            @Max(value = 99999, message = "정렬 순서는 99999 이하여야 해요")
            Integer sortOrder,
            @NotNull(message = "사용 여부를 정해 주세요")
            Boolean active,
            @NotBlank(message = "처리방식을 골라 주세요")
            String processType,
            @Valid LeaveDetailRequest leave) {
    }
}
