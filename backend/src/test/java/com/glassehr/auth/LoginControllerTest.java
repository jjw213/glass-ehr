package com.glassehr.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 로그인·로그아웃 API, 세션 식별(법인 포함, #119), CSRF 헤더, X-Actor-Id 프로필 제한(#76). 서비스는 mock. */
@WebMvcTest(controllers = {LoginController.class, AuthController.class})
@Import(TestAuthUsers.class)
class LoginControllerTest {

    private static final String A = "C001";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LoginService service;

    @MockitoBean
    private PrivacyLogService privacyLogs;

    private static MockHttpServletRequestBuilder login(String body) {
        return post("/api/auth/login").header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String body(String ent, String id, String pw) {
        return "{\"enterCd\":\"" + ent + "\",\"loginId\":\"" + id + "\",\"password\":\"" + pw + "\"}";
    }

    private static LoginService.Result ok(String ent, String id) {
        return new LoginService.Result(LoginService.Outcome.SUCCESS, ent, id, null);
    }

    private MockHttpSession loginAs(String id) throws Exception {
        when(service.login(eq(A), eq(id), anyString(), any())).thenReturn(ok(A, id));
        return (MockHttpSession) mvc.perform(login(body(A, id, "pw")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.loginId").value(id))
                .andExpect(jsonPath("$.enterCd").value(A))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void 로그인하면_세션에_법인과_아이디를_넣고_세션으로_사용자를_찾는다() throws Exception {
        MockHttpSession s = loginAs("hr1");
        assertThat(s.getAttribute(AuthInterceptor.SESSION_LOGIN_ID)).isEqualTo("hr1");
        assertThat(s.getAttribute(AuthInterceptor.SESSION_ENTER_CD)).isEqualTo(A);
        assertThat(s.getAttribute(AuthInterceptor.SESSION_CURRENT_ENTER_CD)).isEqualTo(A);
        assertThat(s.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)).isEqualTo("C001:hr1");
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("hr1"))
                .andExpect(jsonPath("$.enterCd").value(A))
                .andExpect(jsonPath("$.homeEnterCd").value(A));
        // 세션이 있으면 X-Actor-Id·X-Actor-Enter 헤더는 쓰지 않는다(다른 사람으로 바꿔 끼울 수 없음).
        mvc.perform(get("/api/auth/me").session(s).header(AuthInterceptor.ACTOR_ID_HEADER, "admin1")
                .header(AuthInterceptor.ACTOR_ENTER_HEADER, "C002"))
                .andExpect(jsonPath("$.loginId").value("hr1")).andExpect(jsonPath("$.enterCd").value(A));
    }

    @Test
    void 법인이_없는_이전_세션이나_현재_법인이_어긋난_세션은_401() throws Exception {
        MockHttpSession old = new MockHttpSession();
        old.setAttribute(AuthInterceptor.SESSION_LOGIN_ID, "hr1");
        mvc.perform(get("/api/auth/me").session(old)).andExpect(status().isUnauthorized());

        MockHttpSession odd = new MockHttpSession();
        odd.setAttribute(AuthInterceptor.SESSION_LOGIN_ID, "hr1");
        odd.setAttribute(AuthInterceptor.SESSION_ENTER_CD, A);
        odd.setAttribute(AuthInterceptor.SESSION_CURRENT_ENTER_CD, "C002");
        mvc.perform(get("/api/auth/me").session(odd)).andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인하면_이전_세션을_버리고_새_세션을_만든다() throws Exception {
        when(service.login(eq(A), eq("hr1"), anyString(), any())).thenReturn(ok(A, "hr1"));
        MockHttpSession before = new MockHttpSession();
        var after = mvc.perform(login(body(A, "hr1", "pw")).session(before))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        assertThat(before.isInvalid()).isTrue();
        assertThat(after).isNotSameAs(before);
    }

    @Test
    void 입력이_없으면_400_틀리면_401_잠기면_423() throws Exception {
        mvc.perform(login("{\"enterCd\":\"C001\",\"loginId\":\" \",\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(login("{\"enterCd\":\"C001\",\"loginId\":\"hr1\"}")).andExpect(status().isBadRequest());
        mvc.perform(login("{\"loginId\":\"hr1\",\"password\":\"pw\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("회사를 선택해 주세요"));
        verify(service, never()).login(any(), any(), any(), any());

        when(service.login(eq(A), eq("hr1"), eq("bad"), any()))
                .thenReturn(new LoginService.Result(LoginService.Outcome.BAD_CREDENTIALS, A, "hr1", null));
        mvc.perform(login(body(A, "hr1", "bad"))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("아이디 또는 비밀번호가 맞지 않아요"));

        when(service.login(eq(A), eq("hr1"), eq("bad2"), any())).thenReturn(new LoginService.Result(
                LoginService.Outcome.LOCKED, A, "hr1", LocalDateTime.of(2026, 11, 19, 9, 30)));
        var res = mvc.perform(login(body(A, "hr1", "bad2"))).andExpect(status().isLocked()).andReturn();
        assertThat(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("09:30");
        assertThat(res.getRequest().getSession(false)).isNull();
    }

    @Test
    void 없는_법인은_아이디_불일치와_같은_401_응답() throws Exception {
        when(service.login(eq("C999"), eq("hr1"), anyString(), any()))
                .thenReturn(new LoginService.Result(LoginService.Outcome.BAD_CREDENTIALS, "C999", "hr1", null));
        var res = mvc.perform(login(body("C999", "hr1", "pw"))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("아이디 또는 비밀번호가 맞지 않아요")).andReturn();
        assertThat(res.getRequest().getSession(false)).isNull();
    }

    @Test
    void CSRF_헤더가_없으면_로그인_로그아웃_변경요청을_거부한다() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(A, "hr1", "pw"))).andExpect(status().isForbidden());
        verify(service, never()).login(any(), any(), any(), any());
        MockHttpSession s = loginAs("hr1");
        mvc.perform(post("/api/auth/logout").session(s)).andExpect(status().isForbidden());
        assertThat(s.isInvalid()).isFalse();
    }

    @Test
    void 로그아웃하면_세션이_끝나고_기록한다() throws Exception {
        MockHttpSession s = loginAs("hr1");
        mvc.perform(post("/api/auth/logout").session(s)
                .header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)).andExpect(status().isNoContent());
        assertThat(s.isInvalid()).isTrue();
        verify(service).logout(eq(A), eq("hr1"), any());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        // 세션 없이 로그아웃해도 204, 기록은 남기지 않는다.
        mvc.perform(post("/api/auth/logout").header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    void test_프로필이_아니면_X_Actor_Id_헤더를_무시한다() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/me");
        req.addHeader(AuthInterceptor.ACTOR_ID_HEADER, "hr1");
        assertThat(new AuthInterceptor(null, null, false).principalOf(req))
                .isEqualTo(new AuthInterceptor.Principal(null, null));
        // X-Actor-Enter 가 없으면 C001
        assertThat(new AuthInterceptor(null, null, true).principalOf(req))
                .isEqualTo(new AuthInterceptor.Principal(A, "hr1"));
        req.addHeader(AuthInterceptor.ACTOR_ENTER_HEADER, "C002");
        assertThat(new AuthInterceptor(null, null, true).principalOf(req))
                .isEqualTo(new AuthInterceptor.Principal("C002", "hr1"));
        var session = req.getSession(true);
        session.setAttribute(AuthInterceptor.SESSION_LOGIN_ID, "emp1");
        session.setAttribute(AuthInterceptor.SESSION_ENTER_CD, A);
        session.setAttribute(AuthInterceptor.SESSION_CURRENT_ENTER_CD, A);
        assertThat(new AuthInterceptor(null, null, false).principalOf(req))
                .isEqualTo(new AuthInterceptor.Principal(A, "emp1"));
        assertThat(new AuthInterceptor(null, null, true).principalOf(req))
                .isEqualTo(new AuthInterceptor.Principal(A, "emp1"));
    }

    @Test
    void test_프로필_X_Actor_Enter로_다른_법인이면_사용자를_찾지_않는다() throws Exception {
        mvc.perform(get("/api/auth/me").header(AuthInterceptor.ACTOR_ID_HEADER, "hr1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enterCd").value(A));
        mvc.perform(get("/api/auth/me").header(AuthInterceptor.ACTOR_ID_HEADER, "hr1")
                .header(AuthInterceptor.ACTOR_ENTER_HEADER, "C002")).andExpect(status().isUnauthorized());
    }
}
