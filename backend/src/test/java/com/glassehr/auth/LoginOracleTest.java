package com.glassehr.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Oracle 실DB에서 로그인·잠금·로그아웃, Spring Session JDBC 세션 저장, 접속기록(V027)을 검증한다. 실행 조건: {@code ORACLE_IT=true}.
 * #119: 로그인은 법인 + 아이디. 같은 아이디가 두 법인에 있는 경우와 법인 격리(V032)도 본다.
 * 세션 저장은 별도 트랜잭션이라 롤백하지 않고, 테스트마다 새 아이디·새 법인을 쓰고 끝나면 사용자·세션·테스트 법인 행을 지운다
 * (C001 접속기록은 남긴다. 테스트 법인 행은 '기존 행은 모두 C001' 검사(EnterCompanyOracleTest)를 위해 지운다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"glassehr.auth.max-failed-attempts=3", "glassehr.auth.lock-duration=10m",
        "glassehr.tenant.multi-company-enabled=true"})
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class LoginOracleTest {

    private static final String PW = "it-only-pass-1";
    private static final String PW_B = "it-only-pass-2";
    private static final String COOKIE = "GHR_SESSION";
    private static final String A = "C001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PrivacyAccessLogger privacyLogger;

    @Autowired
    private PrivacyLogService privacyLogs;

    private String id;
    /** 테스트 법인(가짜). 매번 새 코드. */
    private String b;
    private String off;

    @BeforeEach
    void createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        id = "itl-" + suffix.toLowerCase();
        b = "ZB" + suffix;
        off = "ZO" + suffix;
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)"
                + " VALUES (?, 'IT 법인 B(예시)', ?, 'Y', 0)", b, "99" + randomDigits());
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)"
                + " VALUES (?, 'IT 사용중지 법인(예시)', ?, 'N', 0)", off, "98" + randomDigits());
        insertUser(A, PW);
    }

    private static String randomDigits() {
        return String.format("%08d", (long) (Math.random() * 100_000_000L));
    }

    private void insertUser(String ent, String password) {
        jdbc.update("INSERT INTO app_user (enter_cd, login_id, active_yn, password_hash, password_changed_at) "
                + "VALUES (?, ?, 'Y', ?, SYSTIMESTAMP)", ent, id, LoginService.hash(password));
    }

    @AfterEach
    void dropUser() {
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME IN (?, ?)", A + ":" + id, b + ":" + id);
        jdbc.update("DELETE FROM app_user_role WHERE login_id = ?", id);
        jdbc.update("DELETE FROM app_user WHERE login_id = ?", id);
        jdbc.update("DELETE FROM login_access_log WHERE enter_cd IN (?, ?)", b, off);
        jdbc.update("DELETE FROM privacy_access_log WHERE enter_cd = ? OR accessor_enter_cd = ?", b, b);
        jdbc.update("DELETE FROM enter_company WHERE enter_cd IN (?, ?)", b, off);
    }

    private ResultActions login(String ent, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enterCd\":\"" + ent + "\",\"loginId\":\"" + id + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions login(String password) throws Exception {
        return login(A, password);
    }

    private List<String> events(String ent) {
        return jdbc.queryForList("SELECT event_code || ':' || NVL(fail_reason, '-') || ':' || locked_yn "
                + "FROM login_access_log WHERE enter_cd = ? AND login_id = ? ORDER BY log_id", String.class, ent, id);
    }

    private int failures(String ent) {
        return jdbc.queryForObject("SELECT failed_login_count FROM app_user WHERE enter_cd = ? AND login_id = ?",
                Integer.class, ent, id);
    }

    @Test
    void 로그인하면_DB_세션_쿠키로_API에_접근하고_로그아웃하면_거부된다() throws Exception {
        Cookie session = login(PW).andExpect(status().isOk()).andExpect(jsonPath("$.enterCd").value(A))
                .andReturn().getResponse().getCookie(COOKIE);
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class,
                A + ":" + id)).isEqualTo(1);

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value(id)).andExpect(jsonPath("$.enterCd").value(A))
                .andExpect(jsonPath("$.homeEnterCd").value(A));

        mvc.perform(post("/api/auth/logout").cookie(session)
                .header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class,
                A + ":" + id)).isZero();
        assertThat(events(A)).containsExactly("LOGIN_SUCCESS:-:N", "LOGOUT:-:N");
    }

    @Test
    void 같은_아이디가_두_법인에_있으면_각자_비밀번호로_로그인되고_다른_법인_비밀번호로는_실패한다() throws Exception {
        insertUser(b, PW_B);
        jdbc.update("INSERT INTO app_user_role (enter_cd, login_id, role_code) VALUES (?, ?, 'HR')", b, id);

        login(A, PW_B).andExpect(status().isUnauthorized());
        login(b, PW).andExpect(status().isUnauthorized());
        assertThat(failures(A)).isEqualTo(1);
        assertThat(failures(b)).isEqualTo(1);

        Cookie sa = login(A, PW).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);
        Cookie sb = login(b, PW_B).andExpect(status().isOk()).andExpect(jsonPath("$.enterCd").value(b))
                .andReturn().getResponse().getCookie(COOKIE);

        // 법인 A 세션에는 법인 B 계정의 역할(HR)이 섞이지 않는다(app_user_role 법인 격리).
        mvc.perform(get("/api/auth/me").cookie(sa)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enterCd").value(A))
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("HR"))));
        mvc.perform(get("/api/auth/me").cookie(sb)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enterCd").value(b)).andExpect(jsonPath("$.homeEnterCd").value(b))
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.hasItem("HR")));

        // 법인 B 계정만 사용 중지하면 B 세션만 끊긴다(app_user 법인 격리).
        jdbc.update("UPDATE app_user SET active_yn = 'N' WHERE enter_cd = ? AND login_id = ?", b, id);
        mvc.perform(get("/api/auth/me").cookie(sb)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(sa)).andExpect(status().isOk());
    }

    @Test
    void 없는_법인과_사용중지_법인은_일반_로그인_실패와_같은_응답() throws Exception {
        String expected = login(A, "wrong").andExpect(status().isUnauthorized()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // 사용 중지 법인에 같은 아이디·비밀번호 계정이 있어도 로그인되지 않는다.
        insertUser(off, PW);
        String offBody = login(off, PW).andExpect(status().isUnauthorized()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String ghostBody = login(b, PW).andExpect(status().isUnauthorized()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(offBody).isEqualTo(expected);
        assertThat(ghostBody).isEqualTo(expected);
        assertThat(events(off)).containsExactly("LOGIN_FAILURE:UNKNOWN_USER:N");
        assertThat(events(b)).containsExactly("LOGIN_FAILURE:UNKNOWN_USER:N");
    }

    @Test
    void 다른_법인_사원에는_계정을_연결할_수_없다() {
        Long empId = jdbc.queryForObject("SELECT MIN(emp_id) FROM employee WHERE enter_cd = 'C001'", Long.class);
        org.junit.jupiter.api.Assumptions.assumeTrue(empId != null, "C001 사원이 없으면 건너뜀");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO app_user (enter_cd, login_id, emp_id, active_yn) "
                + "VALUES (?, ?, ?, 'Y')", b, id, empId)).hasMessageContaining("ORA-02291");
    }

    @Test
    void 개인정보_조회_로그는_대상_법인_기준으로만_조회된다() {
        AuthUser userB = new AuthUser(b, b, id, null, Set.of("ADMIN"), Set.of(), Set.of(), "127.0.0.1");
        privacyLogger.log(userB, 1L, PrivacyAccessLog.Item.EMP_PERSONAL, PrivacyAccessLog.Action.VIEW,
                PrivacyAccessLog.Result.SUCCESS, "/api/employees/1");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        assertThat(privacyLogs.search(A, today, today, id, null).rows()).isEmpty();
        assertThat(privacyLogs.search(b, today, today, id, null).rows()).singleElement()
                .satisfies(r -> assertThat(r.accessorId()).isEqualTo(id));
        assertThat(jdbc.queryForObject("SELECT accessor_enter_cd FROM privacy_access_log WHERE enter_cd = ? "
                + "AND accessor_id = ?", String.class, b, id)).isEqualTo(b);
    }

    /** #120: 그룹 관리자 법인 전환(V033). 일반 계정은 403, 그룹 관리자는 전환 뒤 다른 법인 데이터를 보고 전환이 접속기록에 남는다. */
    @Test
    void 그룹_관리자만_법인을_전환하고_전환하면_다른_법인_데이터가_보인다() throws Exception {
        AuthUser userB = new AuthUser(b, b, id, null, Set.of("ADMIN"), Set.of(), Set.of(), "127.0.0.1");
        privacyLogger.log(userB, 1L, PrivacyAccessLog.Item.EMP_PERSONAL, PrivacyAccessLog.Action.VIEW,
                PrivacyAccessLog.Result.SUCCESS, "/api/employees/1");
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        jdbc.update("INSERT INTO app_user_role (enter_cd, login_id, role_code) VALUES (?, ?, 'ADMIN')", A, id);

        // 일반(관리자) 계정: 전환 403, 현재 법인 그대로
        Cookie admin = login(PW).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);
        mvc.perform(switchTo(b).cookie(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").cookie(admin)).andExpect(jsonPath("$.enterCd").value(A));

        // 그룹 관리자 역할 부여 → 다시 로그인
        jdbc.update("INSERT INTO app_user_role (enter_cd, login_id, role_code) VALUES (?, ?, 'GROUP_ADMIN')", A, id);
        Cookie group = login(PW).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);
        mvc.perform(get("/api/admin/privacy-logs").param("from", today).param("to", today).param("accessorId", id)
                .cookie(group)).andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(0));
        mvc.perform(switchTo(off).cookie(group)).andExpect(status().isNotFound());
        mvc.perform(switchTo(b).cookie(group)).andExpect(status().isOk()).andExpect(jsonPath("$.enterCd").value(b))
                .andExpect(jsonPath("$.homeEnterCd").value(A));
        mvc.perform(get("/api/auth/me").cookie(group)).andExpect(jsonPath("$.enterCd").value(b))
                .andExpect(jsonPath("$.homeEnterCd").value(A));
        mvc.perform(get("/api/admin/privacy-logs").param("from", today).param("to", today).param("accessorId", id)
                .cookie(group)).andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(1));
        assertThat(jdbc.queryForList("SELECT target_enter_cd FROM login_access_log WHERE enter_cd = ? AND login_id = ? "
                + "AND event_code = 'ENTER_SWITCH'", String.class, A, id)).containsExactly(b);

        // 역할을 회수하면 전환한 세션은 쓰지 못한다
        jdbc.update("DELETE FROM app_user_role WHERE enter_cd = ? AND login_id = ? AND role_code = 'GROUP_ADMIN'", A, id);
        mvc.perform(get("/api/auth/me").cookie(group)).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder switchTo(String ent) {
        return post("/api/auth/switch-enter").header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enterCd\":\"" + ent + "\"}");
    }

    @Test
    void 틀리면_401_설정_횟수에서_잠기고_잠긴_동안은_맞는_비밀번호도_423() throws Exception {
        login("wrong-1").andExpect(status().isUnauthorized());
        login("wrong-2").andExpect(status().isUnauthorized());
        login("wrong-3").andExpect(status().isLocked());
        login(PW).andExpect(status().isLocked());
        assertThat(events(A)).containsExactly("LOGIN_FAILURE:BAD_PASSWORD:N", "LOGIN_FAILURE:BAD_PASSWORD:N",
                "LOGIN_FAILURE:BAD_PASSWORD:Y", "LOGIN_FAILURE:LOCKED:N");
        assertThat(failures(A)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE enter_cd = ? AND login_id = ? "
                + "AND locked_until > SYSTIMESTAMP", Integer.class, A, id)).isEqualTo(1);

        // 잠금 시각이 지나면 다시 로그인할 수 있고 실패 횟수가 지워진다.
        jdbc.update("UPDATE app_user SET locked_until = SYSTIMESTAMP - INTERVAL '1' MINUTE "
                + "WHERE enter_cd = ? AND login_id = ?", A, id);
        login(PW).andExpect(status().isOk());
        assertThat(failures(A)).isZero();
    }

    @Test
    void 없는_아이디도_접속기록에_남고_비밀번호는_남지_않는다() throws Exception {
        String ghost = id + "-x";
        mvc.perform(post("/api/auth/login").header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enterCd\":\"C001\",\"loginId\":\"" + ghost + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForList("SELECT fail_reason FROM login_access_log WHERE enter_cd = 'C001' "
                + "AND login_id = ?", String.class, ghost)).containsExactly("UNKNOWN_USER");
        Integer leaked = jdbc.queryForObject("SELECT COUNT(*) FROM login_access_log WHERE login_id LIKE ? "
                + "AND (login_id LIKE ? OR client_ip LIKE ?)", Integer.class, id + "%", "%" + PW + "%", "%" + PW + "%");
        assertThat(leaked).isZero();
    }

    @Test
    void 사용_중지하면_남은_세션도_거부된다() throws Exception {
        Cookie session = login(PW).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);
        jdbc.update("UPDATE app_user SET active_yn = 'N' WHERE enter_cd = ? AND login_id = ?", A, id);
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void 법인을_사용_중지하면_남은_세션도_거부된다() throws Exception {
        insertUser(b, PW_B);
        Cookie session = login(b, PW_B).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);
        jdbc.update("UPDATE enter_company SET use_yn = 'N' WHERE enter_cd = ?", b);
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }
}
