package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.WorkHoursCalculator.DayInput;
import com.glassehr.attendance.WorkHoursCalculator.DayKind;
import com.glassehr.attendance.WorkHoursCalculator.DayResult;
import com.glassehr.attendance.WorkHoursCalculator.Interval;
import com.glassehr.attendance.WorkHoursCalculator.Leave;
import com.glassehr.attendance.WorkHoursCalculator.LeaveKind;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 근로기준법 제50조·제53조·제54조·제55조·제56조 근로시간 분류를 tests/legal-fixtures/work-hours-cases.csv 정답
 * (Codex 법령 데이터 PR #88, 사람 승인)으로 검증한다.
 *
 * <p>기준값은 seed/legal_rate_hr_current.sql·legal_rate_work_pattern.sql 의 insert_period 행으로 적용일 조회를 흉내 내고,
 * 실제 경로 {@link WorkHoursLegalRules#asOf} → {@link WorkHoursCalculator#calculateDay} →
 * {@link WorkHoursCalculator#applyWeekly}를 거친다. 가산율도 그 날짜에 조회돼야 한다(금액 계산은 하지 않음).
 */
class WorkHoursFixtureTest {

    private static final String FIXTURE = "tests/legal-fixtures/work-hours-cases.csv";
    private static final List<String> SEEDS = List.of(
            "backend/src/main/resources/db/oracle/seed/legal_rate_hr_current.sql",
            "backend/src/main/resources/db/oracle/seed/legal_rate_work_pattern.sql");
    private static final Pattern SEED_ROW = Pattern.compile(
            "insert_period\\(\\s*'([A-Z_]+)',\\s*DATE '(\\d{4}-\\d{2}-\\d{2})',\\s*(NULL|DATE '(\\d{4}-\\d{2}-\\d{2})'),"
                    + "\\s*(\\d+(?:\\.\\d+)?),\\s*'([A-Z_]+)'");

    /** Maven은 backend/에서, IDE는 저장소 루트에서 돌 수 있어 둘 다 찾는다. */
    static Path repoFile(String relative) {
        for (Path base : List.of(Path.of(".."), Path.of("."))) {
            Path p = base.resolve(relative).normalize();
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        throw new IllegalStateException("파일을 찾을 수 없어요: " + relative);
    }

    private static List<LegalRateRow> seedRows() throws IOException {
        return seedRows(SEEDS);
    }

    /** 시드 파일의 insert_period 행(다른 fixture 테스트도 쓴다). */
    static List<LegalRateRow> seedRows(List<String> seeds) throws IOException {
        List<LegalRateRow> rows = new ArrayList<>();
        for (String seed : seeds) {
            Matcher m = SEED_ROW.matcher(Files.readString(repoFile(seed), StandardCharsets.UTF_8));
            while (m.find()) {
                rows.add(new LegalRateRow(m.group(1), LocalDate.parse(m.group(2)),
                        m.group(4) == null ? null : LocalDate.parse(m.group(4)),
                        new BigDecimal(m.group(5)), m.group(6), "seed", null));
            }
        }
        return rows;
    }

    /** 시드 행으로 적용일 조회를 흉내 낸다. 해당 기간 행이 정확히 1개가 아니면 실패. */
    static LegalRateService seededRates(List<LegalRateRow> rows) {
        LegalRateService s = mock(LegalRateService.class);
        when(s.getEffective(any(LegalRateItem.class), any(LocalDate.class))).thenAnswer(inv -> {
            LegalRateItem item = inv.getArgument(0);
            LocalDate date = inv.getArgument(1);
            List<LegalRateRow> hit = rows.stream()
                    .filter(r -> r.itemCode().equals(item.code()) && r.isEffectiveOn(date)).toList();
            if (hit.size() != 1) {
                throw new IllegalStateException("시드 값이 1개가 아니에요: " + item.code() + " " + date + " " + hit.size());
            }
            return hit.get(0);
        });
        return s;
    }

    /** 따옴표 안 쉼표를 허용하는 CSV 한 줄. */
    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /** fixture 날 성격 → 엔진 날 성격. 주휴일·공휴일·회사휴일은 모두 휴일(근로기준법 제55조·제56조 제2항). */
    private static DayKind dayKind(String s) {
        return switch (s) {
            case "WORKDAY" -> DayKind.WORKDAY;
            case "OFF_DAY" -> DayKind.OFFDAY;
            case "WEEKLY_HOLIDAY", "PUBLIC_HOLIDAY", "COMPANY_HOLIDAY" -> DayKind.HOLIDAY;
            default -> throw new IllegalArgumentException("알 수 없는 날 성격: " + s);
        };
    }

    /** "HH:mm" 은 근무일 시각, 날짜가 붙은 ISO 는 그대로. */
    private static LocalDateTime endTime(LocalDate workDate, String s) {
        return s.contains("T") ? LocalDateTime.parse(s) : workDate.atTime(LocalTime.parse(s));
    }

    /** 휴게 시각이 출근 시각보다 이르면 다음 날(자정을 넘는 근무의 휴게)이다. */
    private static List<Interval> breaks(LocalDate workDate, LocalDateTime start, String s) {
        List<Interval> out = new ArrayList<>();
        if (s.isBlank()) {
            return out;
        }
        for (String part : s.split("\\|")) {
            String[] p = part.split("~");
            LocalDateTime bs = workDate.atTime(LocalTime.parse(p[0].trim()));
            if (bs.isBefore(start)) {
                bs = bs.plusDays(1);
            }
            LocalDateTime be = bs.toLocalDate().atTime(LocalTime.parse(p[1].trim()));
            if (!be.isAfter(bs)) {
                be = be.plusDays(1);
            }
            out.add(new Interval(bs, be));
        }
        return out;
    }

    static Stream<Arguments> fixtureRows() throws IOException {
        List<String> lines = Files.readAllLines(repoFile(FIXTURE), StandardCharsets.UTF_8);
        assertThat(parseCsvLine(lines.get(0))).containsExactly("case_id", "근무일",
                "날_성격(WORKDAY|OFF_DAY|WEEKLY_HOLIDAY|PUBLIC_HOLIDAY|COMPANY_HOLIDAY)", "다음날_성격", "소정근로분",
                "출근시각", "퇴근시각(다음 날이면 날짜 포함 ISO)", "휴게구간(시작~종료;여러 개는 |로 구분)", "휴가분",
                "주_누적_평일실근로분(그날 전까지)", "기대_실근로분", "기대_소정분", "기대_연장분", "기대_야간분",
                "기대_휴일8h이내분", "기대_휴일8h초과분", "출처", "검증일");
        return lines.stream().skip(1).filter(l -> !l.isBlank()).map(l -> Arguments.of(parseCsvLine(l).get(0),
                parseCsvLine(l)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureRows")
    void 근로시간_분류가_fixture_정답과_같다(String caseId, List<String> c) throws IOException {
        LocalDate workDate = LocalDate.parse(c.get(1));
        DayKind kind = dayKind(c.get(2));
        dayKind(c.get(3)); // 다음 날 성격도 알려진 값이어야 한다(엔진은 근로개시일 성격만 쓴다).
        int contract = Integer.parseInt(c.get(4));
        LocalDateTime start = workDate.atTime(LocalTime.parse(c.get(5)));
        LocalDateTime end = endTime(workDate, c.get(6));
        int leaveMinutes = Integer.parseInt(c.get(8));
        Leave leave = leaveMinutes == 0 ? null
                : new Leave(leaveMinutes >= contract ? LeaveKind.FULL_DAY : LeaveKind.HALF_DAY_AM, leaveMinutes, true);
        int priorWeekday = Integer.parseInt(c.get(9));

        WorkHoursLegalRules.Applied applied = new WorkHoursLegalRules(seededRates(seedRows())).asOf(workDate);
        DayInput in = new DayInput(workDate, kind, contract, List.of(new Interval(start, end)),
                breaks(workDate, start, c.get(7)), leave, applied.rules());
        DayResult r = WorkHoursCalculator.applyWeekly(WorkHoursCalculator.calculateDay(in), priorWeekday,
                applied.rules().weeklyStandardMinutes());

        assertThat(r.workedMinutes()).as(caseId + " 실근로").isEqualTo(Integer.parseInt(c.get(10)));
        assertThat(r.regularMinutes()).as(caseId + " 소정").isEqualTo(Integer.parseInt(c.get(11)));
        assertThat(r.overtimeMinutes()).as(caseId + " 연장").isEqualTo(Integer.parseInt(c.get(12)));
        assertThat(r.nightMinutes()).as(caseId + " 야간").isEqualTo(Integer.parseInt(c.get(13)));
        assertThat(r.holidayMinutes()).as(caseId + " 휴일 8h 이내").isEqualTo(Integer.parseInt(c.get(14)));
        assertThat(r.holidayExcessMinutes()).as(caseId + " 휴일 8h 초과").isEqualTo(Integer.parseInt(c.get(15)));
        assertThat(applied.rates()).as(caseId + " 가산율 조회").isNotNull();
        assertThat(c.get(16)).as(caseId + " 출처").isNotBlank();
        assertThat(LocalDate.parse(c.get(17))).as(caseId + " 검증일").isNotNull();
    }
}
