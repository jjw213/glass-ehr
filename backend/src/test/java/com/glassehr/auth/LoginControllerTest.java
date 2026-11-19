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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 로그인·로그아웃 API, 세션 식별, CSRF 헤더, X-Actor-Id 프로필 제한(#76). 서비스는 mock. */
@WebMvcTest(controllers = {LoginController.class, AuthController.class})
@Import(TestAuthUsers.class)
class LoginControllerTest {

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

    private MockHttpSession loginAs(String id) throws Exception {
        when(service.login(eq(id), anyString(), any())).thenReturn(new LoginService.Result(LoginService.Outcome.SUCCESS, id, null));
        return (MockHttpSession) mvc.perform(login("{\"loginId\":\"" + id + "\",\"password\":\"pw\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.loginId").value(id))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void 로그인하면_세션으로_사용자를_찾는다() throws Exception {
        MockHttpSession s = loginAs("hr1");
        assertThat(s.getAttribute(AuthInterceptor.SESSION_LOGIN_ID)).isEqualTo("hr1");
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk()).andExpect(jsonPath("$.loginId").value("hr1"));
        // 세션이 있으면 X-Actor-Id 헤더는 쓰지 않는다(다른 사람으로 바꿔 끼울 수 없음).
        mvc.perform(get("/api/auth/me").session(s).header(AuthInterceptor.ACTOR_ID_HEADER, "admin1"))
                .andExpect(jsonPath("$.loginId").value("hr1"));
    }

    @Test
    void 로그인하면_이전_세션을_버리고_새_세션을_만든다() throws Exception {
        when(service.login(eq("hr1"), anyString(), any())).thenReturn(new LoginService.Result(LoginService.Outcome.SUCCESS, "hr1", null));
        MockHttpSession before = new MockHttpSession();
        var after = mvc.perform(login("{\"loginId\":\"hr1\",\"password\":\"pw\"}").session(before))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        assertThat(before.isInvalid()).isTrue();
        assertThat(after).isNotSameAs(before);
    }

    @Test
    void 입력이_없으면_400_틀리면_401_잠기면_423() throws Exception {
        mvc.perform(login("{\"loginId\":\" \",\"password\":\"pw\"}")).andExpect(status().isBadRequest());
        mvc.perform(login("{\"loginId\":\"hr1\"}")).andExpect(status().isBadRequest());
        verify(service, never()).login(any(), any(), any());

        when(service.login(eq("hr1"), eq("bad"), any()))
                .thenReturn(new LoginService.Result(LoginService.Outcome.BAD_CREDENTIALS, "hr1", null));
        mvc.perform(login("{\"loginId\":\"hr1\",\"password\":\"bad\"}")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("아이디 또는 비밀번호가 맞지 않아요"));

        when(service.login(eq("hr1"), eq("bad2"), any())).thenReturn(new LoginService.Result(LoginService.Outcome.LOCKED,
                "hr1", LocalDateTime.of(2026, 11, 19, 9, 30)));
        var res = mvc.perform(login("{\"loginId\":\"hr1\",\"password\":\"bad2\"}")).andExpect(status().isLocked())
                .andReturn();
        assertThat(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("09:30");
        assertThat(res.getRequest().getSession(false)).isNull();
    }

    @Test
    void CSRF_헤더가_없으면_로그인_로그아웃_변경요청을_거부한다() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"hr1\",\"password\":\"pw\"}")).andExpect(status().isForbidden());
        verify(service, never()).login(any(), any(), any());
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
        verify(service).logout(eq("hr1"), any());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        // 세션 없이 로그아웃해도 204, 기록은 남기지 않는다.
        mvc.perform(post("/api/auth/logout").header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    void test_프로필이_아니면_X_Actor_Id_헤더를_무시한다() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/me");
        req.addHeader(AuthInterceptor.ACTOR_ID_HEADER, "hr1");
        assertThat(new AuthInterceptor(null, null, false).loginIdOf(req)).isNull();
        assertThat(new AuthInterceptor(null, null, true).loginIdOf(req)).isEqualTo("hr1");
        req.getSession(true).setAttribute(AuthInterceptor.SESSION_LOGIN_ID, "emp1");
        assertThat(new AuthInterceptor(null, null, false).loginIdOf(req)).isEqualTo("emp1");
        assertThat(new AuthInterceptor(null, null, true).loginIdOf(req)).isEqualTo("emp1");
    }
}
