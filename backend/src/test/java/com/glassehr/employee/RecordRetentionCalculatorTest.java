package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.legalrate.LegalRateException;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import com.glassehr.legalrate.LegalRateUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 보존기한 = 퇴직일 + legal_rate 연 수. 법정 보존 기간 값 자체는 검증하지 않는다(값은 사람이 입력, 정답은 tests/legal-fixtures 만).
 * 여기서 쓰는 연 수 7·2 는 날짜 계산 확인용 임의 값이며 법령 수치가 아니다.
 */
class RecordRetentionCalculatorTest {

    private static LegalRateRow row(String value) {
        return new LegalRateRow(LegalRateItem.EMPLOYEE_RECORD_RETENTION_YEARS.code(), LocalDate.of(2000, 1, 1), null,
                new BigDecimal(value), LegalRateUnit.YEARS, "근로기준법 제42조", null);
    }

    @Test
    void 퇴직일_기준_적용값으로_퇴직일에_연수를_더한다() {
        LegalRateService rates = mock(LegalRateService.class);
        LocalDate retire = LocalDate.of(2026, 3, 31);
        when(rates.getEffective(LegalRateItem.EMPLOYEE_RECORD_RETENTION_YEARS, retire)).thenReturn(row("7"));

        assertThat(new RecordRetentionCalculator(rates).retentionUntil(retire)).isEqualTo(LocalDate.of(2033, 3, 31));
        verify(rates).getEffective(eq(LegalRateItem.EMPLOYEE_RECORD_RETENTION_YEARS), eq(retire));
    }

    @Test
    void 윤일_퇴직은_평년에_2월_28일() {
        LegalRateService rates = mock(LegalRateService.class);
        when(rates.getEffective(any(LegalRateItem.class), any())).thenReturn(row("2.00"));
        assertThat(new RecordRetentionCalculator(rates).retentionUntil(LocalDate.of(2028, 2, 29)))
                .isEqualTo(LocalDate.of(2030, 2, 28));
    }

    @Test
    void 정수가_아니거나_0_이하면_거부한다() {
        for (String v : new String[] {"2.5", "0", "-1"}) {
            LegalRateService rates = mock(LegalRateService.class);
            when(rates.getEffective(any(LegalRateItem.class), any())).thenReturn(row(v));
            assertThatThrownBy(() -> new RecordRetentionCalculator(rates).retentionUntil(LocalDate.of(2026, 1, 1)))
                    .as(v).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void 기준값이_없으면_기본값_없이_예외() {
        LegalRateService rates = mock(LegalRateService.class);
        LegalRateException missing = mock(LegalRateException.class);
        when(rates.getEffective(any(LegalRateItem.class), any())).thenThrow(missing);
        assertThatThrownBy(() -> new RecordRetentionCalculator(rates).retentionUntil(LocalDate.of(2026, 1, 1)))
                .isSameAs(missing);
    }
}
