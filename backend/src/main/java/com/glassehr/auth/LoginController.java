package com.glassehr.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인·로그아웃(#76, docs/auth-design.md 8장). 세션은 Spring Session JDBC(Oracle SPRING_SESSION)에 저장하고,
 * 화면에는 HttpOnly·SameSite=Lax 쿠키(GHR_SESSION)만 간다.
 * <ul>
 *   <li>POST /api/auth/login {loginId, password} — 200 {loginId}, 400 입력 없음, 401 아이디·비밀번호 불일치, 423 잠김</li>
 *   <li>POST /api/auth/logout — 204. 세션이 없어도 204.</li>
 * </ul>
 * 둘 다 {@link PublicApi}지만 CSRF 헤더({@code X-Requested-With})는 AuthInterceptor 가 검사한다.
 */
@RestController
public class LoginController {

    /** 입력 길이 상한(app_user.login_id 50자, 비밀번호는 BCrypt 계산량을 키우는 긴 입력을 막는 값). */
    static final int MAX_LOGIN_ID = 50;
    static final int MAX_PASSWORD = 200;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final LoginService service;

    public LoginController(LoginService service) {
        this.service = service;
    }

    /** 비밀번호는 toString 에 나오지 않게 한다(로그에 남지 않게). */
    public record LoginRequest(String loginId, String password) {
        @Override
        public String toString() {
            return "LoginRequest[loginId=" + loginId + "]";
        }
    }

    public record LoginResponse(String loginId) {
    }

    @PostMapping("/api/auth/login")
    @PublicApi
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest body, HttpServletRequest req) {
        if (body == null || body.loginId() == null || body.loginId().isBlank() || body.password() == null
                || body.password().isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "아이디와 비밀번호를 입력해 주세요");
        }
        if (body.loginId().strip().length() > MAX_LOGIN_ID || body.password().length() > MAX_PASSWORD) {
            return error(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 맞지 않아요");
        }
        LoginService.Result r = service.login(body.loginId(), body.password(), req.getRemoteAddr());
        switch (r.outcome()) {
            case LOCKED:
                return error(HttpStatus.LOCKED, "로그인에 여러 번 실패해 계정이 잠겼어요. "
                        + r.lockedUntil().format(HM) + " 이후 다시 시도해 주세요");
            case BAD_CREDENTIALS:
                return error(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 맞지 않아요");
            default:
                break;
        }
        // 세션 고정 방지: 로그인 전 세션을 버리고 새 세션 ID 로 시작한다.
        HttpSession old = req.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = req.getSession(true);
        session.setAttribute(AuthInterceptor.SESSION_LOGIN_ID, r.loginId());
        // 사용 중지 등으로 그 사용자 세션을 모두 끊을 때 PRINCIPAL_NAME 으로 찾는다.
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, r.loginId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new LoginResponse(r.loginId()));
    }

    @PostMapping("/api/auth/logout")
    @PublicApi
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            if (session.getAttribute(AuthInterceptor.SESSION_LOGIN_ID) instanceof String id) {
                service.logout(id, req.getRemoteAddr());
            }
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
