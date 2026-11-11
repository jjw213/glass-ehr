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

    private static List<String> seedGroups() throws IOException {
        List<String> out = new ArrayList<>();
        Matcher m = GROUP_INSERT.matcher(sql("db/oracle/V007__common_code_seed.sql"));
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static List<Row> seedCodes() throws IOException {
        List<Row> out = new ArrayList<>();
        Matcher m = CODE_INSERT.matcher(sql("db/oracle/V007__common_code_seed.sql"));
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
        String text = sql("db/oracle/V007__common_code_seed.sql");
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
        // 정규식은 엔티티 검증(CodeGroups.CODE_PATTERN)과 같다
        assertThat(ddl).contains("'" + CodeGroups.CODE_PATTERN + "'");
    }
}
