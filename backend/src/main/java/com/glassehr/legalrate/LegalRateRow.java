package com.glassehr.legalrate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * legal_rate 한 행. 값은 {@link BigDecimal}로만 다룬다(부동소수점 금지).
 *
 * <p>기간은 시작일 포함·종료일 제외 {@code [effectiveFrom, effectiveTo)}이며
 * {@code effectiveTo == null}이면 종료일 미정이다. (docs/legal-rate-design.md)
 */
public record LegalRateRow(
        String itemCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal rateValue,
        String unit,
        String legalBasis,
        String sourceUrl) {

    public LegalRateRow {
        Objects.requireNonNull(itemCode, "itemCode");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(rateValue, "rateValue");
    }

    /** 조회일이 이 행의 반개구간 {@code [effectiveFrom, effectiveTo)}에 들어가는지. */
    public boolean isEffectiveOn(LocalDate asOfDate) {
        Objects.requireNonNull(asOfDate, "asOfDate");
        return !asOfDate.isBefore(effectiveFrom)
                && (effectiveTo == null || asOfDate.isBefore(effectiveTo));
    }
}
