package com.glassehr.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
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
 * 세션 저장은 별도 트랜잭션이라 롤백하지 않고, 테스트마다 새 아이디를 쓰고 끝나면 사용자·세션을 지운다(접속기록은 남긴다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"glassehr.auth.max-failed-attempts=3", "glassehr.auth.lock-duration=10m"})
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class LoginOracleTest {

    private static final String PW = "it-only-pass-1";
    private static final String COOKIE = "GHR_SESSION";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String id;

    @BeforeEach
    void createUser() {
        id = "itl-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO app_user (login_id, active_yn, password_hash, password_changed_at) "
                + "VALUES (?, 'Y', ?, SYSTIMESTAMP)", id, LoginService.hash(PW));
    }

    @AfterEach
    void dropUser() {
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", id);
        jdbc.update("DELETE FROM app_user WHERE login_id = ?", id);
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + id + "\",\"password\":\"" + password + "\"}"));
    }

    private List<String> events() {
        return jdbc.queryForList("SELECT event_code || ':' || NVL(fail_reason, '-') || ':' || locked_yn "
                + "FROM login_access_log WHERE login_id = ? ORDER BY log_id", String.class, id);
    }

    @Test
    void 로그인하면_DB_세션_쿠키로_API에_접근하고_로그아웃하면_거부된다() throws Exception {
        Cookie session = login(PW).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, id))
                .isEqualTo(1);

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.loginId").value(id));

        mvc.perform(post("/api/auth/logout").cookie(session)
                .header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, id))
                .isZero();
        assertThat(events()).containsExactly("LOGIN_SUCCESS:-:N", "LOGOUT:-:N");
    }

    @Test
    void 틀리면_401_설정_횟수에서_잠기고_잠긴_동안은_맞는_비밀번호도_423() throws Exception {
        login("wrong-1").andExpect(status().isUnauthorized());
        login("wrong-2").andExpect(status().isUnauthorized());
        login("wrong-3").andExpect(status().isLocked());
        login(PW).andExpect(status().isLocked());
        assertThat(events()).containsExactly("LOGIN_FAILURE:BAD_PASSWORD:N", "LOGIN_FAILURE:BAD_PASSWORD:N",
                "LOGIN_FAILURE:BAD_PASSWORD:Y", "LOGIN_FAILURE:LOCKED:N");
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM app_user WHERE login_id = ?", Integer.class, id))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE login_id = ? AND locked_until > SYSTIMESTAMP",
                Integer.class, id)).isEqualTo(1);

        // 잠금 시각이 지나면 다시 로그인할 수 있고 실패 횟수가 지워진다.
        jdbc.update("UPDATE app_user SET locked_until = SYSTIMESTAMP - INTERVAL '1' MINUTE WHERE login_id = ?", id);
        login(PW).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM app_user WHERE login_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void 없는_아이디도_접속기록에_남고_비밀번호는_남지_않는다() throws Exception {
        String ghost = id + "-x";
        mvc.perform(post("/api/auth/login").header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"" + ghost + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForList("SELECT fail_reason FROM login_access_log WHERE login_id = ?", String.class, ghost))
                .containsExactly("UNKNOWN_USER");
        Integer leaked = jdbc.queryForObject("SELECT COUNT(*) FROM login_access_log WHERE login_id LIKE ? "
                + "AND (login_id LIKE ? OR client_ip LIKE ?)", Integer.class, id + "%", "%" + PW + "%", "%" + PW + "%");
        assertThat(leaked).isZero();
    }

    @Test
    void 사용_중지하면_남은_세션도_거부된다() throws Exception {
        Cookie session = login(PW).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);
        jdbc.update("UPDATE app_user SET active_yn = 'N' WHERE login_id = ?", id);
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }
}
