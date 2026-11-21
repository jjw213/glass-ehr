package com.glassehr.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.approval.ApprovalException;
import com.glassehr.attendance.DailyWorkSummaryMapper;
import com.glassehr.attendance.DailyWorkSummaryRow;
import com.glassehr.attendance.WorkPatternRepository;
import com.glassehr.legalrate.LegalRateException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 근로기준법 제2조 제1항 제7호·제50조 제1항·제53조 제1항 1주 연장근로 한도 판정을
 * tests/legal-fixtures/weekly-overtime-limit.csv 정답(Codex 법령 데이터 PR #97, 사람 승인)으로 검증한다.
 * 기준값은 legal_rate 시드 파일의 insert_period 행으로 적용일 조회를 흉내 낸다.
 */
class WeeklyOvertimeLimitFixtureTest {

    private static final String FIXTURE = "tests/legal-fixtures/weekly-overtime-limit.csv";
    private static final List<String> SEEDS = List.of(
            "backend/src/main/resources/db/oracle/seed/legal_rate_hr_current.sql",
            "backend/src/main/resources/db/oracle/seed/legal_rate_work_pattern.sql");
    private static final Pattern SEED_ROW = Pattern.compile(
            "insert_period\\(\\s*'([A-Z_]+)',\\s*DATE '(\\d{4}-\\d{2}-\\d{2})',\\s*(NULL|DATE '(\\d{4}-\\d{2}-\\d{2})'),"
                    + "\\s*(\\d+(?:\\.\\d+)?),\\s*'([A-Z_]+)'");
    private static final long EMP = 1L;

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
        List<LegalRateRow> rows = new ArrayList<>();
        for (String seed : SEEDS) {
            Matcher m = SEED_ROW.matcher(Files.readString(repoFile(seed), StandardCharsets.UTF_8));
            while (m.find()) {
                rows.add(new LegalRateRow(m.group(1), LocalDate.parse(m.group(2)),
                        m.group(4) == null ? null : LocalDate.parse(m.group(4)),
                        new BigDecimal(m.group(5)), m.group(6), "seed", null));
            }
        }
        return rows;
    }

    /** 시드 행으로 적용일 조회를 흉내 낸다. 0건이면 실제 서비스처럼 NOT_FOUND, 2건 이상이면 실패. */
    private static LegalRateService seededRates(List<LegalRateRow> rows) {
        LegalRateService s = mock(LegalRateService.class);
        when(s.getEffective(any(LegalRateItem.class), any(LocalDate.class))).thenAnswer(inv -> {
            LegalRateItem item = inv.getArgument(0);
            LocalDate date = inv.getArgument(1);
            List<LegalRateRow> hit = rows.stream()
                    .filter(r -> r.itemCode().equals(item.code()) && r.isEffectiveOn(date)).toList();
            if (hit.isEmpty()) {
                throw mock(LegalRateException.class);
            }
            if (hit.size() > 1) {
                throw new IllegalStateException("시드 값이 겹쳐요: " + item.code() + " " + date);
            }
            return hit.get(0);
        });
        when(s.firstEffectiveFrom(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return rows.stream().filter(r -> r.itemCode().equals(code)).map(LegalRateRow::effectiveFrom)
                    .min(LocalDate::compareTo);
        });
        return s;
    }

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

    static Stream<Arguments> cases() throws IOException {
        List<String> lines = Files.readAllLines(repoFile(FIXTURE), StandardCharsets.UTF_8);
        List<String> header = parseCsvLine(lines.get(0).replace("﻿", ""));
        return lines.stream().skip(1).filter(l -> !l.isBlank()).map(l -> {
            List<String> v = parseCsvLine(l);
            return Arguments.of(v.get(header.indexOf("case_id")), v.get(header.indexOf("week_start")),
                    v.get(header.indexOf("daily_records")), v.get(header.indexOf("approved_requests")),
                    v.get(header.indexOf("new_request")),
                    Integer.parseInt(v.get(header.indexOf("weekly_overtime_minutes"))),
                    v.get(header.indexOf("allowed")));
        });
    }

    private static long nextId = 100;

    /** 'YYYY-MM-DD:OVERTIME|HOLIDAY:분' → 승인 대상 신청(근로일 0시 시작, 휴게 0). */
    private static OvertimeRequest request(String spec) {
        String[] p = spec.split(":");
        LocalDateTime start = LocalDate.parse(p[0]).atStartOfDay();
        OvertimeRequest r = new OvertimeRequest(EMP, OvertimeRequest.Kind.valueOf(p[1]), start,
                start.plusMinutes(Integer.parseInt(p[2])), 0, "fixture", start.minusDays(1));
        ReflectionTestUtils.setField(r, "requestId", nextId++);
        return r;
    }

    /** 'YYYY-MM-DD:WORKDAY|OFFDAY|HOLIDAY:실근로분' → 집계 결과 행(실근로분만 의미 있음). */
    private static DailyWorkSummaryRow record(String spec) {
        String[] p = spec.split(":");
        return new DailyWorkSummaryRow(EMP, LocalDate.parse(p[0]), p[1], 1L, 480, null, 0, null, null, 0,
                Integer.parseInt(p[2]), 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, "fixture", LocalDateTime.now());
    }

    private static List<String> items(String s) {
        return s.isBlank() ? List.of() : List.of(s.split(";"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void 정답과_같게_판정한다(String caseId, String weekStart, String daily, String approvedSpec, String newSpec,
            int expectedOvertime, String allowed) throws IOException {
        DailyWorkSummaryMapper summaries = mock(DailyWorkSummaryMapper.class);
        OvertimeRequestRepository requests = mock(OvertimeRequestRepository.class);
        when(summaries.findRange(anyLong(), any(), any()))
                .thenReturn(items(daily).stream().map(WeeklyOvertimeLimitFixtureTest::record).toList());
        when(requests.findByEmpIdAndWorkDateBetweenAndStatusIn(anyLong(), any(), any(), any()))
                .thenReturn(items(approvedSpec).stream().map(WeeklyOvertimeLimitFixtureTest::request).toList());
        OvertimeLimitChecker checker = new OvertimeLimitChecker(seededRates(seedRows()), summaries, requests,
                mock(WorkPatternRepository.class));
        OvertimeRequest target = request(newSpec);
        assertThat(target.getWorkDate().with(java.time.DayOfWeek.MONDAY)).isEqualTo(LocalDate.parse(weekStart));

        if ("Y".equals(allowed)) {
            OvertimeLimitChecker.Result r = checker.check(target);
            assertThat(r.overtimeMinutes()).as(caseId).isEqualTo(expectedOvertime);
            assertThat(r.weekStart()).isEqualTo(LocalDate.parse(weekStart));
        } else {
            assertThatThrownBy(() -> checker.check(target)).as(caseId)
                    .isInstanceOfSatisfying(ApprovalException.class,
                            e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                    .hasMessageContaining(OvertimeLimitChecker.hm(expectedOvertime) + "로");
        }
    }
}
