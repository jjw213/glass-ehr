package com.glassehr.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 기준값 조회 방식(적용일·단위·값 검사)만 검사한다. 만료일 정답값은 tests/legal-fixtures/fixed-term-use-period.csv 로
 * FixedTermUsePeriodFixtureTest 에서 검증한다. 여기서 정답 날짜를 직접 만들지 않는다.
 */
class FixedTermUsePeriodCalculatorTest {

    private static final LocalDate START = LocalDate.of(2025, 4, 1);

    private final LegalRateService rates = mock(LegalRateService.class);
    private final FixedTermUsePeriodCalculator calc = new FixedTermUsePeriodCalculator(rates);

    private static LegalRateRow row(String value, String unit) {
        LegalRateRow r = mock(LegalRateRow.class);
        when(r.itemCode()).thenReturn("FIXED_TERM_MAX_USE_YEARS");
        when(r.rateValue()).thenReturn(new BigDecimal(value));
        when(r.unit()).thenReturn(unit);
        when(r.effectiveFrom()).thenReturn(LocalDate.of(2007, 7, 1));
        return r;
    }

    @Test
    void 계속근로_시작일_기준으로_기준값을_조회한다() {
        LegalRateRow r = row("3", "YEARS");
        when(rates.findEffective(eq("FIXED_TERM_MAX_USE_YEARS"), any())).thenReturn(Optional.of(r));
        assertThat(calc.lastDayWithinLimit(START)).contains(FixedTermUsePeriodCalculator.lastDay(START, 3));
        verify(rates).findEffective("FIXED_TERM_MAX_USE_YEARS", START);
    }

    @Test
    void 기준값이_없으면_빈_값이고_기본값을_쓰지_않는다() {
        when(rates.findEffective(eq("FIXED_TERM_MAX_USE_YEARS"), any())).thenReturn(Optional.empty());
        assertThat(calc.lastDayWithinLimit(START)).isEmpty();
    }

    @Test
    void 정수가_아니거나_0_이하이거나_단위가_다르면_거부한다() {
        LegalRateRow frac = row("1.5", "YEARS");
        when(rates.findEffective(eq("FIXED_TERM_MAX_USE_YEARS"), any())).thenReturn(Optional.of(frac));
        assertThatThrownBy(() -> calc.lastDayWithinLimit(START)).isInstanceOf(IllegalStateException.class);

        LegalRateRow zero = row("0", "YEARS");
        when(rates.findEffective(eq("FIXED_TERM_MAX_USE_YEARS"), any())).thenReturn(Optional.of(zero));
        assertThatThrownBy(() -> calc.lastDayWithinLimit(START)).isInstanceOf(IllegalStateException.class);

        LegalRateRow months = row("24", "MONTHS");
        when(rates.findEffective(eq("FIXED_TERM_MAX_USE_YEARS"), any())).thenReturn(Optional.of(months));
        assertThatThrownBy(() -> calc.lastDayWithinLimit(START)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 연_수가_클수록_마지막_날이_늦다() {
        // 정답 날짜가 아니라 단조성만 본다.
        assertThat(FixedTermUsePeriodCalculator.lastDay(START, 3)).isAfter(FixedTermUsePeriodCalculator.lastDay(START, 2));
        assertThat(FixedTermUsePeriodCalculator.lastDay(START, 1)).isAfter(START);
    }
}
