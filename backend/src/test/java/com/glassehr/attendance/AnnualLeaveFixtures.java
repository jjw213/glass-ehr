package com.glassehr.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.legalrate.LegalRateMapper;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 연차 fixture(tests/legal-fixtures/annual-leave*.csv) 테스트 공통: 파일 찾기, CSV, 시드 기준값, 소정근로일 규칙. */
final class AnnualLeaveFixtures {

    static final String SEED = "backend/src/main/resources/db/oracle/seed/legal_rate_hr_current.sql";
    private static final Pattern SEED_ROW = Pattern.compile(
            "insert_period\\(\\s*'([A-Z_]+)',\\s*DATE '(\\d{4}-\\d{2}-\\d{2})',\\s*(NULL|DATE '\\d{4}-\\d{2}-\\d{2}'),"
                    + "\\s*([\\d.]+),\\s*'([A-Z]+)'");
    private static final Pattern WEEKDAYS_EXCEPT = Pattern.compile("평일\\((\\d{4}-\\d{2}-\\d{2}) 제외,총(\\d+)일\\)");
    private static final Pattern YEAR_WEEKDAYS = Pattern.compile("(\\d{4})년 평일.*");
    private static final Pattern YEAR_ONE_PER_MONTH = Pattern.compile("(\\d{4})년 매월 소정근로일 1일\\(총(\\d+)일\\).*");
    private static final Pattern FRACTION_TERM = Pattern.compile("([+-]?)(\\d+)(?:/(\\d+))?");

    private AnnualLeaveFixtures() {
    }

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

    /** 머리글 행을 포함한 모든 행. 따옴표 안 쉼표를 허용한다. */
    static List<List<String>> readCsv(String relative) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (String line : Files.readAllLines(repoFile(relative), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                rows.add(parseCsvLine(rows.isEmpty() && line.startsWith("﻿") ? line.substring(1) : line));
            }
        }
        return rows;
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

    static List<LegalRateRow> seedRows() throws IOException {
        String sql = Files.readString(repoFile(SEED), StandardCharsets.UTF_8);
        Matcher m = SEED_ROW.matcher(sql);
        List<LegalRateRow> rows = new ArrayList<>();
        while (m.find()) {
            LocalDate to = m.group(3).equals("NULL") ? null : LocalDate.parse(m.group(3).substring(6, 16));
            rows.add(new LegalRateRow(m.group(1), LocalDate.parse(m.group(2)), to, new BigDecimal(m.group(4)),
                    m.group(5), "seed", null));
        }
        return rows;
    }

    /** 시드 행으로 조회하는 실제 {@link AnnualLeaveLegalRates}(발생일 기준). 같은 날은 한 번만 조회한다. */
    static Function<LocalDate, AnnualLeaveRules.Rates> seededRates() throws IOException {
        List<LegalRateRow> rows = seedRows();
        LegalRateMapper mapper = mock(LegalRateMapper.class);
        when(mapper.findEffective(anyString(), any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            LocalDate asOf = inv.getArgument(1);
            return rows.stream().filter(r -> r.itemCode().equals(code) && r.isEffectiveOn(asOf)).toList();
        });
        AnnualLeaveLegalRates rates = new AnnualLeaveLegalRates(new LegalRateService(mapper));
        Map<LocalDate, AnnualLeaveRules.Rates> cache = new HashMap<>();
        return d -> cache.computeIfAbsent(d, rates::asOf);
    }

