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
import org.junit.jupiter.api.Test;

class AnnualLeaveLegalRatesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 1);

    private static LegalRateRow row(LegalRateItem item, String v) {
        return new LegalRateRow(item.code(), LocalDate.of(2000, 1, 1), null, new BigDecimal(v), item.unit(), "test", null);
    }

    private static LegalRateService service(String base) {
        LegalRateService s = mock(LegalRateService.class);
        // 시험용 값(법정 값 아님).
        when(s.getEffective(eq(LegalRateItem.ANNUAL_LEAVE_BASE_DAYS), any())).thenReturn(row(LegalRateItem.ANNUAL_LEAVE_BASE_DAYS, base));
        when(s.getEffective(eq(LegalRateItem.ANNUAL_LEAVE_ATTENDANCE_RATE), any())).thenReturn(row(LegalRateItem.ANNUAL_LEAVE_ATTENDANCE_RATE, "70.5"));
        when(s.getEffective(eq(LegalRateItem.ANNUAL_LEAVE_MONTHLY_DAYS), any())).thenReturn(row(LegalRateItem.ANNUAL_LEAVE_MONTHLY_DAYS, "2"));
        when(s.getEffective(eq(LegalRateItem.ANNUAL_LEAVE_INCREMENT_BASE_YEARS), any())).thenReturn(row(LegalRateItem.ANNUAL_LEAVE_INCREMENT_BASE_YEARS, "3"));
        when(s.getEffective(eq(LegalRateItem.ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS), any())).thenReturn(row(LegalRateItem.ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS, "4"));
        when(s.getEffective(eq(LegalRateItem.ANNUAL_LEAVE_INCREMENT_DAYS), any())).thenReturn(row(LegalRateItem.ANNUAL_LEAVE_INCREMENT_DAYS, "5"));
        when(s.getEffective(eq(LegalRateItem.ANNUAL_LEAVE_MAX_DAYS), any())).thenReturn(row(LegalRateItem.ANNUAL_LEAVE_MAX_DAYS, "30"));
        return s;
    }

    @Test
    void 일곱_항목을_묶어_읽는다() {
        AnnualLeaveRules.Rates r = new AnnualLeaveLegalRates(service("11")).asOf(DAY);
        assertThat(r).isEqualTo(new AnnualLeaveRules.Rates(11, new BigDecimal("70.5"), 2, 3, 4, 5, 30));
    }

    @Test
    void 정수가_아닌_일수는_거부한다() {
        assertThatThrownBy(() -> new AnnualLeaveLegalRates(service("11.5")).asOf(DAY)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 값이_없으면_기본값_없이_예외() {
        LegalRateService s = mock(LegalRateService.class);
        when(s.getEffective(any(LegalRateItem.class), any())).thenThrow(mock(LegalRateException.class));
        assertThatThrownBy(() -> new AnnualLeaveLegalRates(s).asOf(DAY)).isInstanceOf(LegalRateException.class);
    }
}
