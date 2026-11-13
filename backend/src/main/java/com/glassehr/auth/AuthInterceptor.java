package com.glassehr.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * /api 아래 모든 요청의 권한 검사(API 단 검사 — 화면 메뉴 숨김과 별개로 서버에서 막는다).
 *
 * <ol>
 *   <li>{@link PublicApi} 메서드는 통과.</li>
 *   <li>{@code X-Actor-Id}로 사용자를 찾는다. 없거나 사용 중지면 401. 역할은 DB에서 읽고 {@code X-Actor-Role} 헤더는 무시한다.</li>
 *   <li>{@link RequiresPermission}이 없으면 403(빠뜨린 API가 열리지 않게).</li>
 *   <li>권한 중 하나도 없으면 403.</li>
 *   <li>{@link PrivacyAudit} 메서드에서 막히면 거부 로그를 남긴다.</li>
 * </ol>
 * 통과하면 {@link AuthUser}를 요청 속성에 넣고, 컨트롤러는 {@code AuthUser} 인자로 받아 데이터 범위를 검사한다.
 *
 * <p>사용자 식별(X-Actor-Id)은 인증 이슈 전까지의 임시 방식이다(docs/auth-design.md 2장). 로그인 세션·토큰으로 바꿀 때
 * {@link #loginIdOf(HttpServletRequest)}만 바꾸면 된다.
 */
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ACTOR_ID_HEADER = "X-Actor-Id";
    static final String ATTR = AuthUser.class.getName();

    private final AuthUserResolver resolver;
    private final PrivacyAccessLogger privacyLog;

    public AuthInterceptor(AuthUserResolver resolver, PrivacyAccessLogger privacyLog) {
        this.resolver = resolver;
        this.privacyLog = privacyLog;
    }

    static String loginIdOf(HttpServletRequest req) {
        return req.getHeader(ACTOR_ID_HEADER);
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod hm)) {
            return true; // 정적 자원·404 처리 등. 컨트롤러 메서드가 아니면 데이터를 돌려주지 않는다.
        }
        if (hm.hasMethodAnnotation(PublicApi.class)) {
            return true;
        }
        PrivacyAudit audit = hm.getMethodAnnotation(PrivacyAudit.class);
        String loginId = loginIdOf(req);
        Optional<AuthUser> found = resolver.resolve(loginId, req.getRemoteAddr());
        if (found.isEmpty()) {
            if (audit != null) {
                privacyLog.logAnonymousDenied(loginId, req.getRemoteAddr(), targetEmpId(req), audit.item(),
                        audit.action(), req.getRequestURI());
            }
            return reject(res, HttpStatus.UNAUTHORIZED, "로그인이 필요해요");
        }
        AuthUser user = found.get();
        RequiresPermission rp = hm.getMethodAnnotation(RequiresPermission.class);
        boolean allowed = rp != null && (rp.value().length == 0 || user.hasAny(rp.value()));
        if (!allowed) {
            if (audit != null) {
                privacyLog.log(user, targetEmpId(req), audit.item(), audit.action(), PrivacyAccessLog.Result.DENIED,
                        req.getRequestURI());
            }
            return reject(res, HttpStatus.FORBIDDEN, "이 기능을 쓸 권한이 없어요");
        }
        req.setAttribute(ATTR, user);
        return true;
    }

    /** 경로 변수 empId(숫자일 때만). */
    private static Long targetEmpId(HttpServletRequest req) {
        Object vars = req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> m && m.get("empId") instanceof String s) {
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean reject(HttpServletResponse res, HttpStatus status, String msg) throws IOException {
        res.setStatus(status.value());
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"error\":\"" + msg + "\"}"); // msg 는 위의 고정 문구(따옴표 없음)
        return false;
    }
}
