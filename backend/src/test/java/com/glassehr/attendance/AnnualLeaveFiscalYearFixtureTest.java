package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.attendance.AnnualLeaveFiscalYear.Accrual;
import com.glassehr.attendance.AnnualLeaveFiscalYear.Kind;
import com.glassehr.attendance.AnnualLeaveFiscalYear.LeaveDays;
import com.glassehr.attendance.AnnualLeaveFiscalYear.Settlement;
import com.glassehr.attendance.AnnualLeaveRules.Attendance;
import java.io.IOException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 회계연도 기준 연차와 퇴직 정산(근로기준법 제60조, 고용노동부 근기68207-620·근로기준과-5802)을
 * 사람이 승인한 정답 {@code tests/legal-fixtures/annual-leave-fiscal-year.csv}와 시드 기준값으로 대조한다.
 *
 * <p>비교 범위: README에 따라 최초 1년 월 단위분은 전체 내역(최초 1년 끝까지)이고 회계연도분은 기준일까지다. 그래서 재직자
 * 행은 기준일과 최초 1년 마지막 날 중 늦은 날까지 계산한다(예: '둘째해_비례부여_소수원값'은 기준일 2026-01-01이지만
 * 2026-04-10 발생분까지 적혀 있고 입사일 기준 총일수는 11). 퇴직자 행은 퇴직일(=기준일)을 마지막 근무일로 본다.
 * 소정근로일은 평일(입사한 회계연도 시작일부터), 결근 없음.
 */
class AnnualLeaveFiscalYearFixtureTest {

    private static final String FIXTURE = "tests/legal-fixtures/annual-leave-fiscal-year.csv";

    static Stream<Arguments> fixtureRows() throws IOException {
        List<List<String>> rows = AnnualLeaveFixtures.readCsv(FIXTURE);
        assertThat(rows.get(0)).containsExactly("사례", "입사일", "회계연도시작월일", "기준일", "기대발생내역", "비례연차산식",
                "비례분자_출근소정근로일수", "비례분모_연간소정근로일수", "비례연차정확값", "소수처리", "퇴직일", "입사일기준총일수",
                "회계연도기준총일수", "기대정산차이_입사일기준빼기회계연도기준", "출처", "검증일");
        assertThat(rows.stream().skip(1).map(c -> c.get(0))).contains("1월1일직전입사", "1월1일직후입사",
                "둘째해_비례부여_소수원값", "4월1일회계연도_직후입사", "퇴직정산_입사일기준많음", "퇴직정산_같음", "퇴직정산_회계연도기준많음");
        return rows.stream().skip(1).map(c -> Arguments.of(c.get(0), c));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureRows")
    void 회계연도_기준_발생내역과_정산이_fixture_정답과_같다(String name, List<String> c) throws IOException {
        LocalDate hire = LocalDate.parse(c.get(1));
        MonthDay fyStart = MonthDay.parse("--" + c.get(2));
        LocalDate lastWorkDay = c.get(10).isBlank() ? null : LocalDate.parse(c.get(10));
        LocalDate asOf = LocalDate.parse(c.get(3));
        if (lastWorkDay == null) {
            LocalDate firstYearEnd = AnnualLeaveRules.periodEnd(hire, 12);
            asOf = firstYearEnd.isAfter(asOf) ? firstYearEnd : asOf;
        }
        LocalDate hireFyStart = fyStart.atYear(hire.getYear()).isAfter(hire) ? fyStart.atYear(hire.getYear() - 1)
                : fyStart.atYear(hire.getYear());
        Attendance attendance = new Attendance(AnnualLeaveFixtures.weekdays(hireFyStart, asOf), Set.of(), List.of());
        var rates = AnnualLeaveFixtures.seededRates();

        List<Accrual> accruals = AnnualLeaveFiscalYear.accruals(hire, fyStart, asOf, lastWorkDay, attendance, rates);
        assertThat(accruals.stream().map(AnnualLeaveFiscalYearFixtureTest::format).sorted().toList()).as(name + " 발생내역")
                .containsExactlyElementsOf(AnnualLeaveFixtures.entries(c.get(4)).stream()
                        .map(AnnualLeaveFiscalYearFixtureTest::normalize).sorted().toList());

        List<Accrual> prorated = accruals.stream().filter(a -> a.kind() == Kind.PRORATED).toList();
        if (c.get(8).isBlank()) {
            assertThat(prorated).as(name + " 비례분 없음").isEmpty();
        } else {
            // 근기68207-620: 15 × 출근한 소정근로일수 ÷ 연간 소정근로일수(분자·분모 열과 정확값이 서로 맞는지도 확인).
            LeaveDays exact = AnnualLeaveFixtures.fraction(c.get(8));
            assertThat(new LeaveDays(rates.apply(prorated.get(0).grantDate()).baseDays() * Long.parseLong(c.get(6)),
                    Long.parseLong(c.get(7)))).as(name + " 비례 산식").isEqualTo(exact);
            assertThat(prorated).singleElement().extracting(Accrual::days).as(name + " 비례분").isEqualTo(exact);
        }

        Settlement s = AnnualLeaveFiscalYear.settlement(hire, fyStart, asOf, lastWorkDay, attendance, rates);
        assertThat(s.hireDateTotal()).as(name + " 입사일 기준 총일수").isEqualTo(AnnualLeaveFixtures.fraction(c.get(11)));
        assertThat(s.fiscalYearTotal()).as(name + " 회계연도 기준 총일수").isEqualTo(AnnualLeaveFixtures.fraction(c.get(12)));
        LeaveDays diff = AnnualLeaveFixtures.fraction(c.get(13));
        assertThat(s.difference()).as(name + " 정산 차이").isEqualTo(diff);
        // README: 음수는 차감·환수하지 않는다.
        assertThat(s.additionalDays()).as(name + " 추가 정산").isEqualTo(diff.signum() > 0 ? diff : LeaveDays.ZERO);
    }

    /** 일수는 값으로 비교한다(fixture의 {@code 840/87}은 기약분수가 아니고 {@code 280/29}와 같은 값). */
    private static String normalize(String entry) {
        int i = entry.lastIndexOf(':');
        return entry.substring(0, i + 1) + AnnualLeaveFixtures.fraction(entry.substring(i + 1));
    }

    private static String format(Accrual a) {
        String kind = a.kind() == Kind.PRORATED ? "비례연차" : a.kind().name();
        return a.grantDate() + ":" + kind + ":" + a.days();
    }
}
