package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.legalrate.LegalRateException;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** legal_rate 값 → 집계 기준값 변환. 값은 이 테스트가 넣는 입력(법령 정답 아님). */
class WorkHoursLegalRulesTest {

    private static final LocalDate D = LocalDate.of(2099, 3, 2);
    private LegalRateService legalRates;
    private WorkHoursLegalRules rules;

    @BeforeEach
    void setUp() {
        legalRates = mock(LegalRateService.class);
        rules = new WorkHoursLegalRules(legalRates);
    }

    private void put(LegalRateItem item, String value) {
        when(legalRates.getEffective(eq(item), any())).thenReturn(
                new LegalRateRow(item.code(), D, null, new BigDecimal(value), item.unit(), "test", null));
    }

    private void putAll() {
        put(LegalRateItem.DAILY_STANDARD_WORK_HOURS, "7");
        put(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, "35.5");
        put(LegalRateItem.HOLIDAY_PREMIUM_THRESHOLD_HOURS, "6");
        put(LegalRateItem.NIGHT_WORK_START_HOUR, "21");
        put(LegalRateItem.NIGHT_WORK_END_HOUR, "5");
        put(LegalRateItem.OVERTIME_PREMIUM_RATE, "11");
        put(LegalRateItem.NIGHT_PREMIUM_RATE, "22");
        put(LegalRateItem.HOLIDAY_PREMIUM_RATE, "33");
        put(LegalRateItem.HOLIDAY_EXCESS_PREMIUM_RATE, "44.5");
    }

    @Test
    void 시간은_분으로_시각은_자정부터의_분으로_바꾸고_가산율은_그대로() {
        putAll();
        WorkHoursLegalRules.Applied a = rules.asOf(D);
        assertThat(a.rules()).isEqualTo(new WorkHoursCalculator.Rules(420, 2130, 360, 21 * 60, 5 * 60));
        assertThat(a.rates().overtime()).isEqualByComparingTo("11");
        assertThat(a.rates().night()).isEqualByComparingTo("22");
        assertThat(a.rates().holiday()).isEqualByComparingTo("33");
        assertThat(a.rates().holidayExcess()).isEqualByComparingTo("44.5");
    }

    @Test
    void 값이_없으면_기본값_없이_예외() {
        putAll();
        when(legalRates.getEffective(eq(LegalRateItem.NIGHT_PREMIUM_RATE), any()))
                .thenThrow(mock(LegalRateException.class));
        assertThatThrownBy(() -> rules.asOf(D)).isInstanceOf(LegalRateException.class);
    }

    @Test
    void 분_정수가_아니거나_시각이_범위_밖이면_예외() {
        putAll();
        put(LegalRateItem.DAILY_STANDARD_WORK_HOURS, "7.999");
        assertThatThrownBy(() -> rules.asOf(D)).isInstanceOf(IllegalStateException.class);
        putAll();
        put(LegalRateItem.NIGHT_WORK_START_HOUR, "24");
        assertThatThrownBy(() -> rules.asOf(D)).isInstanceOf(IllegalStateException.class);
        putAll();
        put(LegalRateItem.OVERTIME_PREMIUM_RATE, "-1");
        assertThatThrownBy(() -> rules.asOf(D)).isInstanceOf(IllegalStateException.class);
    }
}
