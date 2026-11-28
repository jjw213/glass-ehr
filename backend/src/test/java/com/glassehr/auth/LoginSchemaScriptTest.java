package com.glassehr.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 로그인 스키마 정적 검사(Oracle 없이). */
class LoginSchemaScriptTest {

    private static String sql() throws IOException {
        return new ClassPathResource("db/oracle/V027__app_user_login_session.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    private static String tenantSql() throws IOException {
        return new ClassPathResource("db/oracle/V032__app_user_enter_cd.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static Set<String> names(Class<? extends Enum<?>> e) {
        return Arrays.stream(e.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    private static Set<String> inList(String sql, String regex) {
        Matcher m = Pattern.compile(regex).matcher(sql);
        assertThat(m.find()).as(regex).isTrue();
        return Arrays.stream(m.group(1).split(",")).map(s -> s.strip().replace("'", "")).collect(Collectors.toSet());
    }

    private static String switchSql() throws IOException {
        return new ClassPathResource("db/oracle/V033__group_admin_enter_switch.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 접속기록_CHECK_값은_enum과_같다() throws IOException {
        String sql = sql();
        // 이벤트 목록은 V033 이 ENTER_SWITCH 를 더해 다시 만든 제약이 최신이다.
        assertThat(inList(switchSql(), "ck_login_log_event CHECK \\(event_code IN \\(([^)]*)\\)\\)"))
                .isEqualTo(names(LoginAccessLog.Event.class));
        // 실패 사유 목록은 V051(#151)이 TENANT_UNAVAILABLE 을 더해 다시 만든 제약이 최신이다.
        assertThat(sql).contains("fail_reason IN (");
        String latest = new ClassPathResource("db/oracle/V051__tenant.sql").getContentAsString(StandardCharsets.UTF_8);
        assertThat(inList(latest, "fail_reason IN \\(([^)]*)\\)")).isEqualTo(names(LoginAccessLog.FailReason.class));
    }

    @Test
    void 접속기록은_지우는_구문과_FK가_없고_비밀번호_열이_없다() throws IOException {
        String sql = sql();
        assertThat(sql).doesNotContain("{").doesNotContain("}");
        // Spring Session 테이블의 ON DELETE CASCADE 앞부분(app_user·접속기록)만 본다.
        String beforeSession = sql.substring(0, sql.indexOf("CREATE TABLE SPRING_SESSION"));
        assertThat(beforeSession.toUpperCase()).doesNotContain("DELETE ").doesNotContain("TRUNCATE ");
        int start = sql.indexOf("CREATE TABLE login_access_log");
        String table = sql.substring(start, sql.indexOf(");", start));
        assertThat(table).doesNotContain("FOREIGN KEY");
        // 비밀번호(원문·해시) 열이 없어야 한다. 실패 사유 코드 NO_PASSWORD 는 값이라 열 정의만 본다.
        assertThat(table).doesNotContainPattern("(?im)^\\s*\\w*password\\w*\\s+(VARCHAR2|CHAR|CLOB|RAW)");
    }

    @Test
    void 법인_전환_대상은_전환_기록에만_있고_FK가_없다() throws IOException {
        String sql = switchSql();
        assertThat(sql).contains("ALTER TABLE login_access_log DROP CONSTRAINT ck_login_log_event;")
                .contains("(event_code = 'ENTER_SWITCH' AND target_enter_cd IS NOT NULL")
                .contains("OR (event_code <> 'ENTER_SWITCH' AND target_enter_cd IS NULL)")
                .doesNotContain("FOREIGN KEY").doesNotContain("REFERENCES")
                .doesNotContain("{").doesNotContain("}");
    }

    @Test
    void 비밀번호_해시는_BCrypt_형식만_받는다() throws IOException {
        String hash = LoginService.hash("example-only-1");
        Matcher m = Pattern.compile("REGEXP_LIKE\\(password_hash, '([^']+)'").matcher(sql());
        assertThat(m.find()).isTrue();
        assertThat(Pattern.compile(m.group(1).replace("\\$", "\\$")).matcher(hash).find()).isTrue();
        assertThat(Pattern.compile(m.group(1)).matcher("plain-text").find()).isFalse();
    }

    @Test
    void 데모_계정_사번은_사원_데모_시드에_있다() throws IOException {
        String seed = new ClassPathResource("db/oracle/seed/employee_demo_seed.sql").getContentAsString(StandardCharsets.UTF_8);
        for (DemoAccountSeeder.Account a : DemoAccountSeeder.ACCOUNTS) {
            if (a.empNo() != null) {
                assertThat(seed).as(a.loginId()).contains("'" + a.empNo() + "'");
            }
        }
        assertThat(seed.toLowerCase()).doesNotContain("password");
    }

    @Test
    void 사원_미연결_계정은_법인별_사원_유일성_검사에서_제외한다() throws IOException {
        String sql = tenantSql();
        assertThat(sql).contains("CREATE UNIQUE INDEX ux_app_user_emp ON app_user (")
                .contains("CASE WHEN emp_id IS NOT NULL THEN enter_cd END,")
                .contains("CASE WHEN emp_id IS NOT NULL THEN emp_id END")
                .doesNotContain("ADD CONSTRAINT uq_app_user_emp UNIQUE (enter_cd, emp_id)");
    }
}
