package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 휴일 달력 DDL(V017) 정적 검사. DB 없이 돈다. */
class HolidaySchemaScriptTest {

    private static String sql() throws IOException {
        return new ClassPathResource("db/oracle/V017__holiday_calendar.sql").getContentAsString(StandardCharsets.UTF_8)
                .lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    private static String quotedList(Enum<?>[] values) {
        return Arrays.stream(values).map(v -> "'" + v.name() + "'").collect(Collectors.joining(", "));
    }

    @Test
    void DDL에_중괄호가_없다() throws IOException {
        assertThat(sql()).doesNotContain("{").doesNotContain("}");
    }

    @Test
    void 휴일_종류는_DB와_enum이_같다() throws IOException {
        assertThat(sql()).contains("holiday_kind IN (" + quotedList(HolidayKind.values()) + ")");
    }

    @Test
    void 합의_종류와_근로자대표_구분은_DB와_enum이_같다() throws IOException {
        assertThat(sql()).contains("agreement_kind IN (" + quotedList(WorkerRepAgreement.Kind.values()) + ")")
                .contains("rep_type IN (" + quotedList(WorkerRepAgreement.RepType.values()) + ")");
    }

    @Test
    void 대체된_휴일은_원래_공휴일과_서면합의가_반드시_있다() throws IOException {
        String s = sql();
        assertThat(s).contains("holiday_kind = 'REPLACEMENT' AND replaces_holiday_id IS NOT NULL AND agreement_id IS NOT NULL")
                .contains("REFERENCES worker_rep_agreement (agreement_id)")
                .contains("REFERENCES holiday (holiday_id)")
                .contains("UNIQUE (replaces_holiday_id)");
    }

    @Test
    void 서면합의는_문서_참조가_필수다() throws IOException {
        assertThat(sql()).containsPattern("document_ref\\s+VARCHAR2\\(500 CHAR\\)\\s+NOT NULL")
                .contains("LENGTH(TRIM(document_ref)) > 0");
    }

    @Test
    void 공휴일_행은_출처와_검증일이_있어야_하고_유급이다() throws IOException {
        assertThat(sql()).contains("holiday_kind NOT IN ('PUBLIC', 'SUBSTITUTE') OR (source_ref IS NOT NULL AND verified_date IS NOT NULL)")
                .contains("holiday_kind = 'COMPANY' OR paid_yn = 'Y'");
    }

    @Test
    void 공휴일_날짜_데이터를_마이그레이션에_넣지_않는다() throws IOException {
        // 공휴일 자료는 사람이 넣은 CSV 로만 불러온다(#26 완료 기준).
        assertThat(sql()).doesNotContainPattern("(?i)INSERT\\s+INTO");
    }
}
