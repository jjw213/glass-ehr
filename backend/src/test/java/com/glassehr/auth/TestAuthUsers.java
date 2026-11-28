package com.glassehr.auth;

import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

/**
 * WebMvc 테스트용 가짜 사용자(DB 없이). 역할별 권한은 V013 마이그레이션의 app_role_permission INSERT 를 그대로 읽는다
 * (테스트용 권한표를 따로 두면 실제 표와 어긋날 수 있으므로).
 *
 * <ul>
 *   <li>hr1 — 인사담당(emp 100)</li>
 *   <li>pay1 — 급여담당(emp 101)</li>
 *   <li>admin1 — 관리자(사원 연결 없음)</li>
 *   <li>group1 — 그룹 관리자 + 관리자(사원 연결 없음). C002 로 전환할 수 있다</li>
 *   <li>emp1 — 일반 사원(emp 5, 부서 30)</li>
 *   <li>head1 — 부서장(emp 7). 맡은 부서 10과 하위부서 11</li>
 *   <li>off1 — 사용 중지(찾을 수 없음과 같음)</li>
 * </ul>
 * 로그 기록기는 Mockito mock 이다(테스트에서 verify).
 */
@TestConfiguration
public class TestAuthUsers {

    public static final long HR_EMP = 100;
    public static final long PAY_EMP = 101;
    public static final long EMP_EMP = 5;
    public static final long HEAD_EMP = 7;
    public static final long HEAD_DEPT = 10;
    public static final long HEAD_SUB_DEPT = 11;

    private static final Pattern ROLE_PERM = Pattern.compile(
            "INSERT INTO app_role_permission \\(role_code, perm_code\\) VALUES \\('([A-Z_]+)', '([A-Z_]+)'\\);");

    /** 역할·권한을 넣는 마이그레이션(V013, V033 GROUP_ADMIN, V052 법인 관리). */
    public static final String[] ROLE_SCRIPTS = {"db/oracle/V013__auth_role_privacy_log.sql",
        "db/oracle/V033__group_admin_enter_switch.sql", "db/oracle/V052__enter_company_manage.sql"};

    /** 역할·권한 마이그레이션을 이어 붙인 SQL. */
    public static String roleSql() {
        StringBuilder sb = new StringBuilder();
        try {
            for (String s : ROLE_SCRIPTS) {
                sb.append(new ClassPathResource(s).getContentAsString(StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }

    /** V013·V033 의 역할 → 권한. */
    public static Map<String, Set<Permission>> v011Matrix() {
        String sql = roleSql();
        Map<String, Set<Permission>> m = new HashMap<>();
        Matcher x = ROLE_PERM.matcher(sql);
        while (x.find()) {
            m.computeIfAbsent(x.group(1), k -> EnumSet.noneOf(Permission.class)).add(Permission.valueOf(x.group(2)));
        }
        return m;
    }

    public static final String C001 = "C001";

    public static AuthUser user(String loginId, Long empId, Set<String> roles, Set<Long> managed, String ip) {
        Map<String, Set<Permission>> matrix = v011Matrix();
        Set<Permission> perms = EnumSet.noneOf(Permission.class);
        for (String r : roles) {
            perms.addAll(matrix.getOrDefault(r, Set.of()));
        }
        return new AuthUser("T001", C001, C001, loginId, empId, roles, perms, managed, ip);
    }

    /** 전환 대상으로 쓸 수 있는 가짜 법인. */
    public static final String C002 = "C002";

    /** 테스트 계정은 모두 C001 법인. 다른 법인으로 찾으면 없음(#119). */
    public static Optional<AuthUser> find(String enterCd, String loginId, String ip) {
        return find(enterCd, loginId, enterCd, ip);
    }

    /**
     * 현재 법인이 주체 법인과 다르면 ENTER_SWITCH 권한이 있고 C001·C002 일 때만 찾는다(DbAuthUserResolver 와 같은 규칙).
     * 전환하면 부서장 범위는 비운다.
     */
    public static Optional<AuthUser> find(String enterCd, String loginId, String current, String ip) {
        Optional<AuthUser> home = C001.equals(enterCd) ? find(loginId, ip) : Optional.empty();
        if (home.isEmpty() || enterCd.equals(current)) {
            return home;
        }
        AuthUser u = home.get();
        if (!u.has(Permission.ENTER_SWITCH) || !(C001.equals(current) || C002.equals(current))) {
            return Optional.empty();
        }
        return Optional.of(new AuthUser("T001", current, u.homeEnterCd(), u.loginId(), u.empId(), u.roles(), u.permissions(),
                Set.of(), ip));
    }

    public static Optional<AuthUser> find(String loginId, String ip) {
        if (loginId == null) {
            return Optional.empty();
        }
        return switch (loginId) {
            case "hr1" -> Optional.of(user("hr1", HR_EMP, Set.of("HR", "EMPLOYEE"), Set.of(), ip));
            case "pay1" -> Optional.of(user("pay1", PAY_EMP, Set.of("PAYROLL", "EMPLOYEE"), Set.of(), ip));
            case "admin1" -> Optional.of(user("admin1", null, Set.of("ADMIN", "EMPLOYEE"), Set.of(), ip));
            case "group1" -> Optional.of(user("group1", null,
                    Set.of(AuthUser.ROLE_GROUP_ADMIN, "ADMIN", "EMPLOYEE"), Set.of(), ip));
            case "emp1" -> Optional.of(user("emp1", EMP_EMP, Set.of("EMPLOYEE"), Set.of(), ip));
            case "head1" -> Optional.of(user("head1", HEAD_EMP, Set.of("DEPT_HEAD", "EMPLOYEE"),
                    Set.of(HEAD_DEPT, HEAD_SUB_DEPT), ip));
            default -> Optional.empty();
        };
    }

    @Bean
    AuthUserResolver testAuthUserResolver() {
        return TestAuthUsers::find;
    }

    @Bean
    PrivacyAccessLogger testPrivacyAccessLogger() {
        return mock(PrivacyAccessLogger.class);
    }
}
