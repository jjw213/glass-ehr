package com.glassehr.attendance;

import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 근무유형 검사 기준값을 legal_rate 에서 적용일 기준으로 읽는다(docs/legal-rate-design.md 조회 계약).
 * 값이 없으면 {@link com.glassehr.legalrate.LegalRateException} — 기본값을 쓰지 않는다.
 *
 * <ul>
 *   <li>근로기준법 제50조 제1항·제2항: {@link LegalRateItem#WEEKLY_STANDARD_WORK_HOURS}, {@link LegalRateItem#DAILY_STANDARD_WORK_HOURS}</li>
 *   <li>근로기준법 제54조 제1항: {@link LegalRateItem#BREAK_SHORT_THRESHOLD_HOURS}·{@link LegalRateItem#BREAK_SHORT_MIN_MINUTES},
 *       {@link LegalRateItem#BREAK_LONG_THRESHOLD_HOURS}·{@link LegalRateItem#BREAK_LONG_MIN_MINUTES}</li>
 *   <li>근로기준법 제55조 제1항: {@link LegalRateItem#WEEKLY_PAID_HOLIDAY_MIN_COUNT}</li>
 * </ul>
 * 시간(HOURS) 값은 분으로 바꾼다. 분으로 나누어떨어지지 않거나 0 이하이면 잘못 입력된 기준값으로 보고 예외.
 */
@Component
public class WorkPatternLegalLimits {

    /** 시간 → 분 환산(시계 단위, 법정 수치 아님). */
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final LegalRateService legalRates;

    public WorkPatternLegalLimits(LegalRateService legalRates) {
        this.legalRates = legalRates;
    }

    public WorkPatternRules.Limits asOf(LocalDate date) {
        Objects.requireNonNull(date, "date");
        WorkPatternRules.Limits limits = new WorkPatternRules.Limits(
                hoursAsMinutes(LegalRateItem.DAILY_STANDARD_WORK_HOURS, date),
                hoursAsMinutes(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, date),
                hoursAsMinutes(LegalRateItem.BREAK_SHORT_THRESHOLD_HOURS, date),
                whole(LegalRateItem.BREAK_SHORT_MIN_MINUTES, date),
                hoursAsMinutes(LegalRateItem.BREAK_LONG_THRESHOLD_HOURS, date),
                whole(LegalRateItem.BREAK_LONG_MIN_MINUTES, date),
                whole(LegalRateItem.WEEKLY_PAID_HOLIDAY_MIN_COUNT, date));
        if (limits.breakLongThresholdMinutes() <= limits.breakShortThresholdMinutes()) {
            throw new IllegalStateException("휴게 기준 근로시간은 긴 구간이 짧은 구간보다 커야 해요(" + date + " 적용값 확인)");
        }
        return limits;
    }

    private int hoursAsMinutes(LegalRateItem item, LocalDate date) {
        LegalRateRow row = legalRates.getEffective(item, date);
        return positiveInt(row, row.rateValue().multiply(MINUTES_PER_HOUR));
    }

    private int whole(LegalRateItem item, LocalDate date) {
        LegalRateRow row = legalRates.getEffective(item, date);
        return positiveInt(row, row.rateValue());
    }

    private static int positiveInt(LegalRateRow row, BigDecimal v) {
        int n;
        try {
            n = v.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("기준값을 분·횟수 정수로 바꿀 수 없어요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        if (n <= 0) {
            throw new IllegalStateException("기준값은 1 이상이어야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        return n;
    }
}
