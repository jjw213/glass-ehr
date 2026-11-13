package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 서면합의서 DDL(V019) 정적 검사. DB 없이 돈다. */
class LaborAgreementSchemaScriptTest {

    private static String sql() throws IOException {
        return new ClassPathResource("db/oracle/V019__labor_agreement.sql").getContentAsString(StandardCharsets.UTF_8)
                .lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    private static String inList(Enum<?>[] values) {
        return Arrays.stream(values).map(v -> "'" + v.name() + "'").collect(Collectors.joining(", "));
    }

    @Test
    void DDL에_중괄호가_없다() throws IOException {
        assertThat(sql()).doesNotContain("{").doesNotContain("}");
    }

    @Test
    void 합의_유형은_DB와_코드가_같다() throws IOException {
        String s = sql().replaceAll("\\s+", " ");
        assertThat(s).contains("agreement_type IN (" + inList(AgreementType.values()) + ")");
    }

    @Test
    void 대상_범위와_근로자대표_구분은_DB와_코드가_같다() throws IOException {
        String s = sql();
        for (LaborAgreement.Scope v : LaborAgreement.Scope.values()) {
            assertThat(s).contains("scope_type = '" + v.name() + "'");
        }
        for (LaborAgreement.RepType v : LaborAgreement.RepType.values()) {
            assertThat(s).contains("rep_type = '" + v.name() + "'");
        }
    }

    @Test
    void 공백만_있는_필수_글은_DB가_거부한다() throws IOException {
        // Oracle 은 빈 문자열을 NULL 로 본다. LENGTH(TRIM(x)) > 0 은 NULL 이라 통과하므로 IS NOT NULL 로 쓴다(#58 참고).
        assertThat(sql()).contains("TRIM(title) IS NOT NULL").contains("TRIM(target_desc) IS NOT NULL")
                .doesNotContain("LENGTH(TRIM(");
    }

    @Test
    void 첨부는_합의서를_참조하고_근로자대표는_사원을_참조한다() throws IOException {
        assertThat(sql()).contains("REFERENCES labor_agreement (agreement_id)")
                .contains("REFERENCES employee (emp_id)")
                .contains("REFERENCES biz_site (site_code)");
    }
}
