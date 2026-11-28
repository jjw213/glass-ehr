package com.glassehr.attendance;

import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 근로자 유형 기준값을 legal_rate 에서 적용일 기준으로 읽는다(#158). 값이 없으면
 * {@link com.glassehr.legalrate.LegalRateException} — 기본값을 쓰지 않는다.
 *
 * <ul>
 *   <li>근로기준법 제18조 제3항: {@link LegalRateItem#SHORT_TIME_WORKER_WEEKLY_HOURS_THRESHOLD},
 *       {@link LegalRateItem#SHORT_TIME_WORKER_AVERAGE_WEEKS}</li>
 *   <li>시행령 제9조 제1항 [별표 2] 제4호나목 연차 산식의 8시간: {@link LegalRateItem#DAILY_STANDARD_WORK_HOURS}
 *       (근로기준법 제50조 제2항 1일 기준근로시간과 같은 값)</li>
 * </ul>
 */
@Component
public class WorkerTypeLegalRules {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(WorkerTypeRules.MINUTES_PER_HOUR);

    private final LegalRateService legalRates;

    public WorkerTypeLegalRules(LegalRateService legalRates) {
        this.legalRates = legalRates;
    }

    public WorkerTypeRules.Thresholds asOf(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return new WorkerTypeRules.Thresholds(
                exact(LegalRateItem.SHORT_TIME_WORKER_WEEKLY_HOURS_THRESHOLD, date, MINUTES_PER_HOUR),
                exact(LegalRateItem.SHORT_TIME_WORKER_AVERAGE_WEEKS, date, BigDecimal.ONE),
                exact(LegalRateItem.DAILY_STANDARD_WORK_HOURS, date, MINUTES_PER_HOUR));
    }

    private int exact(LegalRateItem item, LocalDate date, BigDecimal factor) {
        LegalRateRow row = legalRates.getEffective(item, date);
        try {
            int n = row.rateValue().multiply(factor).intValueExact();
            if (n <= 0) {
                throw new IllegalStateException("기준값은 1 이상이어야 해요: " + row.itemCode() + " " + row.effectiveFrom());
            }
            return n;
        } catch (ArithmeticException e) {
            throw new IllegalStateException("기준값을 분·주 정수로 바꿀 수 없어요: " + row.itemCode() + " " + row.effectiveFrom());
        }
    }
}
