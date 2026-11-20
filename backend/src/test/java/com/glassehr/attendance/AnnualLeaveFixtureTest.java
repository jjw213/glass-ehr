package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.attendance.AnnualLeaveRules.Accrual;
import com.glassehr.attendance.AnnualLeaveRules.Attendance;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 입사일 기준 연차 발생(근로기준법 제60조 제1항·제2항·제4항·제6항·제7항, 민법 제157조·제160조, 법률 제17185호 부칙 제2조)을
 * 사람이 승인한 정답 {@code tests/legal-fixtures/annual-leave.csv}와 시드 {@code legal_rate_hr_current.sql} 기준값으로 대조한다.
 * README 계약: 기대발생내역은 입사일부터 기준일까지 발생한 전체 내역이다.
 */
class AnnualLeaveFixtureTest {

    private static final String FIXTURE = "tests/legal-fixtures/annual-leave.csv";

    static Stream<Arguments> fixtureRows() throws IOException {
        List<List<String>> rows = AnnualLeaveFixtures.readCsv(FIXTURE);
        assertThat(rows.get(0)).hasSize(10).startsWith("사례", "입사일", "기준일", "마지막근무일(빈칸=재직)", "결근일(;구분)");
        assertThat(rows.get(0).subList(8, 10)).containsExactly("출처", "검증일");
        return rows.stream().skip(1).map(c -> Arguments.of(c.get(0), c));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureRows")
    void 입사일_기준_발생내역이_fixture_정답과_같다(String name, List<String> c) throws IOException {
        LocalDate hire = LocalDate.parse(c.get(1));
        LocalDate asOf = LocalDate.parse(c.get(2));
        LocalDate lastWorkDay = c.get(3).isBlank() ? null : LocalDate.parse(c.get(3));
        Set<LocalDate> absent = AnnualLeaveFixtures.dates(c.get(4));
        Attendance attendance = new Attendance(AnnualLeaveFixtures.scheduledDays(c.get(6), hire, asOf, absent), absent,
                AnnualLeaveFixtures.ranges(c.get(5)));

        List<String> actual = AnnualLeaveRules.accruals(hire, asOf, lastWorkDay, attendance, AnnualLeaveFixtures.seededRates())
                .stream().map(AnnualLeaveFixtureTest::format).sorted().toList();

        assertThat(actual).as(name).containsExactlyElementsOf(AnnualLeaveFixtures.entries(c.get(7)));
    }

    @Test
    void 필수_사례가_fixture에_있다() throws IOException {
        assertThat(AnnualLeaveFixtures.readCsv(FIXTURE).stream().skip(1).map(c -> c.get(0)))
                .contains("1년미만_월개근_최대11일", "월중결근_해당월제외", "1월31일입사_월말기간만료", "윤년2월29일입사_평년1년만료",
                        "1년만근_15일", "출근율_정확히80퍼센트", "출근율_80퍼센트미만", "최초1년_출근율미달_1년시점없음",
                        "2년차_출근율미달_개근월만발생", "3년차_16일", "5년차_17일", "21년차_25일", "23년차_25일_상한유지",
                        "365일근로후퇴직_연차미발생", "월단위_다음날재직없음_미발생", "업무상재해휴업_출근간주",
                        "출산전후휴가_출근간주", "육아휴직_출근간주", "최초1년월단위분_제7항소멸",
                        "제60조제7항_2020년개정_시행일전후소멸경계");
    }

    private static String format(Accrual a) {
        return a.grantDate() + ":" + a.kind() + ":" + a.days() + ":" + a.lastUsableDate();
    }
}
