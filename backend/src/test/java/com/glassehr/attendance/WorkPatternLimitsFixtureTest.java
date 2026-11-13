package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 근로기준법 제50조 제1항·제2항(1주·1일 근로시간), 제54조 제1항(휴게), 제55조 제1항(주휴일) 저장 검사를
 * tests/legal-fixtures/work-pattern-limits.csv 정답(Codex 법령 데이터 PR #64, 사람 승인)으로 검증한다.
 *
 * <p>기준값은 seed/legal_rate_work_pattern.sql 의 insert_period 행을 읽어 [시작, 종료) 기간으로
 * {@link LegalRateService#getEffective(LegalRateItem, LocalDate)} 조회 결과를 흉내 낸다. 실제 변환 경로
 * {@link WorkPatternLegalLimits#asOf} → {@link WorkPatternRules#check}를 거친다.
 */
class WorkPatternLimitsFixtureTest {

    private static final String FIXTURE = "tests/legal-fixtures/work-pattern-limits.csv";
    private static final String SEED = "backend/src/main/resources/db/oracle/seed/legal_rate_work_pattern.sql";
    private static final Pattern SEED_ROW = Pattern.compile(
            "insert_period\\(\\s*'([A-Z_]+)',\\s*DATE '(\\d{4}-\\d{2}-\\d{2})',\\s*(NULL|DATE '(\\d{4}-\\d{2}-\\d{2})'),"
                    + "\\s*(\\d+(?:\\.\\d+)?),\\s*'([A-Z_]+)'");

    /** fixture 기대_결과 → 검사기 위반 코드. OK 는 위반 없음. */
    private static final Map<String, WorkPatternRules.Code> EXPECTED = Map.of(
            "DAILY_LIMIT", WorkPatternRules.Code.DAILY_LIMIT_EXCEEDED,
            "WEEKLY_LIMIT", WorkPatternRules.Code.WEEKLY_LIMIT_EXCEEDED,
            "BREAK_SHORT", WorkPatternRules.Code.BREAK_INSUFFICIENT,
            "BREAK_LONG", WorkPatternRules.Code.BREAK_INSUFFICIENT,
            "BREAK_NOT_DURING_WORK", WorkPatternRules.Code.BREAK_OUTSIDE_WORK,
            "WEEKLY_HOLIDAY", WorkPatternRules.Code.WEEKLY_HOLIDAY_REQUIRED);

    /** Maven은 backend/에서, IDE는 저장소 루트에서 돌 수 있어 둘 다 찾는다. */
    private static Path repoFile(String relative) {
        for (Path base : List.of(Path.of(".."), Path.of("."))) {
            Path p = base.resolve(relative).normalize();
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        throw new IllegalStateException("파일을 찾을 수 없어요: " + relative);
    }

    private static List<LegalRateRow> seedRows() throws IOException {
        Matcher m = SEED_ROW.matcher(Files.readString(repoFile(SEED), StandardCharsets.UTF_8));
        List<LegalRateRow> rows = new ArrayList<>();
        while (m.find()) {
            rows.add(new LegalRateRow(m.group(1), LocalDate.parse(m.group(2)),
                    m.group(4) == null ? null : LocalDate.parse(m.group(4)),
                    new BigDecimal(m.group(5)), m.group(6), "seed", null));
        }
        return rows;
    }

    /** 시드에 그 적용일 행이 없을 때(실제 서비스의 NOT_FOUND 대신). */
    private static final class NoSeedValue extends RuntimeException {
        NoSeedValue(String item, LocalDate date) {
            super("시드 값 없음: " + item + " " + date);
        }
    }

    /** 시드 행으로 적용일 조회를 흉내 낸다. 해당 기간 행이 정확히 1개가 아니면 실패. */
    private static LegalRateService seededRates(List<LegalRateRow> rows) {
        LegalRateService s = mock(LegalRateService.class);
        when(s.getEffective(any(LegalRateItem.class), any(LocalDate.class))).thenAnswer(inv -> {
            LegalRateItem item = inv.getArgument(0);
            LocalDate date = inv.getArgument(1);
            List<LegalRateRow> hit = rows.stream()
                    .filter(r -> r.itemCode().equals(item.code()) && r.isEffectiveOn(date)).toList();
            if (hit.size() != 1) {
                throw new NoSeedValue(item.code(), date);
            }
            return hit.get(0);
        });
        return s;
    }

    /** 따옴표로 감싼 CSV 한 줄. 이 fixture 값에는 따옴표·쉼표가 섞인 칸이 없지만 출처 칸 등은 따옴표 안 쉼표를 허용한다. */
    private static List<String> parseCsvLine(String line) {
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

    private static int minutes(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** 월요일부터 근무일(W) n개, 다음 주휴일(H) m개, 나머지 휴무일(O). */
    private static String dayTypes(int workDays, int holidays) {
        assertThat(workDays + holidays).as("근무일+주휴일은 7일 이하").isLessThanOrEqualTo(WorkPatternRules.DAYS_PER_WEEK);
        return String.valueOf(WorkPatternRules.WORK_DAY).repeat(workDays)
                + String.valueOf(WorkPatternRules.WEEKLY_HOLIDAY).repeat(holidays)
                + String.valueOf(WorkPatternRules.OFF_DAY).repeat(WorkPatternRules.DAYS_PER_WEEK - workDays - holidays);
    }

    static Stream<Arguments> fixtureRows() throws IOException {
        List<String> lines = Files.readAllLines(repoFile(FIXTURE), StandardCharsets.UTF_8);
        assertThat(parseCsvLine(lines.get(0))).containsExactly("case_id", "기준일", "시업", "종업", "휴게시작", "휴게종료",
                "1주_근무일수", "주휴일수", "기대_결과", "출처", "검증일");
        return lines.stream().skip(1).filter(l -> !l.isBlank()).map(l -> {
            List<String> c = parseCsvLine(l);
            return Arguments.of(c.get(0), LocalDate.parse(c.get(1)), c.get(2), c.get(3), c.get(4), c.get(5),
                    Integer.parseInt(c.get(6)), Integer.parseInt(c.get(7)), c.get(8));
        });
    }

    @ParameterizedTest(name = "{0} ({1}) → {8}")
    @MethodSource("fixtureRows")
    void 근무유형_검사_결과가_fixture_정답과_같다(String caseId, LocalDate asOf, String start, String end,
            String breakStart, String breakEnd, int workDays, int holidays, String expected) throws IOException {
        WorkPatternRules.Limits limits = new WorkPatternLegalLimits(seededRates(seedRows())).asOf(asOf);

        Integer bs = breakStart.isBlank() ? null : minutes(breakStart);
        int breakMinutes = bs == null ? 0 : WorkPatternRules.offsetFromStart(bs, minutes(breakEnd));
        WorkPatternRules.Spec spec = new WorkPatternRules.Spec(
                minutes(start), minutes(end), bs, breakMinutes, dayTypes(workDays, holidays));

        WorkPatternRules.Result result = WorkPatternRules.check(spec, limits);
        Set<WorkPatternRules.Code> codes = result.violations().stream()
                .map(WorkPatternRules.Violation::code)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(WorkPatternRules.Code.class)));

        if ("OK".equals(expected)) {
            assertThat(codes).as(caseId).isEmpty();
            return;
        }
        assertThat(EXPECTED).as("알 수 없는 기대_결과 %s", expected).containsKey(expected);
        assertThat(codes).as(caseId).containsExactly(EXPECTED.get(expected));
        // 근로기준법 제54조 제1항: 짧은 구간(4시간)·긴 구간(8시간) 중 어느 기준으로 부족한지까지 맞춘다.
        if ("BREAK_SHORT".equals(expected)) {
            assertThat(WorkPatternRules.requiredBreakMinutes(result.dailyContractMinutes(), limits))
                    .as(caseId).isEqualTo(limits.breakShortMinMinutes());
        } else if ("BREAK_LONG".equals(expected)) {
            assertThat(WorkPatternRules.requiredBreakMinutes(result.dailyContractMinutes(), limits))
                    .as(caseId).isEqualTo(limits.breakLongMinMinutes());
        }
    }

    @Test
    void 시드는_7개_항목을_모두_담고_항목_단위와_같다() throws IOException {
        List<LegalRateRow> rows = seedRows();
        Set<String> codes = rows.stream().map(LegalRateRow::itemCode).collect(Collectors.toSet());
        assertThat(codes).containsExactlyInAnyOrder(
                "WEEKLY_STANDARD_WORK_HOURS", "DAILY_STANDARD_WORK_HOURS",
                "BREAK_SHORT_THRESHOLD_HOURS", "BREAK_SHORT_MIN_MINUTES",
                "BREAK_LONG_THRESHOLD_HOURS", "BREAK_LONG_MIN_MINUTES",
                "WEEKLY_PAID_HOLIDAY_MIN_COUNT");
        for (LegalRateRow r : rows) {
            assertThat(r.unit()).as(r.itemCode()).isEqualTo(LegalRateItem.valueOf(r.itemCode()).unit());
        }
    }

    @Test
    void 시드_적용_시작일_전날은_기준값이_없어_검사하지_않는다() throws IOException {
        // 시드는 2008-07-01부터만 제공한다(그 이전 미지원). 기본값으로 검사하지 않고 조회 실패여야 한다.
        List<LegalRateRow> rows = seedRows();
        LocalDate first = rows.stream().map(LegalRateRow::effectiveFrom).min(LocalDate::compareTo).orElseThrow();
        WorkPatternLegalLimits limits = new WorkPatternLegalLimits(seededRates(rows));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> limits.asOf(first.minusDays(1)))
                .isInstanceOf(NoSeedValue.class);
    }
}
