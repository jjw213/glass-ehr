package com.glassehr.legalrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** PERCENT 저장 규칙의 변환 동작 테스트. 값은 변환 확인용 가짜 값이며 법정 수치가 아니다. */
class LegalRateRowTest {

    private static LegalRateRow row(String value, String unit) {
        return new LegalRateRow("TEST_ITEM", LocalDate.parse("2026-01-01"), null,
                new BigDecimal(value), unit, "테스트용 근거", "https://example.invalid/test");
    }

    @Test
    void 퍼센트는_소수점_이동으로_비율이_된다() {
        assertThat(row("12.345", LegalRateUnit.PERCENT).toRatio()).isEqualTo(new BigDecimal("0.12345"));
        assertThat(row("0.5", LegalRateUnit.PERCENT).toRatio()).isEqualTo(new BigDecimal("0.005"));
        assertThat(row("100", LegalRateUnit.PERCENT).toRatio()).isEqualByComparingTo("1");
    }

    @Test
    void 퍼센트가_아닌_항목은_비율_변환을_거부한다() {
        assertThatThrownBy(() -> row("1000", "TEST_UNIT").toRatio())
                .isInstanceOf(IllegalStateException.class);
    }
}
