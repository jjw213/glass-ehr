package com.glassehr.attendance;

import com.glassehr.attendance.FlexibleWorkCalculator.Limits;
import com.glassehr.attendance.FlexibleWorkCalculator.SelectiveJob;
import com.glassehr.attendance.FlexibleWorkCalculator.WorkSystem;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 유연근무제 법정 한도를 legal_rate(V028 항목, seed/legal_rate_flexible_work.sql)에서 단위기간 시작일 기준으로 읽는다.
 * 1일·1주 기준시간은 {@link WorkHoursLegalRules}(근로기준법 제50조)와 같은 값을 쓴다. 값이 없으면 LegalRateException.
 */
@Component
public class FlexibleWorkLegalRules {

    /** 시간 → 분 환산(시계 단위, 법정 수치 아님). */
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final LegalRateService legalRates;
    private final WorkHoursLegalRules workHours;

    public FlexibleWorkLegalRules(LegalRateService legalRates, WorkHoursLegalRules workHours) {
        this.legalRates = legalRates;
        this.workHours = workHours;
    }

    /** 탄력적 근로시간제 한도. */
    public Limits flexibleAsOf(WorkSystem system, LocalDate date) {
        Objects.requireNonNull(date, "date");
        WorkHoursCalculator.Rules base = workHours.asOf(date).rules();
        int day = base.dailyStandardMinutes();
        int week = base.weeklyStandardMinutes();
        return switch (system) {
            // 근로기준법 제51조 제1항: 2주 이내, 특정한 주 48시간(특정한 날 상한 없음)
            case FLEXIBLE_SHORT -> new Limits(count(LegalRateItem.FLEX_SHORT_UNIT_PERIOD_MAX_WEEKS, date), 0,
                    minutes(LegalRateItem.FLEX_SHORT_WEEK_MAX_HOURS, date), 0, 0, day, week, 0);
            // 근로기준법 제51조 제2항: 3개월 이내, 특정한 주 52시간·특정한 날 12시간
            case FLEXIBLE_WRITTEN -> new Limits(0, count(LegalRateItem.FLEX_WRITTEN_UNIT_PERIOD_MAX_MONTHS, date),
                    minutes(LegalRateItem.FLEX_WRITTEN_WEEK_MAX_HOURS, date),
                    minutes(LegalRateItem.FLEX_WRITTEN_DAY_MAX_HOURS, date), 0, day, week, 0);
            // 근로기준법 제51조의2 제1항·제2항 제1호: 6개월 이내, 52시간·12시간, 근로일 간 11시간 휴식
            case FLEXIBLE_LONG -> new Limits(0, count(LegalRateItem.FLEX_LONG_UNIT_PERIOD_MAX_MONTHS, date),
                    minutes(LegalRateItem.FLEX_LONG_WEEK_MAX_HOURS, date),
                    minutes(LegalRateItem.FLEX_LONG_DAY_MAX_HOURS, date),
                    minutes(LegalRateItem.FLEX_LONG_DAILY_REST_MIN_HOURS, date), day, week,
                    count(LegalRateItem.FLEX_WRITTEN_UNIT_PERIOD_MAX_MONTHS, date));
            default -> throw new IllegalArgumentException("탄력적 근로시간제가 아니에요: " + system);
        };
    }

    /** 선택적 근로시간제 정산기간 한도(근로기준법 제52조 제1항). 상한은 없고 restMinutes 는 1개월 초과 정산기간 휴식. */
    public Limits selectiveAsOf(SelectiveJob job, LocalDate date) {
        Objects.requireNonNull(date, "date");
        WorkHoursCalculator.Rules base = workHours.asOf(date).rules();
        int months = count(job == SelectiveJob.RND
                ? LegalRateItem.SELECTIVE_SETTLEMENT_RND_MAX_MONTHS
                : LegalRateItem.SELECTIVE_SETTLEMENT_MAX_MONTHS, date);
        // 근로기준법 제52조 제2항 제1호: 1개월 초과 정산기간의 근로일 간 연속 11시간 휴식
        int rest = minutes(LegalRateItem.SELECTIVE_RND_DAILY_REST_MIN_HOURS, date);
        return new Limits(0, months, 0, 0, rest, base.dailyStandardMinutes(), base.weeklyStandardMinutes(), 0);
    }

    private int count(LegalRateItem item, LocalDate date) {
        LegalRateRow row = legalRates.getEffective(item, date);
        int n = exactInt(row, row.rateValue());
        if (n <= 0) {
            throw new IllegalStateException("기준값은 0보다 커야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        return n;
    }

    private int minutes(LegalRateItem item, LocalDate date) {
        LegalRateRow row = legalRates.getEffective(item, date);
        int n = exactInt(row, row.rateValue().multiply(MINUTES_PER_HOUR));
        if (n <= 0) {
            throw new IllegalStateException("기준시간은 0보다 커야 해요: " + row.itemCode() + " " + row.effectiveFrom());
        }
        return n;
    }

    private static int exactInt(LegalRateRow row, BigDecimal v) {
        try {
            return v.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("기준값을 정수로 바꿀 수 없어요: " + row.itemCode() + " " + row.effectiveFrom());
        }
    }
}
