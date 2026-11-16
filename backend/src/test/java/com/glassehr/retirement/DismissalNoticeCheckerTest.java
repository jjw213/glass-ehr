package com.glassehr.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import com.glassehr.legalrate.LegalRateUnit;
import com.glassehr.retirement.DismissalNoticeChecker.Exemption;
import com.glassehr.retirement.DismissalNoticeChecker.NoticeCheck;
import com.glassehr.retirement.DismissalNoticeChecker.Result;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 해고 예고 확인 규칙(분기·경고·기준값 조회). 기준값은 일부러 법정값과 다른 가짜 값(예고 10일, 적용 제외 2개월)을 써서
 * 코드가 legal_rate 값을 따라가는지만 본다. 법정 일수·경계일 정답은 다루지 않는다
 * (tests/legal-fixtures/ 정답이 들어오면 별도 fixture 테스트로 검증 — docs/retirement-design.md 7장).
 */
class DismissalNoticeCheckerTest {

    private static final LocalDate HIRE = LocalDate.of(2025, 1, 10);
    private static final LocalDate DISMISS = LocalDate.of(2026, 3, 31);

    private final LegalRateService legalRates = mock(LegalRateService.class);
    private final DismissalNoticeChecker checker = new DismissalNoticeChecker(legalRates);

    private void value(LegalRateItem item, String unit, String v) {
        when(legalRates.findEffective(eq(item.code()), any())).thenReturn(Optional.of(new LegalRateRow(item.code(),
                LocalDate.of(2000, 1, 1), null, new BigDecimal(v), unit, "근로기준법 제26조", null)));
    }

    private void none(LegalRateItem item) {
        when(legalRates.findEffective(eq(item.code()), any())).thenReturn(Optional.empty());
    }

