package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.attendance.FlexibleWorkCalculator.ActualDay;
import com.glassehr.attendance.FlexibleWorkCalculator.Check;
import com.glassehr.attendance.FlexibleWorkCalculator.DayOvertime;
import com.glassehr.attendance.FlexibleWorkCalculator.DiscretionaryResult;
import com.glassehr.attendance.FlexibleWorkCalculator.FlexSettlement;
import com.glassehr.attendance.FlexibleWorkCalculator.Limits;
import com.glassehr.attendance.FlexibleWorkCalculator.PlanDay;
import com.glassehr.attendance.FlexibleWorkCalculator.SelectiveDay;
import com.glassehr.attendance.FlexibleWorkCalculator.SelectiveJob;
import com.glassehr.attendance.FlexibleWorkCalculator.SelectiveResult;
import com.glassehr.attendance.FlexibleWorkCalculator.WeekOvertime;
import com.glassehr.attendance.FlexibleWorkCalculator.WorkSystem;
import com.glassehr.legalrate.LegalRateService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 근로기준법 제51조·제51조의2·제51조의3(탄력적), 제52조(선택적), 제58조 제3항(재량) 계산을
 * tests/legal-fixtures/flexible-work-hours.csv·selective-work-hours.csv·discretionary-work-hours.csv
 * (Codex 법령 데이터 PR #105·#107, 사람 승인) 정답으로 검증한다. 한도는 seed 의 insert_period 행으로 적용일 조회를 흉내 낸다.
 */
class FlexibleWorkFixtureTest {

    private static final List<String> SEEDS = List.of(
            "backend/src/main/resources/db/oracle/seed/legal_rate_hr_current.sql",
            "backend/src/main/resources/db/oracle/seed/legal_rate_work_pattern.sql",
            "backend/src/main/resources/db/oracle/seed/legal_rate_flexible_work.sql");

    private static FlexibleWorkLegalRules rules;
    private static WorkHoursLegalRules workHours;

    private static synchronized FlexibleWorkLegalRules rules() throws IOException {
        if (rules == null) {
            LegalRateService rates = WorkHoursFixtureTest.seededRates(WorkHoursFixtureTest.seedRows(SEEDS));
            workHours = new WorkHoursLegalRules(rates);
            rules = new FlexibleWorkLegalRules(rates, workHours);
        }
        return rules;
    }

    private static List<List<String>> csv(String path) throws IOException {
        List<String> lines = Files.readAllLines(WorkHoursFixtureTest.repoFile(path), StandardCharsets.UTF_8);
        return lines.stream().skip(1).filter(l -> !l.isBlank()).map(WorkHoursFixtureTest::parseCsvLine).toList();
    }

    private static Integer optInt(String s) {
        return s == null || s.isBlank() ? null : Integer.valueOf(s.strip());
    }

    // ------------------------------------------------------------ 탄력적

    static Stream<Arguments> flexibleCases() throws IOException {
        Map<String, List<List<String>>> byCase = new LinkedHashMap<>();
        for (List<String> r : csv("tests/legal-fixtures/flexible-work-hours.csv")) {
            byCase.computeIfAbsent(r.get(0), k -> new ArrayList<>()).add(r);
        }
        return byCase.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("flexibleCases")
    void flexible(String caseId, List<List<String>> rows) throws IOException {
        List<String> first = rows.get(0);
        WorkSystem system = WorkSystem.valueOf(first.get(1));
        LocalDate ps = LocalDate.parse(first.get(2));
        LocalDate pe = LocalDate.parse(first.get(3));
        LocalDate wf = LocalDate.parse(first.get(8));
        LocalDate wt = LocalDate.parse(first.get(9));
        Limits limits = rules().flexibleAsOf(system, ps);

        List<PlanDay> plan = new ArrayList<>();
        List<ActualDay> actual = new ArrayList<>();
        for (List<String> r : rows) {
            LocalDate d = LocalDate.parse(r.get(4));
            LocalDateTime s = r.get(6).isBlank() ? null : LocalDateTime.parse(r.get(6));
            LocalDateTime t = r.get(7).isBlank() ? null : LocalDateTime.parse(r.get(7));
            int planMin = Integer.parseInt(r.get(10));
            plan.add(new PlanDay(d, s, t, planMin));
            actual.add(new ActualDay(d, planMin, Integer.parseInt(r.get(11))));
        }
        Check expected = Check.valueOf(rows.get(rows.size() - 1).get(15));
        assertThat(FlexibleWorkCalculator.checkFlexiblePlan(ps, pe, plan, limits)).as(caseId + " 정의 검사")
                .isEqualTo(expected);
        if (expected != Check.OK) {
            return;
        }

        FlexSettlement s = FlexibleWorkCalculator.settleFlexible(ps, pe, wf, wt, actual, limits);
        Map<LocalDate, Integer> dayOt = new HashMap<>();
        for (DayOvertime d : s.days()) {
            dayOt.put(d.date(), d.overtimeMinutes());
        }
        Map<LocalDate, Integer> weekOt = new HashMap<>();
        for (WeekOvertime w : s.weeks()) {
            weekOt.put(w.weekEnd(), w.overtimeMinutes());
        }
        for (List<String> r : rows) {
            LocalDate d = LocalDate.parse(r.get(4));
            assertThat(dayOt.getOrDefault(d, 0)).as(caseId + " " + d + " 일 연장").isEqualTo(Integer.parseInt(r.get(12)));
            Integer w = optInt(r.get(13));
            if (w != null) {
                assertThat(weekOt).as(caseId + " " + d + " 주 연장").containsEntry(d, w);
            }
        }
        // 기대값이 빈 사례(written_3months_plus_1day, 정의 검사만 다룸)는 단위기간 연장을 대조하지 않는다.
        Integer period = optInt(rows.get(rows.size() - 1).get(14));
        if (period != null) {
            assertThat(s.periodOvertime()).as(caseId + " 단위기간 연장").isEqualTo(period);
        }
    }

    // ------------------------------------------------------------ 선택적

    static Stream<Arguments> selectiveCases() throws IOException {
        return csv("tests/legal-fixtures/selective-work-hours.csv").stream().map(r -> Arguments.of(r.get(0), r));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("selectiveCases")
    void selective(String caseId, List<String> r) throws IOException {
        SelectiveJob job = SelectiveJob.valueOf(r.get(1));
        LocalDate start = LocalDate.parse(r.get(2));
        LocalDate end = LocalDate.parse(r.get(3));
        List<SelectiveDay> days = new ArrayList<>();
        if (!r.get(4).isBlank()) {
            for (String part : r.get(4).split(";")) {
                String[] f = part.split("=");
                LocalDate d = LocalDate.parse(f[0]);
                String[] hm = f[1].split("~");
                LocalDateTime a = d.atTime(LocalTime.parse(hm[0]));
                LocalDateTime b = d.atTime(LocalTime.parse(hm[1]));
                if (!b.isAfter(a)) {
                    b = b.plusDays(1);
                }
                days.add(new SelectiveDay(d, a, b, Integer.parseInt(f[2])));
            }
        }
        LocalTime cs = null;
        LocalTime ce = null;
        if (!r.get(5).isBlank()) {
            String[] c = r.get(5).split("~");
            cs = LocalTime.parse(c[0]);
            ce = LocalTime.parse(c[1]);
        }
        Limits limits = rules().selectiveAsOf(job, start);
        SelectiveResult res = FlexibleWorkCalculator.settleSelective(start, end, days, cs, ce, limits,
                limits.restMinutes());
        assertThat(res.check()).as(caseId + " 정의 검사").isEqualTo(Check.valueOf(r.get(9)));
        assertThat(res.totalLimitMinutes()).as(caseId + " 총량 한도").isEqualTo(Integer.parseInt(r.get(6)));
        if (res.check() != Check.SETTLEMENT_EXCEEDED) {
            assertThat(res.overtimeMinutes()).as(caseId + " 연장").isEqualTo(Integer.parseInt(r.get(7)));
            assertThat(res.coreTimeMissDays()).as(caseId + " 코어타임 미준수").isEqualTo(Integer.parseInt(r.get(8)));
        }
    }

    // ------------------------------------------------------------ 재량

    static Stream<Arguments> discretionaryCases() throws IOException {
        return csv("tests/legal-fixtures/discretionary-work-hours.csv").stream().map(r -> Arguments.of(r.get(0), r));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("discretionaryCases")
    void discretionary(String caseId, List<String> r) throws IOException {
        rules();
        int daily = workHours.asOf(LocalDate.parse(r.get(12))).rules().dailyStandardMinutes();
        boolean holiday = r.get(4).equals("WEEKLY_HOLIDAY") || r.get(4).equals("PUBLIC_HOLIDAY");
        DiscretionaryResult res = FlexibleWorkCalculator.settleDiscretionary(r.get(1), Integer.parseInt(r.get(2)),
                Integer.parseInt(r.get(5)), holiday, daily);
        assertThat(res.check()).as(caseId).isEqualTo(Check.valueOf(r.get(10)));
        assertThat(res.recognizedMinutes()).as(caseId + " 인정").isEqualTo(Integer.parseInt(r.get(6)));
        assertThat(res.overtimeMinutes()).as(caseId + " 연장").isEqualTo(Integer.parseInt(r.get(7)));
        assertThat(res.nightMinutes()).as(caseId + " 야간").isEqualTo(Integer.parseInt(r.get(8)));
        assertThat(res.holidayMinutes()).as(caseId + " 휴일").isEqualTo(Integer.parseInt(r.get(9)));
    }

    /** 서면합의가 필요한 제도와 아닌 제도(근로기준법 제51조 제1항은 취업규칙, 나머지는 근로자대표 서면합의). */
    @Test
    void requiredAgreement() {
        assertThat(WorkSystem.FLEXIBLE_SHORT.requiredAgreement()).isNull();
        assertThat(WorkSystem.FLEXIBLE_WRITTEN.requiredAgreement()).isEqualTo(AgreementType.FLEXIBLE_WRITTEN);
        assertThat(WorkSystem.FLEXIBLE_LONG.requiredAgreement()).isEqualTo(AgreementType.FLEXIBLE_LONG);
        assertThat(WorkSystem.SELECTIVE.requiredAgreement()).isEqualTo(AgreementType.SELECTIVE);
        assertThat(WorkSystem.DISCRETIONARY.requiredAgreement()).isEqualTo(AgreementType.DISCRETIONARY);
    }
}
