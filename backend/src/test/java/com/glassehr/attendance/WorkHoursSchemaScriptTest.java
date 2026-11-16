package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 일별 근로시간 집계 DDL(V022)·MyBatis 매퍼 정적 검사. DB 없이 돈다. */
class WorkHoursSchemaScriptTest {

    private static String sql() throws IOException {
        return new ClassPathResource("db/oracle/V022__attendance_daily_summary.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    private static String quotedList(Enum<?>[] values) {
        return Arrays.stream(values).map(v -> "'" + v.name() + "'").collect(Collectors.joining(", "));
    }

    private static String snake(String camel) {
        return camel.replaceAll("([A-Z])", "_$1").toLowerCase();
    }

    @Test
    void DDL에_중괄호가_없다() throws IOException {
        assertThat(sql()).doesNotContain("{").doesNotContain("}");
    }

    @Test
    void 날_성격과_휴가_종류는_DB와_enum이_같다() throws IOException {
        assertThat(sql()).contains("day_kind IN (" + quotedList(WorkHoursCalculator.DayKind.values()) + ")")
                .contains("leave_kind IN (" + quotedList(WorkHoursCalculator.LeaveKind.values()) + ")");
    }

    @Test
    void 사원_근무일마다_한_행이고_분류_합이_실근로와_같다() throws IOException {
        assertThat(sql()).contains("PRIMARY KEY (emp_id, work_date)")
                .contains("worked_minutes = regular_minutes + overtime_minutes + holiday_minutes + holiday_excess_minutes");
    }

    @Test
    void 행_레코드의_모든_필드가_DDL과_매퍼_열에_있다() throws IOException {
        String ddl = sql();
        String xml = new ClassPathResource("mapper/attendance/DailyWorkSummaryMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);
        for (RecordComponent c : DailyWorkSummaryRow.class.getRecordComponents()) {
            String col = snake(c.getName());
            assertThat(ddl).as(col).containsPattern("\\n\\s+" + col + "\\s");
            assertThat(xml).as(col).contains("column=\"" + col + "\"").contains("#{row." + c.getName());
        }
    }
}
