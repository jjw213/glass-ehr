package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 사원 DDL(V009)·데모 시드 정적 검사. DB 없이 돈다. 실제 적용은 {@link EmployeeOracleTest}. */
class EmployeeSchemaScriptTest {

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8)
                .lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    private static String ddl() throws IOException {
        return read("db/oracle/V009__employee.sql");
    }

    private static String seed() throws IOException {
        return read("db/oracle/seed/employee_demo_seed.sql");
    }

    /** CREATE TABLE name ( ... ); 본문. */
    private static String table(String name) throws IOException {
        Matcher m = Pattern.compile("CREATE TABLE " + name + " \\((.*?)\\n\\);", Pattern.DOTALL).matcher(ddl());
        assertThat(m.find()).as(name).isTrue();
        return m.group(1);
    }

    private static List<String> checkValues(String column) throws IOException {
        Matcher m = Pattern.compile(column + " IN \\(([^)]*)\\)").matcher(ddl());
        assertThat(m.find()).as(column).isTrue();
        return Arrays.stream(m.group(1).split(",")).map(s -> s.strip().replace("'", "")).toList();
    }

    @Test
    void DDL에_중괄호가_없다() throws IOException {
        // Oracle JDBC 가 {..}를 SQL92 escape 로 해석한다(V006 참고).
        assertThat(ddl()).doesNotContain("{").doesNotContain("}");
        assertThat(read("db/oracle/V010__legal_rate_item_record_retention.sql")).doesNotContain("{");
    }

    @Test
    void 시행령_제20조_기재사항_컬럼이_모두_있다() throws IOException {
        String emp = table("employee");
        // 성명, 성별, 생년월일, 주소, 이력, 종사 업무, 고용·고용갱신일, 계약기간, 그 밖의 고용 사항, 해고·퇴직·사망일과 사유, 그 밖의 사항
        for (String col : List.of("emp_name", "gender", "birth_date", "address", "career_history", "job_description",
                "hire_date", "renewal_date", "contract_start_date", "contract_end_date", "employment_type_code",
                "employment_note", "retire_date", "retire_reason_code", "retire_reason_detail", "other_note")) {
            assertThat(emp).as(col).containsPattern("(?m)^\\s+" + col + "\\s");
        }
        // 제42조 보존기한, 현재 소속·직급·직책
        assertThat(emp).containsPattern("(?m)^\\s+record_retention_until\\s+DATE");
        assertThat(emp).containsPattern("(?m)^\\s+current_dept_id\\s").containsPattern("(?m)^\\s+job_grade_code\\s")
                .containsPattern("(?m)^\\s+job_title_code\\s");
    }

    @Test
    void 사원_테이블에는_주민등록번호_컬럼이_없고_emp_rrn에도_평문_컬럼이_없다() throws IOException {
        assertThat(table("employee").toLowerCase()).doesNotContain("rrn");
        String rrn = table("emp_rrn");
        List<String> cols = rrn.lines().map(String::strip).filter(l -> l.matches("^[a-z_]+\\s.*"))
                .map(l -> l.split("\\s+")[0]).toList();
        assertThat(cols).containsExactlyInAnyOrder("emp_id", "rrn_enc", "rrn_masked", "rrn_hash", "key_version",
                "created_at", "updated_at", "updated_by");
        assertThat(rrn).contains("rrn_enc      RAW(64)").contains("rrn_hash     RAW(32)");
        String log = table("rrn_access_log").toLowerCase();
        assertThat(log.replace("rrn_access_log", "")).doesNotContain("rrn").doesNotContain("masked");
    }

    @Test
    void 사번_마스킹_규칙은_DB와_코드가_같다() throws IOException {
        assertThat(ddl()).contains("REGEXP_LIKE(emp_no, '" + EmpNoGenerator.EMP_NO_PATTERN + "', 'c')");
        assertThat(ddl()).contains("emp_no                  VARCHAR2(12 CHAR)");
        Matcher m = Pattern.compile("REGEXP_LIKE\\(rrn_masked, '([^']+)', 'c'\\)").matcher(ddl());
        assertThat(m.find()).isTrue();
        assertThat(Rrn.parse("000000-0000001").masked()).matches(m.group(1));
    }

    @Test
    void 조회로그_CHECK는_enum과_같다() throws IOException {
        assertThat(checkValues("purpose_code"))
                .containsExactlyElementsOf(Arrays.stream(RrnAccessLog.Purpose.values()).map(Enum::name).toList());
        assertThat(checkValues("result"))
                .containsExactlyElementsOf(Arrays.stream(RrnAccessLog.Result.values()).map(Enum::name).toList());
    }

    @Test
    void 보존_기간_값은_마이그레이션에_없다() throws IOException {
        String v010 = read("db/oracle/V010__legal_rate_item_record_retention.sql");
        assertThat(v010).doesNotContainPattern("(?i)INSERT\\s+INTO\\s+legal_rate\\s*\\(")
                .doesNotContainPattern("(?i)legal_rate_api\\.");
    }

    // ---------------------------------------------------------------- 데모 시드

    private record SeedEmp(String empNo, String site, String name, String gender, LocalDate birth, String address,
            String jobFamily, LocalDate hire, String cStart, String cEnd, String type, int minutes, String location,
            String grade, String title, String deptCode) {
    }

    private static final Pattern SEED_ROW = Pattern.compile(
            "SELECT '(S\\d\\d-\\d{4}-\\d{3})', '(S\\d\\d)', '([^']+)', '([MF])', DATE '([\\d-]+)', '([^']+)', "
                    + "(?:NULL|'[^']*'), '[^']+', '([A-Z_]+)', DATE '([\\d-]+)', (NULL|DATE '[\\d-]+'), "
                    + "(NULL|DATE '[\\d-]+'), '([A-Z_]+)', (\\d+), '([A-Z_]+)', dept_id, '([A-Z_]+)', '([A-Z_]+)' "
                    + "FROM org_dept WHERE dept_code = '([A-Z0-9_]+)';");

    private static List<SeedEmp> seedEmployees() throws IOException {
        String sql = seed();
        List<SeedEmp> rows = new ArrayList<>();
        Matcher m = SEED_ROW.matcher(sql);
        while (m.find()) {
            rows.add(new SeedEmp(m.group(1), m.group(2), m.group(3), m.group(4), LocalDate.parse(m.group(5)),
                    m.group(6), m.group(7), LocalDate.parse(m.group(8)), m.group(9), m.group(10), m.group(11),
                    Integer.parseInt(m.group(12)), m.group(13), m.group(14), m.group(15), m.group(16)));
        }
        int inserts = (int) Pattern.compile("INSERT INTO employee \\(").matcher(sql).results().count();
        assertThat(rows).as("모든 INSERT 가 형식대로 파싱돼야 함").hasSize(inserts);
        return rows;
    }

    @Test
    void 시드는_30명_이상이고_사번이_겹치지_않는다() throws IOException {
        List<SeedEmp> rows = seedEmployees();
        assertThat(rows.size()).isGreaterThanOrEqualTo(30);
        assertThat(rows.stream().map(SeedEmp::empNo).distinct()).hasSameSizeAs(rows);
        for (SeedEmp e : rows) {
            assertThat(e.empNo()).matches(EmpNoGenerator.EMP_NO_PATTERN).startsWith(e.site() + "-" + e.hire().getYear() + "-");
            assertThat(e.birth()).isBefore(e.hire());
        }
    }

    @Test
    void 시드는_명백한_가짜_값만_쓴다() throws IOException {
        String raw = new ClassPathResource("db/oracle/seed/employee_demo_seed.sql").getContentAsString(StandardCharsets.UTF_8);
        // 주민등록번호 형식(숫자 6자리-숫자 7자리)·전화번호 형식이 없다. emp_rrn 에 넣지 않는다.
        assertThat(raw).doesNotContainPattern("\\d{6}-?\\d{7}").doesNotContainPattern("01\\d-?\\d{3,4}-?\\d{4}");
        assertThat(seed()).doesNotContainPattern("(?i)INSERT INTO emp_rrn");
        for (SeedEmp e : seedEmployees()) {
            assertThat(e.name()).matches("^예시사원\\d\\d$");
            assertThat(e.address()).startsWith("가상시 ");
        }
    }

    @Test
    void 시드_코드는_공통코드_시드에_있고_부서는_시드에서_만든다() throws IOException {
        String codes = read("db/oracle/V007__common_code_seed.sql");
        Map<String, Set<String>> byGroup = new HashMap<>();
        Matcher m = Pattern.compile("\\('([A-Z_]+)', '([A-Z_]+)', '").matcher(codes);
        while (m.find()) {
            byGroup.computeIfAbsent(m.group(1), k -> new HashSet<>()).add(m.group(2));
        }
        Set<String> depts = new HashSet<>();
        Matcher d = Pattern.compile("INSERT INTO org_dept \\(dept_code, valid_from\\) VALUES \\('([A-Z0-9_]+)'").matcher(seed());
        while (d.find()) {
            depts.add(d.group(1));
        }
        for (SeedEmp e : seedEmployees()) {
            assertThat(byGroup.get("JOB_FAMILY")).as(e.empNo()).contains(e.jobFamily());
            assertThat(byGroup.get("EMPLOYMENT_TYPE")).as(e.empNo()).contains(e.type());
            assertThat(byGroup.get("WORK_LOCATION")).as(e.empNo()).contains(e.location());
            assertThat(byGroup.get("JOB_GRADE")).as(e.empNo()).contains(e.grade());
            assertThat(byGroup.get("JOB_TITLE")).as(e.empNo()).contains(e.title());
            assertThat(depts).as(e.empNo()).contains(e.deptCode());
            assertThat(e.minutes()).isPositive();
            if (e.type().equals(EmployeeService.FIXED_TERM)) {
                assertThat(e.cEnd()).as(e.empNo()).isNotEqualTo("NULL");
            }
        }
    }

    @Test
    void 채번_상태는_시드_사번의_연도별_최대_순번과_같다() throws IOException {
        Map<String, Integer> expected = new HashMap<>();
        for (SeedEmp e : seedEmployees()) {
            String[] p = e.empNo().split("-");
            expected.merge(p[0] + "/" + Integer.parseInt(p[1]), Integer.parseInt(p[2]), Math::max);
        }
        Map<String, Integer> actual = new HashMap<>();
        Matcher m = Pattern.compile("INSERT INTO emp_no_seq \\(site_code, hire_year, last_seq\\) VALUES \\('(S\\d\\d)', (\\d{4}), (\\d+)\\);")
                .matcher(seed());
        while (m.find()) {
            actual.put(m.group(1) + "/" + m.group(2), Integer.parseInt(m.group(3)));
        }
        assertThat(actual).isEqualTo(expected);
    }
}
