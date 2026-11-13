package com.glassehr.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 근로계약 DDL(V012)·항목 등록(V011) 정적 검사. DB 없이 돈다. */
class ContractSchemaScriptTest {

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8)
                .lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    private static String ddl() throws IOException {
        return read("db/oracle/V012__employment_contract.sql");
    }

    @Test
    void DDL에_중괄호가_없다() throws IOException {
        // Oracle JDBC 가 {..}를 SQL92 escape 로 해석한다(V006 참고).
        assertThat(ddl()).doesNotContain("{").doesNotContain("}");
        assertThat(read("db/oracle/V011__legal_rate_item_fixed_term_limit.sql")).doesNotContain("{");
    }

    @Test
    void 엔티티_컬럼이_DDL에_모두_있다() throws IOException {
        String table = ddl();
        for (Field f : EmploymentContract.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null) {
                assertThat(table).as(c.name()).containsPattern("(?m)^\\s+" + c.name() + "\\s");
            }
        }
    }

    @Test
    void 명시사항_컬럼이_모두_있다() throws IOException {
        // 근로기준법 제17조·시행령 제8조, 기간제법 제17조: 계약기간, 임금, 소정근로시간, 근로시간·휴게, 근로일, 휴일, 휴가, 장소, 업무
        String table = ddl();
        for (String col : List.of("start_date", "end_date", "wage_amount", "wage_unit_code", "wage_components",
                "wage_calc_method", "wage_payment_method", "weekly_contract_minutes", "work_hours_text",
                "work_days_text", "holiday_text", "leave_text", "work_place", "job_duty", "written_delivered_date")) {
            assertThat(table).as(col).containsPattern("(?m)^\\s+" + col + "\\s");
        }
        // 금액은 원 단위 정수
        assertThat(table).containsPattern("wage_amount\\s+NUMBER\\(15\\)");
    }

    @Test
    void 임금_단위_CHECK와_요청_검사가_같다() throws Exception {
        Matcher m = Pattern.compile("wage_unit_code IN \\(([^)]*)\\)").matcher(ddl());
        assertThat(m.find()).isTrue();
        List<String> db = Arrays.stream(m.group(1).split(",")).map(s -> s.strip().replace("'", "")).toList();
        jakarta.validation.constraints.Pattern p = ContractDtos.ContractRequest.class.getDeclaredField("wageUnitCode")
                .getAnnotation(jakarta.validation.constraints.Pattern.class);
        assertThat(p).isNotNull();
        assertThat(p.regexp()).isEqualTo("^(" + String.join("|", db) + ")$");
    }

    @Test
    void 삭제_문장이_없다() throws IOException {
        // 근로기준법 제42조: 계약 서류 보존
        assertThat(ddl()).doesNotContainPattern("(?i)\\bDELETE\\b|\\bDROP\\b");
    }
}
