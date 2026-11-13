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
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * legal_rate → 검사 기준값 변환. 여기 값(7.5시간 등)은 변환 확인용 <b>임의 값이며 법령 수치가 아니다</b>.
 */
class WorkPatternLegalLimitsTest {

    private static final LocalDate D = LocalDate.of(2026, 3, 1);

    private static LegalRateService rates(Map<LegalRateItem, String> values) {
        LegalRateService s = mock(LegalRateService.class);
        values.forEach((item, v) -> when(s.getEffective(eq(item), eq(D))).thenReturn(
                new LegalRateRow(item.code(), LocalDate.of(2000, 1, 1), null, new BigDecimal(v), item.unit(), "조문", null)));
        return s;
    }

    private static Map<LegalRateItem, String> fake() {
        return new java.util.EnumMap<>(Map.of(
                LegalRateItem.DAILY_STANDARD_WORK_HOURS, "7.5",
                LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, "33.00",
                LegalRateItem.BREAK_SHORT_THRESHOLD_HOURS, "3",
                LegalRateItem.BREAK_SHORT_MIN_MINUTES, "20",
                LegalRateItem.BREAK_LONG_THRESHOLD_HOURS, "6",
                LegalRateItem.BREAK_LONG_MIN_MINUTES, "45",
                LegalRateItem.WEEKLY_PAID_HOLIDAY_MIN_COUNT, "2"));
    }

    @Test
    void 시간은_분으로_바꾸고_적용일_값을_읽는다() {
        WorkPatternRules.Limits l = new WorkPatternLegalLimits(rates(fake())).asOf(D);
        assertThat(l).isEqualTo(new WorkPatternRules.Limits(450, 1980, 180, 20, 360, 45, 2));
    }

    @Test
    void 값이_없으면_기본값_없이_예외() {
        LegalRateService s = mock(LegalRateService.class);
        LegalRateException missing = mock(LegalRateException.class);
        when(s.getEffective(any(LegalRateItem.class), any())).thenThrow(missing);
        assertThatThrownBy(() -> new WorkPatternLegalLimits(s).asOf(D)).isSameAs(missing);
    }

    @Test
    void 분으로_나누어떨어지지_않거나_0_이하면_거부() {
        for (String bad : new String[] {"7.505", "0", "-1"}) {
            Map<LegalRateItem, String> v = fake();
            v.put(LegalRateItem.DAILY_STANDARD_WORK_HOURS, bad);
            assertThatThrownBy(() -> new WorkPatternLegalLimits(rates(v)).asOf(D)).as(bad)
                    .isInstanceOf(IllegalStateException.class);
        }
        Map<LegalRateItem, String> frac = fake();
        frac.put(LegalRateItem.BREAK_LONG_MIN_MINUTES, "30.5");
        assertThatThrownBy(() -> new WorkPatternLegalLimits(rates(frac)).asOf(D)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 휴게_기준_구간_순서가_뒤집히면_거부() {
        Map<LegalRateItem, String> v = fake();
        v.put(LegalRateItem.BREAK_LONG_THRESHOLD_HOURS, "3");
        assertThatThrownBy(() -> new WorkPatternLegalLimits(rates(v)).asOf(D)).isInstanceOf(IllegalStateException.class);
    }
}
