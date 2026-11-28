package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.AuthInterceptor;
import com.glassehr.auth.LoginService;
import com.glassehr.auth.TenantUnavailableException;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 고객사(tenant) 마스터와 법인 소속(V051, #151). 설계: docs/saas-design.md 2장. 실행 조건: {@code ORACLE_IT=true}.
 * 테스트마다 새 고객사·법인·계정을 만들고 끝나면 지운다(세션 저장은 별도 트랜잭션이라 롤백하지 않는다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "glassehr.tenant.multi-company-enabled=true")
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class TenantLoginOracleTest {

    private static final String PW = "it-only-pass-1";
    private static final String COOKIE = "GHR_SESSION";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String tenant;
    private String ent;
    private String id;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        tenant = "ZT" + suffix;
        ent = "ZE" + suffix;
        id = "itt-" + suffix.toLowerCase();
        jdbc.update("INSERT INTO tenant (tenant_id, tenant_name, slug, status) VALUES (?, 'IT 고객사(예시)', ?, 'ACTIVE')",
                tenant, "it-" + suffix.toLowerCase());
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order, tenant_id)"
                + " VALUES (?, 'IT 고객사 법인(예시)', ?, 'Y', 0, ?)", ent, "97" + String.format("%08d",
                        (long) (Math.random() * 100_000_000L)), tenant);
        jdbc.update("INSERT INTO app_user (enter_cd, login_id, active_yn, password_hash, password_changed_at) "
                + "VALUES (?, ?, 'Y', ?, SYSTIMESTAMP)", ent, id, LoginService.hash(PW));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", ent + ":" + id);
        jdbc.update("DELETE FROM app_user WHERE enter_cd = ?", ent);
        jdbc.update("DELETE FROM login_access_log WHERE enter_cd = ?", ent);
        jdbc.update("DELETE FROM enter_company WHERE enter_cd = ?", ent);
        jdbc.update("DELETE FROM tenant WHERE tenant_id = ?", tenant);
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enterCd\":\"" + ent + "\",\"loginId\":\"" + id + "\",\"password\":\"" + password + "\"}"));
    }

    private List<String> events() {
        return jdbc.queryForList("SELECT event_code || ':' || NVL(fail_reason, '-') FROM login_access_log "
                + "WHERE enter_cd = ? AND login_id = ? ORDER BY log_id", String.class, ent, id);
    }

    private String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 기존_법인은_모두_T001이고_세션에_고객사가_보인다() throws Exception {
        assertThat(jdbc.queryForObject("SELECT status FROM tenant WHERE tenant_id = 'T001'", String.class))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM enter_company WHERE enter_cd = 'C001'", String.class))
                .isEqualTo("T001");

        Cookie s = login(PW).andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(tenant))
                .andReturn().getResponse().getCookie(COOKIE);
        mvc.perform(get("/api/auth/me").cookie(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenant)).andExpect(jsonPath("$.enterCd").value(ent));
    }

    @Test
    void 정지_해지된_고객사_사용자는_로그인이_거부되고_실패_횟수는_늘지_않는다() throws Exception {
        for (String st : List.of("SUSPENDED", "TERMINATED")) {
            jdbc.update("UPDATE tenant SET status = ? WHERE tenant_id = ?", st, tenant);
            ResultActions r = login(PW).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value(TenantUnavailableException.MESSAGE));
            assertThat(r.andReturn().getRequest().getSession(false)).as(st).isNull();
        }
        // 비밀번호가 틀리면 고객사 상태를 드러내지 않는다(일반 로그인 실패와 같은 응답).
        assertThat(body(login("wrong").andExpect(status().isUnauthorized())))
                .doesNotContain(TenantUnavailableException.MESSAGE);
        assertThat(events()).containsExactly("LOGIN_FAILURE:TENANT_UNAVAILABLE", "LOGIN_FAILURE:TENANT_UNAVAILABLE",
                "LOGIN_FAILURE:BAD_PASSWORD");
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM app_user WHERE enter_cd = ? AND login_id = ?",
                Integer.class, ent, id)).isEqualTo(1);
    }

    @Test
    void 고객사가_정지되면_기존_세션도_401로_끊긴다() throws Exception {
        Cookie s = login(PW).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);
        mvc.perform(get("/api/auth/me").cookie(s)).andExpect(status().isOk());

        jdbc.update("UPDATE tenant SET status = 'SUSPENDED' WHERE tenant_id = ?", tenant);
        mvc.perform(get("/api/auth/me").cookie(s)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(TenantUnavailableException.MESSAGE));
        // 다시 이용 가능해져도 끊긴 세션은 쓰지 못한다(다시 로그인).
        jdbc.update("UPDATE tenant SET status = 'ACTIVE' WHERE tenant_id = ?", tenant);
        mvc.perform(get("/api/auth/me").cookie(s)).andExpect(status().isUnauthorized());
    }

    @Test
    void 체험_고객사는_종료_시각_전까지만_로그인되고_지나면_정지처럼_거부된다() throws Exception {
        // 체험 종료 시각은 Asia/Seoul 기준이라 DB 서버 시간대와 무관하게 애플리케이션 시각으로 넣는다.
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        jdbc.update("UPDATE tenant SET status = 'TRIAL', trial_ends_at = ? WHERE tenant_id = ?", now.plusDays(1), tenant);
        Cookie s = login(PW).andExpect(status().isOk()).andReturn().getResponse().getCookie(COOKIE);

        jdbc.update("UPDATE tenant SET trial_ends_at = ? WHERE tenant_id = ?", now.minusMinutes(1), tenant);
        mvc.perform(get("/api/auth/me").cookie(s)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(TenantUnavailableException.MESSAGE));
        login(PW).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(TenantUnavailableException.MESSAGE));
    }

    @Test
    void 고객사_형식과_slug_유일성과_법인_소속을_DB가_검사한다() {
        for (String bad : List.of("t1", "A", "ABCDEFGHIJK", "A-1")) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO tenant (tenant_id, tenant_name, slug) VALUES (?, '틀림', ?)",
                    bad, "bad-" + tenant.toLowerCase())).as(bad).isInstanceOf(DataAccessException.class);
        }
        for (String bad : List.of("ab", "Upper-" + tenant.toLowerCase(), "under_" + tenant.toLowerCase(),
                "x".repeat(31))) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO tenant (tenant_id, tenant_name, slug) VALUES ('ZZBAD', '틀림', ?)",
                    bad)).as(bad).isInstanceOf(DataAccessException.class);
        }
        String slug = jdbc.queryForObject("SELECT slug FROM tenant WHERE tenant_id = ?", String.class, tenant);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tenant (tenant_id, tenant_name, slug) VALUES ('ZZDUP', '중복', ?)",
                slug)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tenant (tenant_id, tenant_name, slug, status) "
                + "VALUES ('ZZTRI', '체험', 'zz-trial', 'TRIAL')")).isInstanceOf(DataIntegrityViolationException.class);
        // 법인은 고객사 없이 만들 수 없고 없는 고객사에도 넣을 수 없다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no) "
                + "VALUES ('ZZNOT', '고객사 없음', '0000000081')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, tenant_id) "
                + "VALUES ('ZZNOT', '없는 고객사', '0000000082', 'ZZNONE')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
