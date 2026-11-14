package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 출퇴근 원시 기록 DDL(V019) 정적 검사. DB 없이 돈다. */
class PunchSchemaScriptTest {

    private static String sql() throws IOException {
        return new ClassPathResource("db/oracle/V019__attendance_punch.sql").getContentAsString(StandardCharsets.UTF_8)
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
    void 종류_판정_경로는_DB와_enum이_같다() throws IOException {
        assertThat(sql()).contains("punch_type IN (" + quotedList(AttendancePunch.Type.values()) + ")")
                .contains("punch_check IN (" + quotedList(AttendancePunch.Check.values()) + ")")
                .contains("source IN (" + quotedList(AttendancePunch.Source.values()) + ")");
    }

    @Test
    void 사원_참조와_근무일_규칙이_있다() throws IOException {
        assertThat(sql()).contains("REFERENCES employee (emp_id)")
                .contains("work_date = TRUNC(punched_at)")
                .containsPattern("punched_at\\s+TIMESTAMP\\(6\\)\\s+NOT NULL");
    }
}
