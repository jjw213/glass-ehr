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

    private static MockHttpServletRequestBuilder switchTo(String ent) {
        return post("/api/auth/switch-enter").header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enterCd\":\"" + ent + "\"}");
    }

    @Test
    void 일반_계정이_법인_전환을_부르면_403이고_현재_법인은_그대로다() throws Exception {
        for (String id : new String[] {"emp1", "hr1", "admin1"}) {
            MockHttpSession s = loginAs(id);
            mvc.perform(switchTo("C002").session(s)).andExpect(status().isForbidden());
            assertThat(s.getAttribute(AuthInterceptor.SESSION_CURRENT_ENTER_CD)).isEqualTo(A);
        }
        mvc.perform(switchTo("C002").header(AuthInterceptor.ACTOR_ID_HEADER, "emp1")).andExpect(status().isForbidden());
        verify(service, never()).switchEnter(any(), any(), any());
    }

    @Test
    void 그룹_관리자는_전환하면_다른_법인_데이터를_본다() throws Exception {
        MockHttpSession s = loginAs("group1");
        when(service.switchEnter(any(), eq("C002"), any())).thenReturn(true);
        mvc.perform(switchTo(" C002 ").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enterCd").value("C002")).andExpect(jsonPath("$.homeEnterCd").value(A));
        assertThat(s.getAttribute(AuthInterceptor.SESSION_CURRENT_ENTER_CD)).isEqualTo("C002");
        // 주체 법인·아이디는 그대로
        assertThat(s.getAttribute(AuthInterceptor.SESSION_ENTER_CD)).isEqualTo(A);
        assertThat(s.getAttribute(AuthInterceptor.SESSION_LOGIN_ID)).isEqualTo("group1");
        verify(service).switchEnter(org.mockito.ArgumentMatchers.argThat(u -> "group1".equals(u.loginId())),
                eq("C002"), any());
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enterCd").value("C002")).andExpect(jsonPath("$.homeEnterCd").value(A))
                .andExpect(jsonPath("$.loginId").value("group1"));
        // 업무 조회는 현재 법인(C002) 조건으로 간다.
        when(privacyLogs.search(eq("C002"), any(), any(), any(), any()))
                .thenReturn(new PrivacyLogService.LogPage(null, null, java.util.List.of(), false));
        mvc.perform(get("/api/admin/privacy-logs").session(s)).andExpect(status().isOk());
        verify(privacyLogs).search(eq("C002"), any(), any(), any(), any());
        verify(privacyLogs, never()).search(eq(A), any(), any(), any(), any());

        // 다시 주체 법인으로
        when(service.switchEnter(any(), eq(A), any())).thenReturn(true);
        mvc.perform(switchTo(A).session(s)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(s)).andExpect(jsonPath("$.enterCd").value(A));
    }

    @Test
    void 없는_법인이나_사용안함_법인으로는_전환되지_않고_404() throws Exception {
        MockHttpSession s = loginAs("group1");
        when(service.switchEnter(any(), anyString(), any())).thenReturn(false);
        mvc.perform(switchTo("ZZ99").session(s)).andExpect(status().isNotFound());
        assertThat(s.getAttribute(AuthInterceptor.SESSION_CURRENT_ENTER_CD)).isEqualTo(A);
        mvc.perform(post("/api/auth/switch-enter").session(s)
                .header(AuthInterceptor.CSRF_HEADER, AuthInterceptor.CSRF_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        // CSRF 헤더가 없으면 403
        mvc.perform(post("/api/auth/switch-enter").session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enterCd\":\"C002\"}")).andExpect(status().isForbidden());
    }

    @Test
    void 세션의_현재_법인을_조작해도_전환_권한이_없으면_401() throws Exception {
        MockHttpSession s = loginAs("hr1");
        s.setAttribute(AuthInterceptor.SESSION_CURRENT_ENTER_CD, "C002");
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isUnauthorized());
        // 그룹 관리자라도 허용되지 않은 법인이면 401
        MockHttpSession g = loginAs("group1");
        g.setAttribute(AuthInterceptor.SESSION_CURRENT_ENTER_CD, "C009");
        mvc.perform(get("/api/auth/me").session(g)).andExpect(status().isUnauthorized());
    }

    @Test
    void test_프로필_X_Actor_Enter로_다른_법인이면_사용자를_찾지_않는다() throws Exception {
        mvc.perform(get("/api/auth/me").header(AuthInterceptor.ACTOR_ID_HEADER, "hr1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enterCd").value(A));
        mvc.perform(get("/api/auth/me").header(AuthInterceptor.ACTOR_ID_HEADER, "hr1")
                .header(AuthInterceptor.ACTOR_ENTER_HEADER, "C002")).andExpect(status().isUnauthorized());
    }
}
