package com.glassehr.auth;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 권한 API.
 * <ul>
 *   <li>GET /api/auth/me — 내 역할·권한·부서장 범위(화면 메뉴 표시용. 실제 차단은 각 API가 한다)</li>
 *   <li>GET /api/admin/privacy-logs?from=&amp;to=&amp;accessorId=&amp;targetEmpId= — 개인정보 조회 로그(관리자)</li>
 * </ul>
 */
@RestController
public class AuthController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** from 을 비우면 오늘부터 며칠 전까지 볼지(화면 기본값, 법령 기준값 아님). */
    static final int DEFAULT_DAYS = 30;

    private final PrivacyLogService logs;

    public AuthController(PrivacyLogService logs) {
        this.logs = logs;
    }

    public record Me(String loginId, Long empId, List<String> roles, List<String> permissions,
            List<Long> managedDeptIds) {
    }

    @GetMapping("/api/auth/me")
    @RequiresPermission
    public Me me(AuthUser user) {
        return new Me(user.loginId(), user.empId(), List.copyOf(user.roles()),
                user.permissions().stream().map(Enum::name).sorted().toList(), List.copyOf(user.managedDeptIds()));
    }

    @GetMapping("/api/admin/privacy-logs")
    @RequiresPermission(Permission.PRIVACY_LOG_READ)
    public ResponseEntity<PrivacyLogService.LogPage> privacyLogs(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "accessorId", required = false) String accessorId,
            @RequestParam(name = "targetEmpId", required = false) Long targetEmpId) {
        LocalDate end = to != null ? to : LocalDate.now(SEOUL);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(logs.search(start, end, accessorId, targetEmpId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRange(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, String>> badParam(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "조회 조건 형식이 올바르지 않아요(날짜는 YYYY-MM-DD)"));
    }
}
