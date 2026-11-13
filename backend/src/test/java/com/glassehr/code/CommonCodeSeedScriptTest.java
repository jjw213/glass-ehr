package com.glassehr.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 공통코드 DDL(V006)·시드(V007)의 정적 검사. DB 없이 돈다. 실제 적용은 {@link CommonCodeOracleTest}. */
class CommonCodeSeedScriptTest {

    private static final Pattern GROUP_INSERT = Pattern.compile(
            "INSERT INTO common_code_group \\([^)]*\\) VALUES\\s*\\('([^']*)',\\s*'([^']*)'");
    private static final Pattern CODE_INSERT = Pattern.compile(
            "INSERT INTO common_code \\([^)]*\\) VALUES\\s*\\('([^']*)',\\s*'([^']*)',\\s*'([^']*)'");

    private record Row(String group, String code, String name) {
    }

    private static String sql(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8)
                .lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    /** 공통코드 그룹·코드를 넣는 마이그레이션(적용 순서). V014는 발령 유형(#58). */
    private static final List<String> SEED_FILES = List.of(
            "db/oracle/V007__common_code_seed.sql", "db/oracle/V014__hr_appointment.sql");

    /** 시드 파일들의 INSERT 문만 이어 붙인다(V014의 CREATE 문은 빼고). */
    private static String seedSql() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String f : SEED_FILES) {
            String text = sql(f);
            int first = text.indexOf("INSERT INTO");
            sb.append(first < 0 ? "" : text.substring(first)).append('\n');
        }
        return sb.toString();
    }

    private static List<String> seedGroups() throws IOException {
        List<String> out = new ArrayList<>();
        Matcher m = GROUP_INSERT.matcher(seedSql());
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static List<Row> seedCodes() throws IOException {
        List<Row> out = new ArrayList<>();
        Matcher m = CODE_INSERT.matcher(seedSql());
        while (m.find()) {
            out.add(new Row(m.group(1), m.group(2), m.group(3)));
        }
        return out;
    }

    @Test
    void 시드_그룹은_CodeGroups와_같다() throws IOException {
        assertThat(seedGroups()).containsExactlyElementsOf(CodeGroups.ALL);
    }

    @Test
    void 모든_INSERT_문을_빠짐없이_읽었다() throws IOException {
        String text = seedSql();
        long inserts = Pattern.compile("INSERT INTO").matcher(text).results().count();
        assertThat(seedGroups().size() + seedCodes().size()).isEqualTo((int) inserts);
    }

    @Test
    void 그룹마다_코드가_있고_코드값은_형식에_맞고_겹치지_않는다() throws IOException {
        List<Row> rows = seedCodes();
        Set<String> seen = new HashSet<>();
        for (Row r : rows) {
            assertThat(CodeGroups.ALL).contains(r.group());
            assertThat(r.code()).matches(CodeGroups.CODE_PATTERN);
            assertThat(r.name()).isNotBlank();
            assertThat(seen.add(r.group() + "/" + r.code())).as("중복 " + r).isTrue();
        }
        for (String g : CodeGroups.ALL) {
            assertThat(rows).as(g).anyMatch(r -> r.group().equals(g));
        }
    }

    @Test
    void 고용형태는_정규_기간제_단시간() throws IOException {
        assertThat(seedCodes().stream().filter(r -> r.group().equals(CodeGroups.EMPLOYMENT_TYPE)).map(Row::code))
                .containsExactly("REGULAR", "FIXED_TERM", "PART_TIME");
    }

    @Test
    void DDL은_사용여부_정렬순서_컬럼과_검사제약을_갖는다() throws IOException {
        String ddl = sql("db/oracle/V006__common_code.sql");
        for (String table : List.of("common_code_group", "common_code")) {
            String body = ddl.substring(ddl.indexOf("CREATE TABLE " + table + " ("));
            body = body.substring(0, body.indexOf(");"));
            assertThat(body).containsPattern("sort_order\\s+NUMBER\\(5\\)\\s+DEFAULT 0\\s+NOT NULL");
            assertThat(body).containsPattern("use_yn\\s+VARCHAR2\\(1 CHAR\\)\\s+DEFAULT 'Y' NOT NULL");
            assertThat(body).contains("CHECK (use_yn IN ('Y', 'N'))");
        }
        // 코드값 규칙 = 문자 CHECK + 컬럼 길이 30자. 둘을 합치면 CodeGroups.CODE_PATTERN 과 같다.
        assertThat(ddl).contains("CHECK (REGEXP_LIKE(group_code, '" + DB_CODE_REGEX + "', 'c'))");
        assertThat(ddl).contains("CHECK (REGEXP_LIKE(code, '" + DB_CODE_REGEX + "', 'c'))");
        assertThat(ddl).containsPattern("group_code\\s+VARCHAR2\\(30 CHAR\\)\\s+NOT NULL");
        assertThat(ddl).containsPattern("\\bcode\\s+VARCHAR2\\(30 CHAR\\)\\s+NOT NULL");
    }

    /** DB CHECK 에 쓰는 문자 규칙. 길이는 컬럼(VARCHAR2(30 CHAR))이 막는다. */
    private static final String DB_CODE_REGEX = "^[A-Z][A-Z0-9_]*$";

    @Test
    void DDL에는_중괄호가_없다() throws IOException {
        // Oracle JDBC는 문장 속 {..}를 SQL92 escape 로 해석해 JDBC로 DDL을 실행할 때 실패할 수 있다.
        assertThat(sql("db/oracle/V006__common_code.sql")).doesNotContain("{").doesNotContain("}");
    }

    @Test
    void DB_코드값_규칙은_CODE_PATTERN과_같다() {
        Pattern db = Pattern.compile(DB_CODE_REGEX);
        Pattern app = Pattern.compile(CodeGroups.CODE_PATTERN);
        List<String> samples = List.of("A", "STAFF", "JOB_GRADE", "A1_B2", "Z".repeat(30), "Z".repeat(31),
                "", "1A", "_A", "a", "Ab", "A-B", "A B", "가");
        for (String s : samples) {
            boolean dbOk = db.matcher(s).matches() && s.length() <= 30;
            assertThat(app.matcher(s).matches()).as(s).isEqualTo(dbOk);
        }
    }
}