    static Set<LocalDate> weekdays(LocalDate from, LocalDate to) {
        Set<LocalDate> out = new HashSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                out.add(d);
            }
        }
        return out;
    }

    /**
     * fixture의 소정근로일규칙 → [from, to] 안의 소정근로일. 규칙: {@code 평일}, {@code 평일(날짜 제외,총N일)},
     * {@code ;}로 나눈 연도별 {@code YYYY년 평일…}·{@code YYYY년 매월 소정근로일 1일(총N일)…}(그 달 결근일, 없으면 첫 평일).
     */
    static Set<LocalDate> scheduledDays(String rule, LocalDate from, LocalDate to, Set<LocalDate> absent) {
        if (rule.equals("평일")) {
            return weekdays(from, to);
        }
        Matcher except = WEEKDAYS_EXCEPT.matcher(rule);
        if (except.matches()) {
            LocalDate excluded = LocalDate.parse(except.group(1));
            Set<LocalDate> days = weekdays(from, to);
            days.remove(excluded);
            long inYear = days.stream().filter(d -> d.getYear() == excluded.getYear()).count();
            if (inYear != Long.parseLong(except.group(2))) {
                throw new IllegalStateException("소정근로일 수가 규칙과 달라요: " + rule + " → " + inYear);
            }
            return days;
        }
        Set<LocalDate> days = new HashSet<>();
        for (String seg : rule.split(";")) {
            String s = seg.trim();
            Matcher w = YEAR_WEEKDAYS.matcher(s);
            Matcher one = YEAR_ONE_PER_MONTH.matcher(s);
            if (w.matches()) {
                int y = Integer.parseInt(w.group(1));
                days.addAll(weekdays(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)));
            } else if (one.matches()) {
                int y = Integer.parseInt(one.group(1));
                for (int m = 1; m <= 12; m++) {
                    YearMonth ym = YearMonth.of(y, m);
                    LocalDate day = absent.stream().filter(d -> YearMonth.from(d).equals(ym)).findFirst()
                            .orElseGet(() -> weekdays(ym.atDay(1), ym.atEndOfMonth()).stream().min(LocalDate::compareTo).orElseThrow());
                    days.add(day);
                }
            } else {
                throw new IllegalStateException("해석할 수 없는 소정근로일규칙: " + rule);
            }
        }
        days.removeIf(d -> d.isBefore(from) || d.isAfter(to));
        return days;
    }

    static Set<LocalDate> dates(String cell) {
        Set<LocalDate> out = new HashSet<>();
        for (String s : cell.split(";")) {
            if (!s.isBlank()) {
                out.add(LocalDate.parse(s.trim()));
            }
        }
        return out;
    }

    /** {@code 시작~끝(사유)} 를 {@code ;}로 나눈 출근 간주 기간. */
    static List<AnnualLeaveRules.DateRange> ranges(String cell) {
        List<AnnualLeaveRules.DateRange> out = new ArrayList<>();
        for (String s : cell.split(";")) {
            String t = s.replaceAll("\\(.*\\)", "").trim();
            if (!t.isEmpty()) {
                String[] p = t.split("~");
                out.add(new AnnualLeaveRules.DateRange(LocalDate.parse(p[0].trim()), LocalDate.parse(p[1].trim())));
            }
        }
        return out;
    }

    /** {@code 26+15/262}, {@code 15-990/131}, {@code -1300/87} 같은 분수 합. */
    static AnnualLeaveFiscalYear.LeaveDays fraction(String expr) {
        String s = expr.replace(" ", "");
        Matcher m = FRACTION_TERM.matcher(s);
        AnnualLeaveFiscalYear.LeaveDays sum = AnnualLeaveFiscalYear.LeaveDays.ZERO;
        int pos = 0;
        while (pos < s.length()) {
            if (!m.find(pos) || m.start() != pos || m.end() == pos) {
                throw new IllegalStateException("해석할 수 없는 일수: " + expr);
            }
            long num = Long.parseLong(m.group(2)) * (m.group(1).equals("-") ? -1 : 1);
            long den = m.group(3) == null ? 1 : Long.parseLong(m.group(3));
            sum = sum.plus(new AnnualLeaveFiscalYear.LeaveDays(num, den));
            pos = m.end();
        }
        return sum;
    }

    /** {@code ;}로 나눈 기대 발생 내역(앞뒤 공백 제거, 정렬). */
    static List<String> entries(String cell) {
        List<String> out = new ArrayList<>();
        for (String s : cell.split(";")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        out.sort(null);
        return out;
    }
}
