package com.glassehr.attendance;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사람이 넣은 공휴일 자료(CSV)를 읽는다(#26). 에이전트는 공휴일 날짜를 만들지 않는다. 이 클래스는 형식만 검사한다.
 *
 * <p>형식(UTF-8, 첫 줄 머리글, 열 순서 무관, 다른 열은 무시):
 * <pre>
 * 날짜,이름,종류,출처,검증일
 * YYYY-MM-DD,설날,법정공휴일,"관공서의 공휴일에 관한 규정 제2조 제4호, https://www.law.go.kr/...",YYYY-MM-DD
 * </pre>
 * <ul>
 *   <li>종류: {@code 법정공휴일}(규정 제2조) 또는 {@code 대체공휴일}(규정 제3조)만.</li>
 *   <li>출처·검증일은 빈칸 불가(AGENTS.md 법령 규칙: 값마다 출처와 확인 날짜).</li>
 *   <li>같은 날짜·이름이 두 번 나오면 오류.</li>
 *   <li>칸은 쉼표로 나누고, 큰따옴표 안 쉼표·{@code ""}(따옴표 자체)를 허용한다.</li>
 * </ul>
 * 오류가 하나라도 있으면 전체를 거부한다(일부만 들어가지 않게).
 */
public final class PublicHolidayCsv {

    public static final String COL_DATE = "날짜";
    public static final String COL_NAME = "이름";
    public static final String COL_KIND = "종류";
    public static final String COL_SOURCE = "출처";
    public static final String COL_VERIFIED = "검증일";
    static final List<String> REQUIRED = List.of(COL_DATE, COL_NAME, COL_KIND, COL_SOURCE, COL_VERIFIED);
    static final int NAME_MAX = 100;
    static final int SOURCE_MAX = 500;

    /** 읽은 한 행. line 은 파일의 줄 번호(머리글 = 1). */
    public record Row(int line, LocalDate date, String name, HolidayKind kind, String source, LocalDate verified) {
    }

    /** 형식 오류. 메시지에 줄 번호를 넣는다. */
    public static final class FormatException extends RuntimeException {
        public FormatException(String message) {
            super(message);
        }
    }

    private PublicHolidayCsv() {
    }

    public static List<Row> parse(String text) {
        if (text == null || text.isBlank()) {
            throw new FormatException("공휴일 자료가 비어 있어요");
        }
        String body = text.startsWith("﻿") ? text.substring(1) : text;
        List<String> lines = body.lines().toList();
        List<String> header = splitLine(lines.get(0)).stream().map(String::trim).toList();
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            col.putIfAbsent(header.get(i), i);
        }
        List<String> missing = REQUIRED.stream().filter(c -> !col.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new FormatException("머리글에 열이 없어요: " + String.join(", ", missing)
                    + " (필요한 열: " + String.join(",", REQUIRED) + ")");
        }
        List<Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            int lineNo = i + 1;
            String raw = lines.get(i);
            if (raw.isBlank()) {
                continue;
            }
            List<String> cells = splitLine(raw);
            String date = cell(cells, col.get(COL_DATE));
            String name = cell(cells, col.get(COL_NAME));
            String kind = cell(cells, col.get(COL_KIND));
            String source = cell(cells, col.get(COL_SOURCE));
            String verified = cell(cells, col.get(COL_VERIFIED));
            LocalDate d = date(date, lineNo, COL_DATE);
            if (name.isEmpty() || name.length() > NAME_MAX) {
                throw new FormatException(lineNo + "번째 줄: 이름은 1~" + NAME_MAX + "자로 써 주세요");
            }
            HolidayKind k = HolidayKind.fromStatutoryLabel(kind);
            if (k == null) {
                throw new FormatException(lineNo + "번째 줄: 종류는 '" + HolidayKind.PUBLIC.label() + "' 또는 '"
                        + HolidayKind.SUBSTITUTE.label() + "'만 쓸 수 있어요: " + kind);
            }
            if (source.isEmpty() || source.length() > SOURCE_MAX) {
                throw new FormatException(lineNo + "번째 줄: 출처(근거 법령·링크)를 1~" + SOURCE_MAX + "자로 써 주세요");
            }
            LocalDate v = date(verified, lineNo, COL_VERIFIED);
            if (!seen.add(d + "|" + name)) {
                throw new FormatException(lineNo + "번째 줄: 같은 날짜·이름이 이미 있어요: " + d + " " + name);
            }
            rows.add(new Row(lineNo, d, name, k, source, v));
        }
        if (rows.isEmpty()) {
            throw new FormatException("공휴일 자료에 행이 없어요");
        }
        return rows;
    }

    private static LocalDate date(String s, int lineNo, String column) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new FormatException(lineNo + "번째 줄: " + column + "은 YYYY-MM-DD로 써 주세요: " + s);
        }
    }

    private static String cell(List<String> cells, int index) {
        return index < cells.size() ? cells.get(index).trim() : "";
    }

    static List<String> splitLine(String line) {
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
}
