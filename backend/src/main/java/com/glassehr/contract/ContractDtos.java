package com.glassehr.contract;

import com.glassehr.contract.ContractTermsChecker.MissingTerm;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 근로계약 API 요청·응답 형식. 길이 제한은 V012 컬럼 길이와 같다. */
public final class ContractDtos {

    private ContractDtos() {
    }

    /** 1주 소정근로시간(분) 입력 상한: 1주 전체 분. 법령 기준값이 아니라 입력 오류 방지용이다. */
    static final int MINUTES_PER_WEEK = 7 * 24 * 60;

    /** NUMBER(15) 상한. */
    static final long MAX_WAGE = 999_999_999_999_999L;

    /** 등록·수정 요청. 명시사항은 비워도 저장되고, 응답의 missingTerms 로 경고한다. */
    public record ContractRequest(
            @NotBlank(message = "계약 유형(고용형태)을 선택해 주세요")
            String contractTypeCode,
            @NotNull(message = "계약 체결일을 입력해 주세요")
            LocalDate contractDate,
            @NotNull(message = "계약 시작일을 입력해 주세요")
            LocalDate startDate,
            LocalDate endDate,
            @PositiveOrZero(message = "임금은 0 이상이어야 해요") @Max(value = MAX_WAGE, message = "임금이 너무 커요")
            Long wageAmount,
            @Pattern(regexp = "^(HOURLY|DAILY|WEEKLY|MONTHLY|ANNUAL)$",
                    message = "임금 지급 단위는 HOURLY·DAILY·WEEKLY·MONTHLY·ANNUAL 중 하나예요")
            String wageUnitCode,
            @Size(max = 1000, message = "임금 구성항목은 1000자 이내로 써 주세요")
            String wageComponents,
            @Size(max = 1000, message = "임금 계산방법은 1000자 이내로 써 주세요")
            String wageCalcMethod,
            @Size(max = 1000, message = "임금 지급방법은 1000자 이내로 써 주세요")
            String wagePaymentMethod,
            @Positive(message = "1주 소정근로시간은 0보다 커야 해요")
            @Max(value = MINUTES_PER_WEEK, message = "1주 소정근로시간(분)이 너무 커요")
            Integer weeklyContractMinutes,
            @Size(max = 1000, message = "근로시간·휴게는 1000자 이내로 써 주세요")
            String workHoursText,
            @Size(max = 1000, message = "근로일·근로일별 근로시간은 1000자 이내로 써 주세요")
            String workDaysText,
            @Size(max = 1000, message = "휴일은 1000자 이내로 써 주세요")
            String holidayText,
            @Size(max = 1000, message = "휴가는 1000자 이내로 써 주세요")
            String leaveText,
            @Size(max = 300, message = "취업 장소는 300자 이내로 써 주세요")
            String workPlace,
            @Size(max = 500, message = "업무는 500자 이내로 써 주세요")
            String jobDuty,
            @Size(max = 2000, message = "그 밖의 근로조건은 2000자 이내로 써 주세요")
            String otherTerms) {
    }

    /** 서면 교부일 기록. */
    public record DeliveryRequest(@NotNull(message = "서면 교부일을 입력해 주세요") LocalDate deliveredDate) {
    }

    public record ContractView(long contractId, long empId, String contractTypeCode, LocalDate contractDate,
            LocalDate startDate, LocalDate endDate, Long wageAmount, String wageUnitCode, String wageComponents,
            String wageCalcMethod, String wagePaymentMethod, Integer weeklyContractMinutes, String workHoursText,
            String workDaysText, String holidayText, String leaveText, String workPlace, String jobDuty,
            String otherTerms, LocalDate writtenDeliveredDate, List<MissingTerm> missingTerms) {

        static ContractView of(EmploymentContract c) {
            EmploymentContract.Terms t = c.terms();
            return new ContractView(c.getContractId(), c.getEmpId(), t.contractTypeCode(), t.contractDate(),
                    t.startDate(), t.endDate(), t.wageAmount(), t.wageUnitCode(), t.wageComponents(),
                    t.wageCalcMethod(), t.wagePaymentMethod(), t.weeklyContractMinutes(), t.workHoursText(),
                    t.workDaysText(), t.holidayText(), t.leaveText(), t.workPlace(), t.jobDuty(), t.otherTerms(),
                    c.getWrittenDeliveredDate(), ContractTermsChecker.check(t));
        }
    }

    /** 서면 미교부 목록 한 줄. */
    public record UndeliveredRow(long contractId, long empId, String empNo, String empName, String contractTypeCode,
            LocalDate contractDate, LocalDate startDate, LocalDate endDate, long daysSinceContract) {
    }

    public enum AlertType {
        /** 계약 종료일이 알림 기간 안에 있음. */
        CONTRACT_EXPIRING,
        /** 마지막 계약이 끝났는데 재직 중이고 다음 계약이 없음. */
        CONTRACT_EXPIRED,
        /** 기간제 계속근로 총기간이 사용기간 상한(기간제법 제4조)에 가까움·도래함·넘음. */
        FIXED_TERM_LIMIT,
        /** 기간제인데 사용기간 상한 기준값(legal_rate)이 없어 계산하지 못함. */
        FIXED_TERM_LIMIT_UNKNOWN
    }

    /**
     * 알림 한 줄. dueDate 는 계약 종료일 또는 사용기간 상한의 마지막 날, daysLeft 는 기준일부터 dueDate 까지 일수(지났으면 음수).
     * continuousStartDate 는 FIXED_TERM_LIMIT 계열에서 계속근로 시작일.
     */
    public record AlertRow(AlertType type, long empId, String empNo, String empName, long contractId,
            LocalDate contractEndDate, LocalDate continuousStartDate, LocalDate dueDate, Long daysLeft) {
    }
}
