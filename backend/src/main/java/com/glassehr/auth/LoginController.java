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
 *   <li>POST /api/auth/login {enterCd, loginId, password} — 200 {enterCd, loginId}, 400 입력 없음,
 *       401 법인·아이디·비밀번호 불일치(없는 법인·사용 중지 법인도 같은 응답), 423 잠김</li>
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

    /**
     * 로그인 요청. enterCd 는 로그인 때만 본문으로 받는다(로그인 전이라 세션이 없다, docs/tenant-design.md 4.1).
     * 비밀번호는 toString 에 나오지 않게 한다(로그에 남지 않게).
     */
    public record LoginRequest(String enterCd, String loginId, String password) {
        @Override
        public String toString() {
            return "LoginRequest[enterCd=" + enterCd + ", loginId=" + loginId + "]";
        }
    }

    public record LoginResponse(String enterCd, String loginId) {
    }

    @PostMapping("/api/auth/login")
    @PublicApi
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest body, HttpServletRequest req) {
        if (body == null || body.enterCd() == null || body.enterCd().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "회사를 선택해 주세요");
        }
        if (body.loginId() == null || body.loginId().isBlank() || body.password() == null
                || body.password().isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "아이디와 비밀번호를 입력해 주세요");
        }
        if (body.loginId().strip().length() > MAX_LOGIN_ID || body.password().length() > MAX_PASSWORD) {
            return error(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 맞지 않아요");
        }
        // 법인 코드 형식·존재·사용 여부는 서비스가 아이디 불일치와 같은 응답으로 거부한다.
        LoginService.Result r = service.login(body.enterCd(), body.loginId(), body.password(), req.getRemoteAddr());
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
        // 주체 법인·아이디와 현재 법인(로그인 때는 주체 법인과 같음)을 따로 둔다(docs/tenant-design.md 4.2).
        session.setAttribute(AuthInterceptor.SESSION_ENTER_CD, r.enterCd());
        session.setAttribute(AuthInterceptor.SESSION_LOGIN_ID, r.loginId());
        session.setAttribute(AuthInterceptor.SESSION_CURRENT_ENTER_CD, r.enterCd());
        // 사용 중지 등으로 그 사용자 세션을 모두 끊을 때 PRINCIPAL_NAME 으로 찾는다. 같은 아이디가 다른 법인에도
        // 있을 수 있어 법인과 함께 적는다(한 법인 계정을 끊을 때 다른 법인 계정 세션까지 끊지 않게).
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                r.enterCd() + ":" + r.loginId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new LoginResponse(r.enterCd(), r.loginId()));
    }

    /** 전환할 법인. 법인 코드를 본문으로 받는 유일한 업무 API(docs/tenant-design.md 2장 예외). */
    public record SwitchRequest(String enterCd) {
    }

    public record SwitchResponse(String enterCd, String homeEnterCd) {
    }

    /**
     * 그룹 관리자 법인 전환(#120, docs/tenant-design.md 4.3). ENTER_SWITCH 권한이 없으면 403(AuthInterceptor).
     * 없거나 사용 안 함·다법인 배포 잠금 밖 법인이면 404. 성공하면 세션의 현재 법인만 바꾸고 주체 법인·아이디와
     * 세션 ID 는 그대로 둔다(로그인 주체가 바뀌지 않음). 전환은 접속기록에 남긴다(LoginService.switchEnter).
     */
    @PostMapping("/api/auth/switch-enter")
    @RequiresPermission(Permission.ENTER_SWITCH)
    public ResponseEntity<?> switchEnter(@RequestBody(required = false) SwitchRequest body, AuthUser user,
            HttpServletRequest req) {
        if (body == null || body.enterCd() == null || body.enterCd().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "바꿀 회사를 선택해 주세요");
        }
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute(AuthInterceptor.SESSION_LOGIN_ID) == null) {
            // test 프로필 헤더 요청(세션 없음)은 바꾼 법인을 둘 곳이 없다.
            return error(HttpStatus.BAD_REQUEST, "로그인한 뒤에 회사를 바꿀 수 있어요");
        }
        String target = body.enterCd().strip();
        if (!service.switchEnter(user, target, req.getRemoteAddr())) {
            return error(HttpStatus.NOT_FOUND, "회사를 찾을 수 없어요");
        }
        session.setAttribute(AuthInterceptor.SESSION_CURRENT_ENTER_CD, target);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SwitchResponse(target, user.homeEnterCd()));
    }

    @PostMapping("/api/auth/logout")
    @PublicApi
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            if (session.getAttribute(AuthInterceptor.SESSION_LOGIN_ID) instanceof String id) {
                Object ent = session.getAttribute(AuthInterceptor.SESSION_ENTER_CD);
                service.logout(ent instanceof String e ? e : null, id, req.getRemoteAddr());
            }
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
