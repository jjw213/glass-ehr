package com.glassehr.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** V011 정적 검사(Oracle 없이). 코드의 enum·권한 원칙과 마이그레이션이 어긋나지 않는지 본다. */
class AuthSchemaScriptTest {

    private static String sql() throws IOException {
        return new ClassPathResource("db/oracle/V011__auth_role_privacy_log.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    private static Set<String> all(String sql, String regex) {
        Matcher m = Pattern.compile(regex).matcher(sql);
        Set<String> out = new LinkedHashSet<>();
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static Set<String> names(Class<? extends Enum<?>> e) {
        return Arrays.stream(e.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    private static Set<String> checkList(String sql, String constraint) {
        Matcher m = Pattern.compile(constraint + " CHECK \\(\\w+ IN \\(([^)]*)\\)\\)").matcher(sql);
        assertThat(m.find()).as(constraint).isTrue();
        return Arrays.stream(m.group(1).split(",")).map(s -> s.strip().replace("'", "")).collect(Collectors.toSet());
    }

    @Test
    void 권한_코드는_Permission_enum과_같다() throws IOException {
        Set<String> perms = all(sql(), "INSERT INTO app_permission \\(perm_code, perm_name\\) VALUES \\('([A-Z_]+)'");
        assertThat(perms).isEqualTo(names(Permission.class));
    }

    @Test
    void 역할은_다섯_가지이고_자동_역할은_직접_부여할_수_없다() throws IOException {
        String sql = sql();
        Set<String> roles = all(sql, "INSERT INTO app_role \\(role_code, role_name, sort_order\\) VALUES \\('([A-Z_]+)'");
        assertThat(roles).containsExactlyInAnyOrder("ADMIN", "HR", "PAYROLL", "DEPT_HEAD", "EMPLOYEE");
        assertThat(sql).contains("CHECK (role_code NOT IN ('DEPT_HEAD', 'EMPLOYEE'))");
    }

    @Test
    void 역할별_권한_원칙() {
        Map<String, Set<Permission>> m = TestAuthUsers.v011Matrix();
        // 주민등록번호 원문은 인사·급여 담당만(docs/rrn-design.md 3장, RequestActor.canRevealRrn 과 같음)
        m.forEach((role, ps) -> assertThat(ps.contains(Permission.RRN_REVEAL))
                .as(role).isEqualTo(role.equals("HR") || role.equals("PAYROLL")));
        // 로그 조회는 관리자만
        m.forEach((role, ps) -> assertThat(ps.contains(Permission.PRIVACY_LOG_READ))
                .as(role).isEqualTo(role.equals("ADMIN")));
        assertThat(m.get("DEPT_HEAD")).containsExactly(Permission.EMP_READ_DEPT);
        assertThat(m.get("EMPLOYEE")).containsExactly(Permission.EMP_READ_SELF);
        assertThat(m.get("PAYROLL")).doesNotContain(Permission.EMP_WRITE, Permission.ORG_WRITE, Permission.CODE_WRITE);
    }

    @Test
    void 로그_CHECK_값은_enum과_같다() throws IOException {
        String sql = sql();
        assertThat(checkList(sql, "ck_privacy_log_item")).isEqualTo(names(PrivacyAccessLog.Item.class));
        assertThat(checkList(sql, "ck_privacy_log_action")).isEqualTo(names(PrivacyAccessLog.Action.class));
        assertThat(checkList(sql, "ck_privacy_log_result")).isEqualTo(names(PrivacyAccessLog.Result.class));
    }

    @Test
    void 로그는_지우는_구문이_없고_중괄호를_쓰지_않는다() throws IOException {
        String sql = sql();
        assertThat(sql).doesNotContain("{").doesNotContain("}");
        assertThat(sql.toUpperCase()).doesNotContain("DELETE ").doesNotContain("TRUNCATE ");
        // 대상 사원에 FK 가 없어야 존재하지 않는 사원 조회 시도도 남길 수 있다.
        int start = sql.indexOf("CREATE TABLE privacy_access_log");
        String table = sql.substring(start, sql.indexOf(");", start));
        assertThat(table).doesNotContain("FOREIGN KEY");
    }
}
