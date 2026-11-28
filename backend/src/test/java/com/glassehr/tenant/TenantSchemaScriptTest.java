package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 법인(enter_cd) 스키마 정적 검사(Oracle 없이, #118). 설계: docs/tenant-design.md 2장·3장.
 * 모든 마이그레이션의 CREATE TABLE 을 모아, 전체 공통 테이블을 뺀 나머지에 enter_cd 가 있는지 본다.
 * 새 테이블을 만들면서 enter_cd 를 빠뜨리면 이 테스트가 실패한다.
 */
class TenantSchemaScriptTest {

    /** 전체 공통(enter_cd 없음). tenant-design.md 3.1·3.3. */
    static final Set<String> COMMON = Set.of("legal_rate_item", "legal_rate", "app_role", "app_permission",
            "app_role_permission", "spring_session", "spring_session_attributes", "enter_company", "tenant");

    /** 법인별이지만 NULL 허용·FK 없음(없는 법인으로 시도한 로그인 실패도 남긴다, 3.2 ※※·4.4). */
    static final Set<String> NULLABLE_NO_FK = Set.of("login_access_log");

    private static final Pattern CREATE = Pattern.compile("(?i)CREATE TABLE\\s+\"?(\\w+)\"?\\s*\\(");
    private static final Pattern ADD_ENTER =
            Pattern.compile("(?is)^ALTER TABLE\\s+(\\w+)\\s+ADD\\s*\\(\\s*(enter_cd\\s+VARCHAR2\\(10 CHAR\\).*)\\)$");

    /** 마이그레이션 파일명 → 내용(이름순). */
    static Map<String, String> migrations() throws IOException {
        Resource[] rs = new PathMatchingResourcePatternResolver().getResources("classpath:db/oracle/V*.sql");
        Map<String, String> out = new java.util.TreeMap<>();
        for (Resource r : rs) {
            out.put(r.getFilename(), r.getContentAsString(StandardCharsets.UTF_8));
        }
        return out;
    }

    /** 주석 줄을 뺀 문장들. */
    private static List<String> statements(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n")) {
            String t = line.strip();
            if (!t.startsWith("--")) {
                sb.append(line).append('\n');
            }
        }
        List<String> out = new ArrayList<>();
        for (String s : sb.toString().split(";")) {
            if (!s.isBlank()) {
                out.add(s.strip());
            }
        }
        return out;
    }

    /** 모든 마이그레이션에서 만든 테이블(소문자). */
    static Set<String> allTables() throws IOException {
        Set<String> out = new TreeSet<>();
        for (String sql : migrations().values()) {
            Matcher m = CREATE.matcher(sql);
            while (m.find()) {
                out.add(m.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** 법인별 테이블 = 전체 테이블 − 전체 공통. */
    static Set<String> tenantTables() throws IOException {
        Set<String> out = new TreeSet<>(allTables());
        out.removeAll(COMMON);
        return out;
    }

    /** 테이블 → enter_cd 정의(CREATE TABLE 안의 줄 또는 ALTER TABLE ... ADD (enter_cd ...) 본문). */
    private static Map<String, String> enterCdDefinitions() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String sql : migrations().values()) {
            for (String st : statements(sql)) {
                Matcher add = ADD_ENTER.matcher(st);
                if (add.matches()) {
                    out.put(add.group(1).toLowerCase(Locale.ROOT), add.group(2));
                    continue;
                }
                Matcher c = CREATE.matcher(st);
                if (c.find() && st.toLowerCase(Locale.ROOT).matches("(?s).*\\benter_cd\\s+varchar2.*")) {
                    out.put(c.group(1).toLowerCase(Locale.ROOT), st);
                }
            }
        }
        return out;
    }

    @Test
    void 전체_공통을_뺀_모든_테이블에_enter_cd가_있다() throws IOException {
        Set<String> tenant = tenantTables();
        // V001~V030 의 49개 중 공통 7개를 뺀 42개(tenant-design.md 3.2). 새 테이블이 생기면 늘어난다.
        assertThat(tenant).hasSizeGreaterThanOrEqualTo(42).contains("employee", "org_dept", "msg_read");
        Set<String> missing = new TreeSet<>(tenant);
        missing.removeAll(enterCdDefinitions().keySet());
        assertThat(missing).as("enter_cd 가 없는 법인별 테이블").isEmpty();
    }

    @Test
    void 전체_공통_테이블에는_enter_cd를_넣지_않는다() throws IOException {
        Set<String> defined = enterCdDefinitions().keySet();
        assertThat(defined).doesNotContainAnyElementsOf(
                COMMON.stream().filter(t -> !t.equals("enter_company")).toList());
    }

    @Test
    void enter_cd는_NOT_NULL_법인마스터_FK_형식_CHECK를_갖는다() throws IOException {
        Map<String, String> defs = enterCdDefinitions();
        for (String t : tenantTables()) {
            String def = defs.get(t);
            assertThat(def).as(t).isNotNull();
            assertThat(def).as(t + " 형식 CHECK")
                    .contains("REGEXP_LIKE(enter_cd, '^[A-Z0-9]+$', 'c') AND LENGTH(enter_cd) >= 2");
            if (NULLABLE_NO_FK.contains(t)) {
                assertThat(def).as(t).doesNotContain("NOT NULL").doesNotContain("REFERENCES");
            } else {
                assertThat(def).as(t + " NOT NULL").contains("NOT NULL");
                assertThat(def).as(t + " FK").contains("REFERENCES enter_company (enter_cd)");
            }
        }
    }

    /** 마이그레이션을 순서대로 따라가 만든 최종 PK·FK(#129). */
    record Fk(String table, String name, List<String> cols, String refTable, List<String> refCols) {}

    record Keys(Map<String, List<String>> pk, Map<String, Fk> fk) {}

    private static final Pattern TARGET = Pattern.compile("(?is)^(?:CREATE TABLE|ALTER TABLE)\\s+\"?(\\w+)\"?\\b.*");
    private static final Pattern DROP_TABLE = Pattern.compile("(?is)^DROP TABLE\\s+(\\w+).*");
    private static final Pattern NAMED_PK =
            Pattern.compile("(?is)CONSTRAINT\\s+(\\w+)\\s+PRIMARY KEY\\s*\\(([^)]*)\\)");
    private static final Pattern NAMED_FK = Pattern.compile(
            "(?is)CONSTRAINT\\s+(\\w+)\\s+FOREIGN KEY\\s*\\(([^)]*)\\)\\s*REFERENCES\\s+(\\w+)\\s*\\(([^)]*)\\)");
    /**
     * 열 정의 안의 FK: {@code enter_cd ... CONSTRAINT fk_x_enter REFERENCES enter_company (enter_cd)}.
     * 자식 열 이름은 읽지 않고 "?"로 둔다. 법인별 테이블을 이렇게 참조하면 아래 FK 검사에서 실패한다.
     */
    private static final Pattern INLINE_FK =
            Pattern.compile("(?is)CONSTRAINT\\s+(\\w+)\\s+REFERENCES\\s+(\\w+)\\s*\\(([^)]*)\\)");
    private static final Pattern DROP_CONSTRAINT = Pattern.compile("(?is)DROP CONSTRAINT\\s+(\\w+)");

    private static List<String> cols(String s) {
        List<String> out = new ArrayList<>();
        for (String c : s.split(",")) {
            out.add(c.strip().replace("\"", "").toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** 주석(-- 끝까지)을 지우고 ; 로 나눈다. 패키지 본문 등 DDL 이 아닌 문장은 아래 패턴에 걸리지 않는다. */
    private static List<String> ddlStatements(String sql) {
        String noComments = sql.replaceAll("--[^\n]*", "");
        List<String> out = new ArrayList<>();
        for (String s : noComments.split(";")) {
            if (!s.isBlank()) {
                out.add(s.strip());
            }
        }
        return out;
    }

    static Keys finalKeys() throws IOException {
        Map<String, List<String>> pk = new java.util.TreeMap<>();
        Map<String, String> pkName = new java.util.HashMap<>();
        Map<String, Fk> fk = new java.util.TreeMap<>();
        List<String> unparsed = new ArrayList<>();
        for (Map.Entry<String, String> file : migrations().entrySet()) {
            for (String st : ddlStatements(file.getValue())) {
                Matcher dropTable = DROP_TABLE.matcher(st);
                if (dropTable.matches()) {
                    String t = dropTable.group(1).toLowerCase(Locale.ROOT);
                    pk.remove(t);
                    fk.values().removeIf(f -> f.table().equals(t));
                    continue;
                }
                Matcher target = TARGET.matcher(st);
                if (!target.matches()) {
                    continue;
                }
                String t = target.group(1).toLowerCase(Locale.ROOT);
                Matcher d = DROP_CONSTRAINT.matcher(st);
                while (d.find()) {
                    String name = d.group(1).toLowerCase(Locale.ROOT);
                    fk.remove(name);
                    if (name.equals(pkName.get(t))) {
                        pk.remove(t);
                        pkName.remove(t);
                    }
                }
                if (st.toUpperCase(Locale.ROOT).contains("DROP PRIMARY KEY")) {
                    pk.remove(t);
                    pkName.remove(t);
                }
                Matcher p = NAMED_PK.matcher(st);
                int pks = 0;
                while (p.find()) {
                    pks++;
                    pk.put(t, cols(p.group(2)));
                    pkName.put(t, p.group(1).toLowerCase(Locale.ROOT));
                }
                Matcher f = NAMED_FK.matcher(st);
                int fks = 0;
                while (f.find()) {
                    fks++;
                    String name = f.group(1).toLowerCase(Locale.ROOT);
                    fk.put(name, new Fk(t, name, cols(f.group(2)), f.group(3).toLowerCase(Locale.ROOT),
                            cols(f.group(4))));
                }
                Matcher i = INLINE_FK.matcher(st);
                while (i.find()) {
                    fks++;
                    String name = i.group(1).toLowerCase(Locale.ROOT);
                    fk.put(name, new Fk(t, name, List.of("?"), i.group(2).toLowerCase(Locale.ROOT),
                            cols(i.group(3))));
                }
                // 이름 없는 PK·FK 나 이 검사가 모르는 모양이면 알 수 없으므로 실패로 알린다.
                if (count(Pattern.compile("(?i)\\bPRIMARY KEY\\b"), st) != pks
                        || count(Pattern.compile("(?i)\\bREFERENCES\\b"), st) != fks) {
                    unparsed.add(file.getKey() + ": " + st.lines().findFirst().orElse(st));
                }
            }
        }
        assertThat(unparsed).as("PK·FK 를 읽지 못한 문장(이름 있는 CONSTRAINT 로 쓴다)").isEmpty();
        return new Keys(pk, fk);
    }

    @Test
    void 법인별_테이블의_PK_첫_컬럼은_enter_cd다() throws IOException {
        Keys keys = finalKeys();
        List<String> wrong = new ArrayList<>();
        for (String t : tenantTables()) {
            List<String> cols = keys.pk().get(t);
            if (NULLABLE_NO_FK.contains(t)) {
                // login_access_log 는 없는 법인으로 시도한 로그인도 남기므로 PK 를 log_id 로 둔다(3.2 ※※).
                assertThat(cols).as(t).containsExactly("log_id");
                continue;
            }
            if (cols == null || !cols.get(0).equals("enter_cd")) {
                wrong.add(t + " " + cols);
            }
        }
        assertThat(wrong).as("PK 첫 컬럼이 enter_cd 가 아닌 법인별 테이블").isEmpty();
        // 전체 공통 테이블에는 enter_cd 를 키로 넣지 않는다(법인 마스터 자신은 enter_cd 가 PK).
        assertThat(keys.pk().get("enter_company")).containsExactly("enter_cd");
        assertThat(keys.pk().get("app_role")).containsExactly("role_code");
    }

    @Test
    void 법인별_테이블을_참조하는_FK는_enter_cd를_포함한다() throws IOException {
        Keys keys = finalKeys();
        Set<String> tenant = tenantTables();
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Fk f : keys.fk().values()) {
            if (!tenant.contains(f.refTable())) {
                continue;
            }
            checked++;
            int i = f.cols().indexOf("enter_cd");
            if (i < 0 || i >= f.refCols().size() || !f.refCols().get(i).equals("enter_cd")) {
                wrong.add(f.name() + " " + f.table() + f.cols() + " → " + f.refTable() + f.refCols());
            }
        }
        // 3.2 표의 사원·부서·결재·메시지 등 복합 FK 수십 개가 있다.
        assertThat(checked).isGreaterThan(40);
        assertThat(wrong).as("enter_cd 없이 법인별 테이블을 참조하는 FK").isEmpty();
        // 전체 공통 테이블이 법인별 테이블을 참조하면 법인 경계가 깨진다.
        assertThat(keys.fk().values()).filteredOn(f -> COMMON.contains(f.table()) && tenant.contains(f.refTable()))
                .isEmpty();
    }

    @Test
    void V031은_기존_행을_C001로_채우고_C001_법인을_넣는다() throws IOException {
        String sql = new ClassPathResource("db/oracle/V031__enter_company_enter_cd.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).contains("VALUES ('C001', ");
        Matcher m = ADD_ENTER.matcher("");
        int adds = 0;
        for (String st : statements(sql)) {
            if (m.reset(st).matches()) {
                adds++;
                assertThat(m.group(2)).as(m.group(1)).contains("DEFAULT 'C001'");
            }
        }
        assertThat(adds).isEqualTo(42);
        // Oracle JDBC 가 {..}를 escape 로 해석하므로 중괄호를 쓰지 않는다(V006 주석).
        assertThat(sql).doesNotContain("{").doesNotContain("}");
        // 이 단계에서는 PK·UNIQUE·기존 FK 를 바꾸지 않는다.
        assertThat(sql.toUpperCase(Locale.ROOT)).doesNotContain("DROP ");
    }

    @Test
    void V031_제약_이름은_30자_이내이고_겹치지_않는다() throws IOException {
        String sql = new ClassPathResource("db/oracle/V031__enter_company_enter_cd.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("(?i)CONSTRAINT\\s+(\\w+)").matcher(sql);
        List<String> names = new ArrayList<>();
        while (m.find()) {
            names.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        assertThat(names).hasSizeGreaterThan(42).doesNotHaveDuplicates().allMatch(n -> n.length() <= 30);
    }
}
