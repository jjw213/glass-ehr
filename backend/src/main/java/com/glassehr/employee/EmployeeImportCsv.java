package com.glassehr.employee;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 사원 일괄 등록 양식(CSV, 엑셀에서 열고 저장). 열 머리글 이름으로 값을 찾으므로 열 순서는 바꿔도 된다.
 * 필수 열은 머리글 끝에 '*'를 붙여 보여 주고, 읽을 때는 '*'가 있든 없든 같은 열로 본다.
 */
final class EmployeeImportCsv {

    /** 양식 열. 머리글 이름과 필수 여부. */
    enum Column {
        SITE_CODE("사업장코드", true),
        NAME("성명", true),
        GENDER("성별(M/F)", true),
        BIRTH_DATE("생년월일", true),
        ADDRESS("주소", true),
        CAREER_HISTORY("이력", false),
        JOB_DESCRIPTION("종사업무", true),
        JOB_FAMILY_CODE("직군코드", false),
        HIRE_DATE("입사일", true),
        RENEWAL_DATE("고용갱신일", false),
        CONTRACT_START_DATE("계약시작일", false),
        CONTRACT_END_DATE("계약종료일", false),
        EMPLOYMENT_TYPE_CODE("고용형태코드", true),
        WEEKLY_CONTRACT_MINUTES("1주소정근로시간(분)", false),
        WORK_LOCATION_CODE("근무지코드", false),
        EMPLOYMENT_NOTE("고용메모", false),
        OTHER_NOTE("기타사항", false),
        DEPT_ID("부서ID", false),
        JOB_GRADE_CODE("직급코드", false),
        JOB_TITLE_CODE("직책코드", false),
        RRN("주민등록번호", false);

        final String label;
        final boolean required;

        Column(String label, boolean required) {
            this.label = label;
            this.required = required;
        }

        String headerText() {
            return required ? label + "*" : label;
        }
    }

    /** 한 파일에 받는 최대 행 수. 법령 기준값이 아니라 한 번에 검증·저장하는 양을 제한하는 운영 값이다. */
    static final int MAX_ROWS = 1000;

    private static final Charset MS949 = Charset.forName("MS949");

    private EmployeeImportCsv() {
    }

    /** 양식 파일: UTF-8 BOM + 머리글 한 줄. 예시 행은 넣지 않는다(지우지 않고 올리면 가짜 사원이 등록되므로). */
    static byte[] template() {
        String header = java.util.Arrays.stream(Column.values()).map(Column::headerText)
                .collect(Collectors.joining(","));
        return ("﻿" + header + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 파일 바이트를 문자열로. BOM 이 있거나 UTF-8 로 읽히면 UTF-8, 아니면 MS949(한글 Windows 엑셀의 기본 CSV 저장 형식).
     */
    static String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, MS949);
        }
    }

    /** RFC 4180 CSV 를 행·칸으로 나눈다(따옴표 안 쉼표·줄바꿈·"" 이스케이프 지원, CRLF·LF 모두 허용). */
    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean any = false; // 현재 행에 글자가 하나라도 있었는가(마지막 빈 줄 무시용)
        int i = 0;
        int n = text.length();
        if (n > 0 && text.charAt(0) == '﻿') {
            i = 1;
        }
        for (; i < n; i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    quoted = true;
                    any = true;
                }
                case ',' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    any = true;
                }
                case '\r' -> {
                    // CRLF 의 CR 은 무시하고 LF 에서 행을 끝낸다. 단독 CR 도 행 끝으로 본다.
                    if (i + 1 < n && text.charAt(i + 1) == '\n') {
                        continue;
                    }
                    any = endRow(rows, row, cell, any);
                    row = new ArrayList<>();
                }
                case '\n' -> {
                    any = endRow(rows, row, cell, any);
                    row = new ArrayList<>();
                }
                default -> {
                    cell.append(c);
                    any = true;
                }
            }
        }
        if (quoted) {
            // 닫는 따옴표가 없으면 뒤 행들이 한 칸에 빨려 들어가므로 일부 표를 돌려주지 않고 거부한다.
            throw new MalformedCsvException("따옴표(\")로 시작한 칸이 닫히지 않았어요. 파일 형식을 확인해 주세요");
        }
        if (any || cell.length() > 0) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    /** CSV 형식 자체가 깨져 행·칸으로 나눌 수 없을 때. 메시지는 사용자에게 그대로 보여 준다(칸 값은 넣지 않음). */
    static final class MalformedCsvException extends RuntimeException {
        MalformedCsvException(String message) {
            super(message);
        }
    }

    private static boolean endRow(List<List<String>> rows, List<String> row, StringBuilder cell, boolean any) {
        row.add(cell.toString());
        cell.setLength(0);
        rows.add(row);
        return false;
    }

    /** 머리글 칸을 열로. '*'와 앞뒤 공백을 떼고 비교한다. 모르는 머리글이면 null. */
    static Column columnOf(String header) {
        String h = header.strip();
        if (h.endsWith("*")) {
            h = h.substring(0, h.length() - 1).strip();
        }
        for (Column c : Column.values()) {
            if (c.label.equals(h)) {
                return c;
            }
        }
        return null;
    }
}
