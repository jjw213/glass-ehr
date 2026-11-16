package com.glassehr.retirement;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.retirement.DismissalNoticeChecker.Exemption;
import com.glassehr.retirement.DismissalNoticeChecker.NoticeCheck;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 퇴직 처리·사용증명서 DDL(V022) 정적 검사. DB 없이 돈다. */
class RetirementSchemaScriptTest {

    private static String sql() throws IOException {
        return new ClassPathResource("db/oracle/V022__retirement_process.sql").getContentAsString(StandardCharsets.UTF_8)
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
    void 예고_판정과_적용제외_코드는_DB와_enum이_같다() throws IOException {
        assertThat(sql()).contains("notice_check IN (" + quotedList(NoticeCheck.values()) + ")")
                .contains("notice_exempt_code IN (" + quotedList(Exemption.values()) + ")");
    }

    @Test
    void 해고는_예고일과_서면통지일이_DB에서도_필수다() throws IOException {
        assertThat(sql()).containsPattern("retire_reason_code <> 'DISMISSAL'\\s+OR \\(dismissal_notice_date IS NOT NULL"
                + " AND written_notice_date IS NOT NULL\\)");
        assertThat(sql()).contains("dismissal_notice_date <= retire_date").contains("written_notice_date <= retire_date");
    }

    @Test
    void 사원_1명에_퇴직_처리_1건이고_사원을_참조한다() throws IOException {
        assertThat(sql()).contains("CONSTRAINT uq_retirement_case_emp UNIQUE (emp_id)")
                .contains("CONSTRAINT fk_retirement_case_emp FOREIGN KEY (emp_id) REFERENCES employee (emp_id)")
                .contains("CONSTRAINT fk_employment_cert_emp FOREIGN KEY (emp_id) REFERENCES employee (emp_id)");
    }

    @Test
    void 사용증명서_기재항목_목록이_enum과_같다() throws IOException {
        String raw = new ClassPathResource("db/oracle/V022__retirement_process.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String listed = Arrays.stream(RetirementDtos.CertificateItem.values()).map(Enum::name)
                .collect(Collectors.joining(", "));
        assertThat(raw).contains("근로자가 요구한 항목(쉼표 구분): " + listed);
    }

    @Test
    void 값_행과_삭제_문장이_없다() throws IOException {
        assertThat(sql()).doesNotContainPattern("(?i)INSERT\\s+INTO\\s+legal_rate\\s*\\(")
                .doesNotContainPattern("(?i)\\bDELETE\\b");
    }
}