    @Test
    void 예고_일수가_기준_이상이면_OK() {
        value(LegalRateItem.DISMISSAL_NOTICE_DAYS, LegalRateUnit.DAYS, "10");
        Result r = checker.check(HIRE, DISMISS.minusDays(10), DISMISS, null);
        assertThat(r.check()).isEqualTo(NoticeCheck.OK);
        assertThat(r.noticeDays()).isEqualTo(10);
        assertThat(r.requiredDays()).isEqualTo(10);
        assertThat(r.shortfallDays()).isNull();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void 예고_일수가_모자라면_SHORT와_미달_일수_경고() {
        value(LegalRateItem.DISMISSAL_NOTICE_DAYS, LegalRateUnit.DAYS, "10");
        Result r = checker.check(HIRE, DISMISS.minusDays(7), DISMISS, null);
        assertThat(r.check()).isEqualTo(NoticeCheck.SHORT);
        assertThat(r.shortfallDays()).isEqualTo(3);
        assertThat(r.warnings()).singleElement().asString().contains("3일 모자라요").contains("제26조");
    }

    @Test
    void 당일_예고는_0일() {
        value(LegalRateItem.DISMISSAL_NOTICE_DAYS, LegalRateUnit.DAYS, "10");
        Result r = checker.check(HIRE, DISMISS, DISMISS, null);
        assertThat(r.noticeDays()).isZero();
        assertThat(r.shortfallDays()).isEqualTo(10);
    }

    @Test
    void 기준값은_해고일_기준으로_조회한다() {
        when(legalRates.findEffective(eq(LegalRateItem.DISMISSAL_NOTICE_DAYS.code()), eq(DISMISS)))
                .thenReturn(Optional.of(new LegalRateRow("DISMISSAL_NOTICE_DAYS", DISMISS, null, new BigDecimal("5"),
                        LegalRateUnit.DAYS, "근로기준법 제26조", null)));
        when(legalRates.findEffective(eq(LegalRateItem.DISMISSAL_NOTICE_DAYS.code()), eq(DISMISS.minusDays(1))))
                .thenReturn(Optional.empty());
        assertThat(checker.check(HIRE, DISMISS.minusDays(5), DISMISS, null).check()).isEqualTo(NoticeCheck.OK);
        assertThat(checker.check(HIRE, DISMISS.minusDays(6), DISMISS.minusDays(1), null).check())
                .isEqualTo(NoticeCheck.UNKNOWN);
    }

    @Test
    void 기준값이_없으면_UNKNOWN이고_기본값을_쓰지_않는다() {
        none(LegalRateItem.DISMISSAL_NOTICE_DAYS);
        Result r = checker.check(HIRE, DISMISS.minusDays(100), DISMISS, null);
        assertThat(r.check()).isEqualTo(NoticeCheck.UNKNOWN);
        assertThat(r.requiredDays()).isNull();
        assertThat(r.warnings()).singleElement().asString().contains("DISMISSAL_NOTICE_DAYS");
    }

    @Test
    void 근속_적용제외는_기준_개월_미만이면_EXEMPT() {
        value(LegalRateItem.DISMISSAL_NOTICE_DAYS, LegalRateUnit.DAYS, "10");
        value(LegalRateItem.DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS, LegalRateUnit.MONTHS, "2");
        LocalDate hire = LocalDate.of(2026, 2, 1);
        // 2개월이 되는 날의 전날 = 3월 31일. 그보다 앞이면 미만.
        Result r = checker.check(hire, LocalDate.of(2026, 3, 30), LocalDate.of(2026, 3, 30), Exemption.SHORT_SERVICE);
        assertThat(r.check()).isEqualTo(NoticeCheck.EXEMPT);
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void 근속_적용제외를_골랐지만_기준_개월_이상이면_예고기간으로_다시_본다() {
        value(LegalRateItem.DISMISSAL_NOTICE_DAYS, LegalRateUnit.DAYS, "10");
        value(LegalRateItem.DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS, LegalRateUnit.MONTHS, "2");
        LocalDate hire = LocalDate.of(2026, 2, 1);
        Result r = checker.check(hire, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 31), Exemption.SHORT_SERVICE);
        assertThat(r.check()).isEqualTo(NoticeCheck.SHORT);
        assertThat(r.warnings()).hasSize(2);
        assertThat(r.warnings().get(0)).contains("단서 제1호 적용 제외가 아니에요");
    }

    @Test
    void 근속_기준값이_없으면_UNKNOWN() {
        value(LegalRateItem.DISMISSAL_NOTICE_DAYS, LegalRateUnit.DAYS, "10");
        none(LegalRateItem.DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS);
        Result r = checker.check(HIRE, DISMISS, DISMISS, Exemption.SHORT_SERVICE);
        assertThat(r.check()).isEqualTo(NoticeCheck.UNKNOWN);
        assertThat(r.warnings()).singleElement().asString().contains("DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS");
    }

    @Test
    void 부득이한_사유와_근로자_귀책은_기준값_없이_EXEMPT와_증빙_안내() {
        none(LegalRateItem.DISMISSAL_NOTICE_DAYS);
        assertThat(checker.check(HIRE, DISMISS, DISMISS, Exemption.UNAVOIDABLE).warnings())
                .singleElement().asString().contains("제2호");
        Result r = checker.check(HIRE, DISMISS, DISMISS, Exemption.EMPLOYEE_FAULT);
        assertThat(r.check()).isEqualTo(NoticeCheck.EXEMPT);
        assertThat(r.warnings()).singleElement().asString().contains("제3호");
    }

    @Test
    void 기준값_단위가_다르거나_정수가_아니면_오류() {
        value(LegalRateItem.DISMISSAL_NOTICE_DAYS, LegalRateUnit.MONTHS, "1");
        assertThatThrownBy(() -> checker.check(HIRE, DISMISS, DISMISS, null)).isInstanceOf(IllegalStateException.class);
        value(LegalRateItem.DISMISSAL_NOTICE_DAYS, LegalRateUnit.DAYS, "10.5");
        assertThatThrownBy(() -> checker.check(HIRE, DISMISS, DISMISS, null)).isInstanceOf(IllegalStateException.class);
    }
}
