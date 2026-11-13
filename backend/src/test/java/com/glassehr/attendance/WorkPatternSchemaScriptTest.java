package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 근무유형 DDL(V012) 정적 검사. DB 없이 돈다. */
class WorkPatternSchemaScriptTest {

    private static String sql() throws IOException {
        return new ClassPathResource("db/oracle/V012__work_pattern.sql").getContentAsString(StandardCharsets.UTF_8)
                .lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    @Test
    void DDL에_중괄호가_없다() throws IOException {
        // Oracle JDBC 가 {..}를 SQL92 escape 로 해석한다(V006 참고).
        assertThat(sql()).doesNotContain("{").doesNotContain("}");
    }

    @Test
    void 코드_규칙은_DB와_API가_같다() throws IOException {
        assertThat(sql()).contains("REGEXP_LIKE(pattern_code, '" + WorkPatternDtos.CODE_PATTERN + "', 'c')");
    }

    @Test
    void 요일_구분_문자는_DB와_검사기가_같고_주휴일을_DB도_강제한다() throws IOException {
        String s = sql();
        String chars = "" + WorkPatternRules.WORK_DAY + WorkPatternRules.OFF_DAY + WorkPatternRules.WEEKLY_HOLIDAY;
        assertThat(s).contains("REGEXP_LIKE(day_types, '^[" + chars + "]+$', 'c')")
                .contains("LENGTH(day_types) = " + WorkPatternRules.DAYS_PER_WEEK)
                .contains("INSTR(day_types, '" + WorkPatternRules.WEEKLY_HOLIDAY + "') > 0");
    }

    @Test
    void 배정_이력은_사원과_근무유형을_참조하고_시작일이_겹치지_않는다() throws IOException {
        assertThat(sql()).contains("REFERENCES employee (emp_id)")
                .contains("REFERENCES work_pattern (pattern_id)")
                .contains("UNIQUE (emp_id, valid_from)");
    }

    @Test
    void 법정_수치를_DDL에_쓰지_않는다() throws IOException {
        // 한도·휴게 기준은 legal_rate 에서 읽는다. 시각 범위(0~1439)와 요일 수(7) 외의 숫자 CHECK 가 없어야 한다.
        assertThat(sql()).doesNotContainPattern("(?i)minutes\\s*(<=|<|>=)\\s*\\d")
                .doesNotContainPattern("(?i)break_minutes\\s*>=\\s*\\d");
    }
}
