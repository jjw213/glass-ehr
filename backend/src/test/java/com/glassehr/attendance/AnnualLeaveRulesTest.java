package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.attendance.AnnualLeaveRules.Accrual;
import com.glassehr.attendance.AnnualLeaveRules.Attendance;
import com.glassehr.attendance.AnnualLeaveRules.DateRange;
import com.glassehr.attendance.AnnualLeaveRules.Kind;
import com.glassehr.attendance.AnnualLeaveRules.Rates;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * 계산 구조(기간 나누기·개근·출근율·가산·한도·소멸일)만 검사한다.
 * 기준값은 일부러 법정 값과 다른 시험용 값이다 — 법정 정답 검사는 tests/legal-fixtures/ 의 연차 fixture로 한다(#33 데이터 요청).
 */
class AnnualLeaveRulesTest {

    /** 시험용 값: 기본 10일, 출근율 50%, 개근 달마다 2일, 최초 1년 넘는 3년마다 2일 가산, 한도 14일. */
    private static final Rates TEST_RATES = new Rates(10, BigDecimal.valueOf(50), 2, 1, 3, 2, 14);
    private static final Function<LocalDate, Rates> RATES = d -> TEST_RATES;

    private static Set<LocalDate> weekdays(LocalDate from, LocalDate to) {
        Set<LocalDate> s = new HashSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                s.add(d);
            }
        }
        return s;
    }

    private static Attendance perfect(LocalDate from, LocalDate to) {
        return new Attendance(weekdays(from, to), Set.of(), List.of());
    }

    @Test
    void 첫해_개근한_달마다_발생하고_최초_1년_끝까지_사용() {
        LocalDate hire = LocalDate.of(2025, 1, 1);
        List<Accrual> r = AnnualLeaveRules.accruals(hire, LocalDate.of(2026, 1, 1), null, perfect(hire, LocalDate.of(2026, 1, 1)), RATES);

        List<Accrual> monthly = r.stream().filter(a -> a.kind() == Kind.MONTHLY_FIRST_YEAR).toList();
        assertThat(monthly).hasSize(11);
        assertThat(monthly.get(0).grantDate()).isEqualTo(LocalDate.of(2025, 2, 1));
        assertThat(monthly.get(10).grantDate()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(monthly).allSatisfy(a -> {
            assertThat(a.days()).isEqualTo(2);
            assertThat(a.lastUsableDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        });
        Accrual annual = r.get(r.size() - 1);
        assertThat(annual.kind()).isEqualTo(Kind.ANNUAL);
        assertThat(annual.grantDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(annual.days()).isEqualTo(10);
        assertThat(annual.period()).isEqualTo(new DateRange(hire, LocalDate.of(2025, 12, 31)));
        assertThat(annual.lastUsableDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(annual.legalBasis()).contains("제60조 제1항");
    }

    @Test
    void 월말_입사는_해당일이_없는_달의_말일로_한_달이_끝난다() {
        LocalDate hire = LocalDate.of(2025, 1, 31);
        List<Accrual> r = AnnualLeaveRules.accruals(hire, LocalDate.of(2025, 3, 1), null, perfect(hire, LocalDate.of(2025, 3, 1)), RATES);
        assertThat(r).singleElement().satisfies(a -> {
            assertThat(a.period()).isEqualTo(new DateRange(hire, LocalDate.of(2025, 2, 28)));
            assertThat(a.grantDate()).isEqualTo(LocalDate.of(2025, 3, 1));
            assertThat(a.lastUsableDate()).isEqualTo(LocalDate.of(2026, 1, 30));
        });
        assertThat(AnnualLeaveRules.accruals(hire, LocalDate.of(2025, 2, 28), null, perfect(hire, LocalDate.of(2025, 2, 28)), RATES)).isEmpty();
    }

    @Test
    void 윤년_2월_29일_입사의_1년은_다음해_2월_28일에_끝난다() {
        LocalDate hire = LocalDate.of(2024, 2, 29);
        List<Accrual> r = AnnualLeaveRules.accruals(hire, LocalDate.of(2025, 3, 1), null, perfect(hire, LocalDate.of(2025, 3, 1)), RATES);
        Accrual annual = r.get(r.size() - 1);
        assertThat(annual.kind()).isEqualTo(Kind.ANNUAL);
        assertThat(annual.grantDate()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(r).filteredOn(a -> a.kind() == Kind.MONTHLY_FIRST_YEAR)
                .allSatisfy(a -> assertThat(a.lastUsableDate()).isEqualTo(LocalDate.of(2025, 2, 28)));
    }

    @Test
    void 결근한_달은_빠지고_출근으로_보는_기간의_결근은_출근으로_센다() {
        LocalDate hire = LocalDate.of(2025, 1, 1);
        LocalDate asOf = LocalDate.of(2025, 12, 1);
        Set<LocalDate> scheduled = weekdays(hire, asOf);
        LocalDate marchAbsent = LocalDate.of(2025, 3, 3);
        LocalDate mayAbsent = LocalDate.of(2025, 5, 6);
        Attendance a = new Attendance(scheduled, Set.of(marchAbsent, mayAbsent),
                List.of(new DateRange(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31))));

        List<Accrual> r = AnnualLeaveRules.accruals(hire, asOf, null, a, RATES);
        assertThat(r).hasSize(10);
        assertThat(r).extracting(Accrual::grantDate).doesNotContain(LocalDate.of(2025, 4, 1)).contains(LocalDate.of(2025, 6, 1));
    }

    @Test
    void 출근율_미달인_해에는_그해_개근한_달마다_발생한다() {
        LocalDate hire = LocalDate.of(2024, 1, 1);
        LocalDate asOf = LocalDate.of(2026, 1, 1);
        Set<LocalDate> scheduled = weekdays(hire, asOf);
        // 둘째 해 1~7월 전부 결근 → 출근율 50% 미만, 8~12월 5개월 개근.
        Set<LocalDate> absent = weekdays(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 7, 31));
        List<Accrual> r = AnnualLeaveRules.accruals(hire, asOf, null, new Attendance(scheduled, absent, List.of()), RATES);

        Accrual last = r.get(r.size() - 1);
        assertThat(last.kind()).isEqualTo(Kind.MONTHLY_LOW_ATTENDANCE);
        assertThat(last.grantDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(last.days()).isEqualTo(5 * 2);
        assertThat(last.lastUsableDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(r.get(r.size() - 2).kind()).isEqualTo(Kind.ANNUAL);
    }

    @Test
    void 출근율은_기준과_같으면_충족한다() {
        LocalDate hire = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);
        List<LocalDate> days = weekdays(hire, end).stream().sorted().toList();
        Set<LocalDate> absentHalf = new HashSet<>(days.subList(0, days.size() / 2));
        Attendance a = new Attendance(Set.copyOf(days.subList(0, days.size() / 2 * 2)), absentHalf, List.of());
        List<Accrual> r = AnnualLeaveRules.accruals(hire, LocalDate.of(2026, 1, 1), null, a, RATES);
        assertThat(r.get(r.size() - 1).kind()).isEqualTo(Kind.ANNUAL);
    }

    @Test
    void 가산은_최초_1년을_넘는_주기마다이고_한도를_넘지_않는다() {
        assertThat(AnnualLeaveRules.annualDays(1, TEST_RATES)).isEqualTo(10);
        assertThat(AnnualLeaveRules.annualDays(3, TEST_RATES)).isEqualTo(10);
        assertThat(AnnualLeaveRules.annualDays(4, TEST_RATES)).isEqualTo(12);
        assertThat(AnnualLeaveRules.annualDays(7, TEST_RATES)).isEqualTo(14);
        assertThat(AnnualLeaveRules.annualDays(10, TEST_RATES)).isEqualTo(14);
    }

    @Test
    void 기간이_끝난_다음날_근로관계가_없으면_발생하지_않는다() {
        LocalDate hire = LocalDate.of(2025, 1, 1);
        Attendance a = perfect(hire, LocalDate.of(2026, 1, 1));
        List<Accrual> retiredBefore = AnnualLeaveRules.accruals(hire, LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31), a, RATES);
        assertThat(retiredBefore).noneMatch(x -> x.kind() == Kind.ANNUAL).hasSize(11);
        List<Accrual> retiredOn = AnnualLeaveRules.accruals(hire, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), a, RATES);
        assertThat(retiredOn).anyMatch(x -> x.kind() == Kind.ANNUAL);
    }

    @Test
    void 기준값은_발생일_기준으로_조회한다() {
        LocalDate hire = LocalDate.of(2024, 1, 1);
        Rates later = new Rates(12, BigDecimal.valueOf(50), 2, 1, 3, 2, 14);
        Function<LocalDate, Rates> byDate = d -> d.isBefore(LocalDate.of(2025, 6, 1)) ? TEST_RATES : later;
        List<Accrual> r = AnnualLeaveRules.accruals(hire, LocalDate.of(2026, 1, 1), null, perfect(hire, LocalDate.of(2026, 1, 1)), byDate);
        assertThat(r).filteredOn(a -> a.kind() == Kind.ANNUAL).extracting(Accrual::days).containsExactly(10, 12);
    }

    @Test
    void 잘못된_입력은_거부한다() {
        LocalDate hire = LocalDate.of(2025, 1, 1);
        Attendance a = perfect(hire, hire);
        assertThatThrownBy(() -> AnnualLeaveRules.accruals(hire, hire.minusDays(1), null, a, RATES))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Attendance(Set.of(), Set.of(hire), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnnualLeaveRules.accruals(hire, LocalDate.of(2026, 1, 1), null,
                new Attendance(Set.of(), Set.of(), List.of()), RATES))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("소정근로일");
    }
}
