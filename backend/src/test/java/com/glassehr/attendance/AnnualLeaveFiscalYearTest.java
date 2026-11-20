package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.attendance.AnnualLeaveFiscalYear.Accrual;
import com.glassehr.attendance.AnnualLeaveFiscalYear.Kind;
import com.glassehr.attendance.AnnualLeaveFiscalYear.LeaveDays;
import com.glassehr.attendance.AnnualLeaveFiscalYear.UnresolvedIncrementException;
import com.glassehr.attendance.AnnualLeaveRules.Attendance;
import com.glassehr.attendance.AnnualLeaveRules.Rates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * 회계연도 기준 계산 구조만 검사한다. 기준값은 일부러 법정 값과 다른 시험용 값이다 — 법정 정답은
 * {@link AnnualLeaveFiscalYearFixtureTest}(tests/legal-fixtures/annual-leave-fiscal-year.csv)로 판정한다.
 */
class AnnualLeaveFiscalYearTest {

    /** 시험용 값: 기본 10일, 출근율 50%, 개근 달마다 2일, 최초 1년 넘는 3년마다 2일 가산, 한도 14일. */
    private static final Function<LocalDate, Rates> RATES = d -> new Rates(10, BigDecimal.valueOf(50), 2, 1, 3, 2, 14);
    private static final MonthDay JAN_1 = MonthDay.of(1, 1);

    private static Attendance attendance(LocalDate from, LocalDate to, Set<LocalDate> absent) {
        return new Attendance(AnnualLeaveFixtures.weekdays(from, to), absent, List.of());
    }

    private static List<Accrual> fiscalOnly(List<Accrual> all) {
        return all.stream().filter(a -> a.kind() != Kind.MONTHLY_FIRST_YEAR).toList();
    }

    @Test
    void 입사_회계연도_비례분은_출근한_소정근로일을_그해_소정근로일로_나눈다() {
        LocalDate hire = LocalDate.of(2025, 7, 1);
        Attendance a = attendance(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), Set.of(hire));
        long worked = AnnualLeaveFixtures.weekdays(hire, LocalDate.of(2025, 12, 31)).size() - 1L;
        long year = AnnualLeaveFixtures.weekdays(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)).size();

        List<Accrual> fy = fiscalOnly(AnnualLeaveFiscalYear.accruals(hire, JAN_1, LocalDate.of(2026, 1, 1), null, a, RATES));

        assertThat(fy).singleElement().satisfies(x -> {
            assertThat(x.kind()).isEqualTo(Kind.PRORATED);
            assertThat(x.grantDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(x.days()).isEqualTo(new LeaveDays(10 * worked, year));
            assertThat(x.lastUsableDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        });
    }

    @Test
    void 회계연도_시작일에_입사하면_비례분_없이_입사일_기준과_같다() {
        LocalDate hire = LocalDate.of(2025, 1, 1);
        List<Accrual> fy = fiscalOnly(AnnualLeaveFiscalYear.accruals(hire, JAN_1, LocalDate.of(2026, 1, 1), null,
                attendance(hire, LocalDate.of(2026, 1, 1), Set.of()), RATES));
        assertThat(fy).singleElement().satisfies(x -> {
            assertThat(x.kind()).isEqualTo(Kind.ANNUAL);
            assertThat(x.days()).isEqualTo(LeaveDays.of(10));
        });
    }

    @Test
    void 회계연도_출근율_미달이면_그해_개근한_달마다_한_건으로_발생한다() {
        LocalDate hire = LocalDate.of(2025, 1, 1);
        Set<LocalDate> absent = AnnualLeaveFixtures.weekdays(hire, LocalDate.of(2025, 7, 31));
        List<Accrual> fy = fiscalOnly(AnnualLeaveFiscalYear.accruals(hire, JAN_1, LocalDate.of(2026, 1, 1), null,
                attendance(hire, LocalDate.of(2026, 1, 1), absent), RATES));
        assertThat(fy).singleElement().satisfies(x -> {
            assertThat(x.kind()).isEqualTo(Kind.MONTHLY_LOW_ATTENDANCE);
            assertThat(x.days()).isEqualTo(LeaveDays.of(5 * 2)); // 8~12월 개근
        });
    }

    @Test
    void 회계연도_시작일_전에_퇴직하면_비례분이_없다() {
        LocalDate hire = LocalDate.of(2025, 7, 1);
        List<Accrual> fy = fiscalOnly(AnnualLeaveFiscalYear.accruals(hire, JAN_1, LocalDate.of(2026, 1, 1),
                LocalDate.of(2025, 12, 31), attendance(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), Set.of()), RATES));
        assertThat(fy).isEmpty();
    }

    @Test
    void 가산_연수_해석에_따라_일수가_갈리면_확정하지_않는다() {
        LocalDate hire = LocalDate.of(2025, 7, 1);
        Attendance a = attendance(LocalDate.of(2025, 1, 1), LocalDate.of(2029, 1, 1), Set.of());
        // 2028-01-01까지는 온전한 회계연도 2년·입사 회계연도 포함 3년 모두 가산 전이라 같다.
        assertThat(fiscalOnly(AnnualLeaveFiscalYear.accruals(hire, JAN_1, LocalDate.of(2028, 1, 1), null, a, RATES)))
                .extracting(Accrual::kind).containsExactly(Kind.PRORATED, Kind.ANNUAL, Kind.ANNUAL);
        assertThatThrownBy(() -> AnnualLeaveFiscalYear.accruals(hire, JAN_1, LocalDate.of(2029, 1, 1), null, a, RATES))
                .isInstanceOf(UnresolvedIncrementException.class).hasMessageContaining("2029-01-01");
    }

    @Test
    void 정산_차이가_음수면_추가_정산하지_않는다() {
        LocalDate hire = LocalDate.of(2025, 7, 1);
        var s = AnnualLeaveFiscalYear.settlement(hire, JAN_1, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 30),
                attendance(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 6, 30), Set.of()), RATES);
        assertThat(s.difference().signum()).isNegative();
        assertThat(s.additionalDays()).isEqualTo(LeaveDays.ZERO);
    }

    @Test
    void 분수는_기약분수로_더하고_뺀다() {
        assertThat(new LeaveDays(840, 87)).isEqualTo(new LeaveDays(280, 29));
        assertThat(LeaveDays.of(15).minus(new LeaveDays(990, 131))).hasToString("975/131");
        assertThat(new LeaveDays(3, -6)).hasToString("-1/2");
    }

    @Test
    void 회계연도_시작일로_2월_29일은_받지_않는다() {
        LocalDate hire = LocalDate.of(2025, 1, 1);
        assertThatThrownBy(() -> AnnualLeaveFiscalYear.accruals(hire, MonthDay.of(2, 29), hire, null,
                attendance(hire, hire, Set.of()), RATES)).isInstanceOf(IllegalArgumentException.class);
    }
}
