package com.glassehr.attendance;

import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 연차 기준값(근로기준법 제60조 제1항·제2항·제4항)을 legal_rate 에서 적용일 기준으로 읽는다
 * (docs/legal-rate-design.md 조회 계약). 값이 없으면 {@link com.glassehr.legalrate.LegalRateException} — 기본값을 쓰지 않는다.
 */
@Component
public class AnnualLeaveLegalRates {

    private final LegalRateService legalRates;

    public AnnualLeaveLegalRates(LegalRateService legalRates) {
        this.legalRates = legalRates;
    }

    public AnnualLeaveRules.Rates asOf(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return new AnnualLeaveRules.Rates(
                whole(LegalRateItem.ANNUAL_LEAVE_BASE_DAYS, date),
                legalRates.getEffective(LegalRateItem.ANNUAL_LEAVE_ATTENDANCE_RATE, date).rateValue(),
                whole(LegalRateItem.ANNUAL_LEAVE_MONTHLY_DAYS, date),
                whole(LegalRateItem.ANNUAL_LEAVE_INCREMENT_BASE_YEARS, date),
                whole(LegalRateItem.ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS, date),
                whole(LegalRateItem.ANNUAL_LEAVE_INCREMENT_DAYS, date),
                whole(LegalRateItem.ANNUAL_LEAVE_MAX_DAYS, date));
    }

    private int whole(LegalRateItem item, LocalDate date) {
        LegalRateRow row = legalRates.getEffective(item, date);
        try {
            return row.rateValue().intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("기준값은 정수여야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
    }
}
