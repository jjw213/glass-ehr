package com.glassehr.attendance;

import static com.glassehr.attendance.WorkPatternLimitsFixtureTest.parseCsvLine;
import static com.glassehr.attendance.WorkPatternLimitsFixtureTest.repoFile;
import static com.glassehr.attendance.WorkPatternLimitsFixtureTest.seedRows;
import static com.glassehr.attendance.WorkPatternLimitsFixtureTest.seededRates;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 근로기준법 제2조 제1항 제9호·제18조 제1항·제3항, 시행령 제9조 제1항 [별표 2] 제4호나목·제5호나목(#158)을
 * tests/legal-fixtures/part-time-worker-type.csv, part-time-proportional-leave.csv 정답(Codex 법령 데이터 PR #163,
 * 사람 승인)으로 검증한다. 기준값은 seed/legal_rate_part_time.sql·legal_rate_work_pattern.sql·legal_rate_hr_current.sql
 * 의 insert_period 행을 적용일 기준으로 읽는다({@link WorkerTypeLegalRules#asOf}).
 */
class WorkerTypeFixtureTest {

    private static final String TYPE_FIXTURE = "tests/legal-fixtures/part-time-worker-type.csv";
    private static final String LEAVE_FIXTURE = "tests/legal-fixtures/part-time-proportional-leave.csv";
    private static final List<String> SEEDS = List.of(
            "backend/src/main/resources/db/oracle/seed/legal_rate_part_time.sql",
            "backend/src/main/resources/db/oracle/seed/legal_rate_work_pattern.sql",
            "backend/src/main/resources/db/oracle/seed/legal_rate_hr_current.sql");
    private static final Pattern LEADING_INT = Pattern.compile("^\\s*(\\d+)");

    private static LegalRateService rates() throws IOException {
        List<LegalRateRow> rows = new ArrayList<>();
        for (String s : SEEDS) {
            rows.addAll(seedRows(s));
        }
        return seededRates(rows);
    }

    /** 시간(소수 허용) → 분. 분 미만이 남으면 fixture 가 잘못된 것이므로 실패. */
    private static int minutes(String hours) {
        return new BigDecimal(hours.strip()).multiply(BigDecimal.valueOf(60)).intValueExact();
    }

    private static List<List<String>> rows(String fixture, String... header) throws IOException {
        List<String> lines = Files.readAllLines(repoFile(fixture), StandardCharsets.UTF_8);
        assertThat(parseCsvLine(lines.get(0))).containsExactly(header);
        return lines.stream().skip(1).filter(l -> !l.isBlank()).map(WorkPatternLimitsFixtureTest::parseCsvLine).toList();
    }

    static Stream<Arguments> typeRows() throws IOException {
        return rows(TYPE_FIXTURE, "사례", "기준일", "4주간 주별 소정근로시간(;구분)", "통상근로자 주 소정근로시간",
                "기대근로자유형(FULL_TIME/PART_TIME/ULTRA_SHORT)", "기대주휴적용(Y/N)", "출처", "검증일").stream()
                .map(c -> Arguments.of(c.get(0), LocalDate.parse(c.get(1)),
                        Arrays.stream(c.get(2).split(";")).map(WorkerTypeFixtureTest::minutes).toList(),
                        minutes(c.get(3)), WorkerType.valueOf(c.get(4)), "Y".equals(c.get(5))));
    }

    @ParameterizedTest(name = "{0} → {4}")
    @MethodSource("typeRows")
    void 근로자_유형_fixture(String name, LocalDate date, List<Integer> weekly, int fullTime, WorkerType expected,
            boolean holidayApplies) throws IOException {
        WorkerTypeRules.Thresholds t = new WorkerTypeLegalRules(rates()).asOf(date);
        assertThat(weekly).as("4주간 주별 시간").hasSize(t.averageWeeks());
        WorkerType type = WorkerTypeRules.classify(weekly, fullTime, t);
        assertThat(type).as(name).isEqualTo(expected);
        assertThat(type.weeklyHolidayAndAnnualLeaveApply()).as(name + " 주휴").isEqualTo(holidayApplies);
    }

    static Stream<Arguments> leaveRows() throws IOException {
        return rows(LEAVE_FIXTURE, "사례", "기준일", "단시간 주 소정근로시간", "통상근로자 주 소정근로시간",
                "단시간 1일 소정근로시간", "통상근로자 연차일수", "기대연차시간(분 단위 정수 또는 정확한 분수와 절사 규칙 명시)",
                "기대주휴시간(분)", "출처", "검증일").stream()
                .map(c -> {
                    Matcher m = LEADING_INT.matcher(c.get(6));
                    assertThat(m.find()).as(c.get(0) + " 기대연차시간").isTrue();
                    return Arguments.of(c.get(0), LocalDate.parse(c.get(1)), minutes(c.get(2)), minutes(c.get(3)),
                            minutes(c.get(4)), Integer.parseInt(c.get(5).strip()), Integer.parseInt(m.group(1)),
                            Integer.parseInt(c.get(7).strip()));
                });
    }

    @ParameterizedTest(name = "{0} → 연차 {6}분, 주휴 {7}분")
    @MethodSource("leaveRows")
    void 비례_연차_주휴_fixture(String name, LocalDate date, int weekly, int fullTime, int partDaily, int leaveDays,
            int expectedLeave, int expectedHoliday) throws IOException {
        WorkerTypeRules.Thresholds t = new WorkerTypeLegalRules(rates()).asOf(date);
        WorkerType type = WorkerTypeRules.classify(weekly, fullTime, t);
        assertThat(type).as(name).isEqualTo(WorkerType.PART_TIME);
        assertThat(WorkerTypeRules.proportionalAnnualLeaveMinutes(type, leaveDays, weekly, fullTime, t))
                .as(name + " 연차").isEqualTo(expectedLeave);
        // 통상근로자 1주 소정근로일수 = 통상 1주 소정근로시간 ÷ 1일 소정근로시간(제50조 제2항 기준근로시간)
        int fullTimeDays = fullTime / t.annualLeaveHourMinutes();
        int holiday = WorkerTypeRules.paidHolidayMinutes(type, weekly, fullTimeDays);
        assertThat(holiday).as(name + " 주휴").isEqualTo(expectedHoliday).isEqualTo(partDaily);
    }

    /** 근무유형이 없는 법인: 통상근로자 기준은 제50조 기준근로시간. fixture 첫 사례(주 20시간·15일)와 같아야 한다. */
    @Test
    void 화면_안내는_fixture_사례와_같다() throws IOException {
        LocalDate d = LocalDate.of(2026, 11, 26);
        LegalRateService rates = rates();
        WorkPatternRepository patterns = mock(WorkPatternRepository.class);
        when(patterns.findByEnterCdOrderByPatternCodeAsc("C001")).thenReturn(List.of());
        WorkerTypeService s = new WorkerTypeService(patterns, new WorkerTypeLegalRules(rates), rates);

        WorkerTypeService.WorkerTypeView pt = s.describe("C001", 1200, d);
        assertThat(pt.workerType()).isEqualTo(WorkerType.PART_TIME);
        assertThat(pt.fullTimeWeeklyMinutes()).isEqualTo(2400);
        assertThat(pt.baseLeaveDays()).isEqualTo(15);
        assertThat(pt.baseLeaveMinutes()).isEqualTo(3600);
        assertThat(pt.paidHolidayMinutes()).isEqualTo(240);
        assertThat(pt.weeklyHolidayApplies()).isTrue();

        // 제18조 제3항: 15시간 바로 아래(14시간 45분)는 초단시간 → 주휴·연차 미적용 안내
        WorkerTypeService.WorkerTypeView us = s.describe("C001", 885, d);
        assertThat(us.workerType()).isEqualTo(WorkerType.ULTRA_SHORT);
        assertThat(us.weeklyHolidayApplies()).isFalse();
        assertThat(us.annualLeaveApplies()).isFalse();
        assertThat(us.baseLeaveMinutes()).isZero();
        assertThat(us.paidHolidayMinutes()).isZero();
        assertThat(us.notice()).contains("제18조 제3항");

        assertThat(s.describe("C001", 2400, d).workerType()).isEqualTo(WorkerType.FULL_TIME);
        assertThat(s.describe("C001", 0, d)).isNull();
    }

    @Test
    void 지정한_주_35시간_근무유형을_통상근로자_비교_기준으로_쓴다() throws IOException {
        LocalDate d = LocalDate.of(2026, 11, 26);
        LegalRateService rates = rates();
        WorkPatternRepository patterns = mock(WorkPatternRepository.class);
        WorkPatternRules.Spec spec = new WorkPatternRules.Spec(540, 1005, 720, 45, "WWWWWOH");
        WorkPattern baseline = new WorkPattern("C001", "FT35", "주 35시간", spec,
                new WorkPatternRules.Result(420, 2100, List.of()), WorkerType.FULL_TIME, true, true, null,
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(patterns.findByEnterCdOrderByPatternCodeAsc("C001")).thenReturn(List.of(baseline));
        WorkerTypeService s = new WorkerTypeService(patterns, new WorkerTypeLegalRules(rates), rates);

        WorkerTypeService.WorkerTypeView view = s.describe("C001", 1200, d);

        assertThat(view.fullTimeWeeklyMinutes()).isEqualTo(2100);
        assertThat(view.fullTimePatternCode()).isEqualTo("FT35");
        assertThat(view.workerType()).isEqualTo(WorkerType.PART_TIME);
        assertThat(view.baseLeaveMinutes()).isEqualTo(4140);
    }
}
