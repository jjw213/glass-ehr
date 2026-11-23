package com.glassehr.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import com.glassehr.tenant.EnterCompany;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * /api 아래 모든 요청의 권한 검사(API 단 검사 — 화면 메뉴 숨김과 별개로 서버에서 막는다).
 *
 * <ol>
 *   <li>{@link PublicApi} 메서드(헬스 체크·로그인·로그아웃)는 통과. 단 상태를 바꾸는 요청은 CSRF 헤더를 검사한다.</li>
 *   <li>세션의 로그인 아이디로 사용자를 찾는다(#76). 없거나 사용 중지면 401(남은 세션도 끊는다).
 *       역할은 DB에서 읽고 {@code X-Actor-Role} 헤더는 무시한다.</li>
 *   <li>상태를 바꾸는 요청(GET·HEAD·OPTIONS 외)에 {@code X-Requested-With: XMLHttpRequest} 헤더가 없으면 403(CSRF).</li>
 *   <li>{@link RequiresPermission}이 없으면 403(빠뜨린 API가 열리지 않게).</li>
 *   <li>권한 중 하나도 없으면 403.</li>
 *   <li>{@link PrivacyAudit} 메서드에서 막히면 거부 로그를 남긴다.</li>
 * </ol>
 * 통과하면 {@link AuthUser}를 요청 속성에 넣고, 컨트롤러는 {@code AuthUser} 인자로 받아 데이터 범위를 검사한다.
 *
 * <p>{@code X-Actor-Id} 헤더 식별은 {@code test} 프로필에서만, 세션이 없을 때만 받는다. 그 밖의 프로필에서는 무시한다
 * (docs/auth-design.md 8장). 개인정보 보호법 제29조·개인정보의 안전성 확보조치 기준: 인증한 사용자만 접근하게 통제한다.
 *
 * <p>CSRF: 세션 쿠키는 SameSite=Lax 이고, 상태를 바꾸는 요청은 사용자 지정 헤더를 요구한다. 다른 출처의 페이지는
 * CORS 사전 요청 없이 이 헤더를 붙일 수 없고, 이 서버는 CORS 를 열지 않는다.
 */
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ACTOR_ID_HEADER = "X-Actor-Id";
    /** test 프로필에서 X-Actor-Id 와 함께 받는 법인 헤더. 없으면 C001(#119). */
    public static final String ACTOR_ENTER_HEADER = "X-Actor-Enter";
    /** CSRF 방어용 사용자 지정 헤더와 값. 화면(api/http.ts)이 모든 요청에 붙인다. */
    public static final String CSRF_HEADER = "X-Requested-With";
    public static final String CSRF_HEADER_VALUE = "XMLHttpRequest";
    /** 세션에 넣는 로그인 아이디 속성 이름(LoginController 가 로그인 성공 시 넣는다). */
    public static final String SESSION_LOGIN_ID = "glassehr.auth.loginId";
    /** 세션의 주체 법인(로그인한 법인, 바뀌지 않음). docs/tenant-design.md 4.2 */
    public static final String SESSION_ENTER_CD = "glassehr.auth.enterCd";
    /** 세션의 현재 법인(업무 조회·수정의 법인 조건). 로그인 때 주체 법인으로 넣는다. */
    public static final String SESSION_CURRENT_ENTER_CD = "glassehr.auth.currentEnterCd";
    static final String ATTR = AuthUser.class.getName();
    private static final String CSRF_REJECT = "요청 출처를 확인할 수 없어요. 화면을 새로 고친 뒤 다시 시도해 주세요";

    private final AuthUserResolver resolver;
    private final PrivacyAccessLogger privacyLog;
    private final boolean acceptActorHeader;

    /**
     * @param acceptActorHeader {@code X-Actor-Id} 헤더로 사용자를 믿을지. {@code test} 프로필에서만 true(AuthWebConfig).
     */
    public AuthInterceptor(AuthUserResolver resolver, PrivacyAccessLogger privacyLog, boolean acceptActorHeader) {
        this.resolver = resolver;
        this.privacyLog = privacyLog;
        this.acceptActorHeader = acceptActorHeader;
    }

    /** 요청한 주체(법인, 아이디). 둘 중 하나라도 없으면 그 값은 null. */
    record Principal(String enterCd, String loginId) {
    }

    /**
     * 세션의 주체 법인·로그인 아이디. 로그인 세션이 없고 test 프로필이면 X-Actor-Id·X-Actor-Enter(없으면 C001) 헤더.
     * 법인 전환 기능이 아직 없으므로 세션의 현재 법인이 주체 법인과 다르거나 법인이 없는 세션(이 변경 전 세션)은
     * 사용자를 찾지 않는다(401, 다시 로그인).
     */
    Principal principalOf(HttpServletRequest req) {
        String fromSession = sessionLoginId(req);
        if (fromSession != null) {
            HttpSession s = req.getSession(false);
            Object home = s.getAttribute(SESSION_ENTER_CD);
            Object current = s.getAttribute(SESSION_CURRENT_ENTER_CD);
            boolean ok = home instanceof String h && h.equals(current);
            return new Principal(ok ? (String) home : null, fromSession);
        }
        if (!acceptActorHeader) {
            return new Principal(null, null);
        }
        String ent = req.getHeader(ACTOR_ENTER_HEADER);
        return new Principal(ent == null ? EnterCompany.DEFAULT_ENTER_CD : ent, req.getHeader(ACTOR_ID_HEADER));
    }

    private static String sessionLoginId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && session.getAttribute(SESSION_LOGIN_ID) instanceof String id ? id : null;
    }

    private static boolean changesState(HttpServletRequest req) {
        String m = req.getMethod();
        return !("GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m));
    }

    /**
     * CSRF 헤더 검사. test 프로필에서 세션 없이 X-Actor-Id 로 보낸 요청은 그 헤더 자체가 사용자 지정 헤더라
     * 같은 효과가 있어 통과시킨다(쿠키를 쓰지 않으므로 다른 출처 페이지가 사용자 권한으로 보낼 수도 없다).
     */
    private boolean csrfHeaderOk(HttpServletRequest req) {
        if (CSRF_HEADER_VALUE.equals(req.getHeader(CSRF_HEADER))) {
            return true;
        }
        return acceptActorHeader && req.getHeader(ACTOR_ID_HEADER) != null && sessionLoginId(req) == null;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod hm)) {
            return true; // 정적 자원·404 처리 등. 컨트롤러 메서드가 아니면 데이터를 돌려주지 않는다.
        }
        if (hm.hasMethodAnnotation(PublicApi.class)) {
            if (changesState(req) && !csrfHeaderOk(req)) {
                return reject(res, HttpStatus.FORBIDDEN, CSRF_REJECT);
            }
            return true;
        }
        PrivacyAudit audit = hm.getMethodAnnotation(PrivacyAudit.class);
        Principal who = principalOf(req);
        Optional<AuthUser> found = resolver.resolve(who.enterCd(), who.loginId(), req.getRemoteAddr());
        if (found.isEmpty()) {
            if (audit != null) {
                privacyLog.logAnonymousDenied(who.enterCd(), who.loginId(), req.getRemoteAddr(), targetEmpId(req),
                        audit.item(),
                        audit.action(), req.getRequestURI());
            }
            if (sessionLoginId(req) != null) {
                req.getSession(false).invalidate(); // 사용 중지·삭제된 사용자의 남은 세션
            }
            return reject(res, HttpStatus.UNAUTHORIZED, "로그인이 필요해요");
        }
        if (changesState(req) && !csrfHeaderOk(req)) {
            return reject(res, HttpStatus.FORBIDDEN, CSRF_REJECT);
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
