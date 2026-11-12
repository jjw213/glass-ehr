package com.glassehr.org;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 조직 DDL(V008) 정적 검사. DB 없이 돈다. 실제 적용은 {@link OrgDeptOracleTest}. */
class OrgDeptSchemaScriptTest {

    private static String sql() throws IOException {
        return new ClassPathResource("db/oracle/V008__org_dept.sql").getContentAsString(StandardCharsets.UTF_8)
                .lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    @Test
    void DDL에_중괄호가_없다() throws IOException {
        // Oracle JDBC 가 {..}를 SQL92 escape 로 해석한다(V006 참고).
        assertThat(sql()).doesNotContain("{").doesNotContain("}");
    }

    @Test
    void 변경유형_CHECK는_enum과_같다() throws IOException {
        Matcher m = Pattern.compile("change_type IN \\(([^)]*)\\)").matcher(sql());
        assertThat(m.find()).isTrue();
        List<String> db = Arrays.stream(m.group(1).split(",")).map(s -> s.strip().replace("'", "")).toList();
        assertThat(db).containsExactlyElementsOf(Arrays.stream(DeptChangeType.values()).map(Enum::name).toList());
    }

    @Test
    void 부서코드_규칙은_DB와_API가_같다() throws IOException {
        assertThat(sql()).contains("REGEXP_LIKE(dept_code, '" + OrgDtos.DEPT_CODE_PATTERN + "', 'c')")
                .contains("dept_code     VARCHAR2(20 CHAR)");
    }
}
