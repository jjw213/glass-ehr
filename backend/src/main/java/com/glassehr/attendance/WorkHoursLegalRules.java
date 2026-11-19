package com.glassehr.attendance;

import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 근로시간 집계 기준값과 가산율을 legal_rate 에서 근무일 기준으로 읽는다(docs/legal-rate-design.md 조회 계약).
 * 값이 없으면 {@link com.glassehr.legalrate.LegalRateException} — 기본값을 쓰지 않는다.
 *
 * <ul>
 *   <li>근로기준법 제50조 제1항·제2항: WEEKLY_STANDARD_WORK_HOURS, DAILY_STANDARD_WORK_HOURS</li>
 *   <li>근로기준법 제56조 제1항: OVERTIME_PREMIUM_RATE</li>
 *   <li>근로기준법 제56조 제2항: HOLIDAY_PREMIUM_THRESHOLD_HOURS, 제1호 HOLIDAY_PREMIUM_RATE, 제2호 HOLIDAY_EXCESS_PREMIUM_RATE</li>
 *   <li>근로기준법 제56조 제3항: NIGHT_WORK_START_HOUR, NIGHT_WORK_END_HOUR, NIGHT_PREMIUM_RATE</li>
 * </ul>
 * 가산율은 금액 계산 없이 집계 행에 함께 남겨 급여 모듈이 그대로 쓰게 한다.
 */
@Component
public class WorkHoursLegalRules {

    /** 시간 → 분 환산(시계 단위, 법정 수치 아님). */
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final LegalRateService legalRates;

    public WorkHoursLegalRules(LegalRateService legalRates) {
        this.legalRates = legalRates;
    }

    /** 가산율(퍼센트 숫자, 예: 고시 50% → 50). */
    public record Rates(BigDecimal overtime, BigDecimal night, BigDecimal holiday, BigDecimal holidayExcess) {
    }

    public record Applied(WorkHoursCalculator.Rules rules, Rates rates) {
    }

    public Applied asOf(LocalDate date) {
        Objects.requireNonNull(date, "date");
        WorkHoursCalculator.Rules rules = new WorkHoursCalculator.Rules(
                hoursAsMinutes(LegalRateItem.DAILY_STANDARD_WORK_HOURS, date),
                hoursAsMinutes(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, date),
                hoursAsMinutes(LegalRateItem.HOLIDAY_PREMIUM_THRESHOLD_HOURS, date),
                hourOfDayAsMinute(LegalRateItem.NIGHT_WORK_START_HOUR, date),
                hourOfDayAsMinute(LegalRateItem.NIGHT_WORK_END_HOUR, date));
        Rates rates = new Rates(
                percent(LegalRateItem.OVERTIME_PREMIUM_RATE, date),
                percent(LegalRateItem.NIGHT_PREMIUM_RATE, date),
                percent(LegalRateItem.HOLIDAY_PREMIUM_RATE, date),
                percent(LegalRateItem.HOLIDAY_EXCESS_PREMIUM_RATE, date));
        return new Applied(rules, rates);
    }

    private int hoursAsMinutes(LegalRateItem item, LocalDate date) {
        LegalRateRow row = legalRates.getEffective(item, date);
        int n = exactInt(row, row.rateValue().multiply(MINUTES_PER_HOUR));
        if (n <= 0) {
            throw new IllegalStateException("기준시간은 0보다 커야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        return n;
    }

    private int hourOfDayAsMinute(LegalRateItem item, LocalDate date) {
        LegalRateRow row = legalRates.getEffective(item, date);
        int h = exactInt(row, row.rateValue());
        if (h < 0 || h > 23) {
            throw new IllegalStateException("시각은 0~23시여야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        return h * 60;
    }

    private BigDecimal percent(LegalRateItem item, LocalDate date) {
        LegalRateRow row = legalRates.getEffective(item, date);
        if (row.rateValue().signum() < 0) {
            throw new IllegalStateException("가산율은 0 이상이어야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        return row.rateValue();
    }

    private static int exactInt(LegalRateRow row, BigDecimal v) {
        try {
            return v.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("기준값을 분 정수로 바꿀 수 없어요: " + row.itemCode() + " " + row.effectiveFrom());
        }
    }
}
