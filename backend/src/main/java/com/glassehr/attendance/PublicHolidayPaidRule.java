package com.glassehr.attendance;

import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 근로기준법 제55조 제2항(관공서 공휴일을 유급휴일로 보장)이 그 날짜에 이 사업장에 적용되는지 판정한다.
 *
 * <p>제55조 제2항은 부칙에 따라 상시근로자 수별로 시행일이 다르다. 적용 규모 하한은 법정 수치이므로
 * legal_rate {@link LegalRateItem#PUBLIC_HOLIDAY_PAID_MIN_HEADCOUNT}(적용일 기준)에서 읽고,
 * 이 사업장 상시근로자 수(회사 설정 {@code glassehr.workplace.regular-headcount})가 그 값 이상이면 적용한다.
 * 기준값이 없으면 {@link com.glassehr.legalrate.LegalRateException} — 기본값으로 유급 판정을 하지 않는다.
 */
@Component
public class PublicHolidayPaidRule {

    private final LegalRateService legalRates;
    private final int regularHeadcount;

    public PublicHolidayPaidRule(LegalRateService legalRates,
            @Value("${glassehr.workplace.regular-headcount}") int regularHeadcount) {
        if (regularHeadcount <= 0) {
            throw new IllegalArgumentException("상시근로자 수 설정은 1 이상이어야 해요: " + regularHeadcount);
        }
        this.legalRates = legalRates;
        this.regularHeadcount = regularHeadcount;
    }

    /** 근로기준법 제55조 제2항·부칙: 그 날짜에 이 사업장에 공휴일 유급휴일 보장이 적용되면 true. */
    public boolean appliesOn(LocalDate date) {
        Objects.requireNonNull(date, "date");
        LegalRateRow row = legalRates.getEffective(LegalRateItem.PUBLIC_HOLIDAY_PAID_MIN_HEADCOUNT, date);
        int min;
        try {
            min = row.rateValue().intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("적용 규모 하한은 정수(명)여야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        if (min <= 0) {
            throw new IllegalStateException("적용 규모 하한은 1 이상이어야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        return regularHeadcount >= min;
    }
}
