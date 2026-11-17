package com.glassehr.familyleave;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.familyleave.FamilyLeavePeriods.MonthsDays;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 기간 길이 계산의 구조 검사(월 단위는 {@code DismissalNoticeChecker.lastDayOfMonths} 를 그대로 쓴다).
 * 월말·윤년 경계의 법령 정답은 tests/legal-fixtures/ 로 받아 따로 검사한다.
 */
class FamilyLeavePeriodsTest {

    private static final LocalDate D = LocalDate.of(2027, 3, 1);

    @Test
    void 월_단위로_딱_맞으면_남는_일수가_없다() {
        assertThat(FamilyLeavePeriods.length(D, D.plusMonths(3).minusDays(1))).isEqualTo(new MonthsDays(3, 0));
    }

    @Test
    void 월을_채우지_못한_날은_일수로_남긴다() {
        assertThat(FamilyLeavePeriods.length(D, D.plusDays(9))).isEqualTo(new MonthsDays(0, 10));
        assertThat(FamilyLeavePeriods.length(D, D.plusMonths(2).plusDays(4))).isEqualTo(new MonthsDays(2, 5));
        assertThat(FamilyLeavePeriods.length(D, D)).isEqualTo(new MonthsDays(0, 1));
    }

    @Test
    void 끝이_시작보다_빠르면_0() {
        assertThat(FamilyLeavePeriods.length(D, D.minusDays(1))).isEqualTo(MonthsDays.ZERO);
    }

    @Test
    void 남는_일수가_있으면_한도와_같은_개월도_넘는다() {
        assertThat(new MonthsDays(3, 0).exceeds(3)).isFalse();
        assertThat(new MonthsDays(3, 1).exceeds(3)).isTrue();
        assertThat(new MonthsDays(4, 0).exceeds(3)).isTrue();
    }

    @Test
    void 연도별_일수는_그해에_든_날만_센다() {
        LocalDate s = LocalDate.of(2027, 12, 21);
        LocalDate e = LocalDate.of(2028, 1, 10);
        assertThat(FamilyLeavePeriods.daysInYear(s, e, 2027)).isEqualTo(11);
        assertThat(FamilyLeavePeriods.daysInYear(s, e, 2028)).isEqualTo(10);
        assertThat(FamilyLeavePeriods.daysInYear(s, e, 2026)).isZero();
    }
}
