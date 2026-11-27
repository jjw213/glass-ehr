package com.glassehr.tenant;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import com.glassehr.tenant.EnterSettingService.SettingChange;
import com.glassehr.tenant.EnterSettingService.SettingException;
import com.glassehr.tenant.EnterSettingService.SettingView;
import com.glassehr.tenant.EnterSettingService.UpdateRequest;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 법인별 회사 설정 API(#131).
 * <ul>
 *   <li>GET /api/enter-settings — 현재 법인의 설정(오늘 값·기본값·적용시작일별 값)</li>
 *   <li>PUT /api/enter-settings/{key} — {effectiveFrom, value} 등록·수정</li>
 *   <li>GET /api/enter-settings/history — 변경 이력(최근 것부터)</li>
 * </ul>
 * 권한: 그 법인의 관리자·인사({@link Permission#EMP_WRITE}). 법인은 세션의 현재 법인만 쓴다(본문·쿼리로 받지 않음).
 */
@RestController
@RequestMapping("/api/enter-settings")
public class EnterSettingController {

    private final EnterSettingService service;

    public EnterSettingController(EnterSettingService service) {
        this.service = service;
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @GetMapping
    public List<SettingView> list(AuthUser user) {
        return service.list(user.enterCd());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PutMapping("/{key}")
    public SettingView update(@PathVariable String key, @RequestBody UpdateRequest req, AuthUser user) {
        return service.update(user.enterCd(), key, req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @GetMapping("/history")
    public List<SettingChange> history(AuthUser user) {
        return service.history(user.enterCd());
    }

    @ExceptionHandler(SettingException.class)
    ResponseEntity<Map<String, String>> setting(SettingException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "요청 형식이 올바르지 않아요(적용 시작일 YYYY-MM-DD, 값은 정수)"));
    }

    /** 같은 적용시작일을 동시에 처음 등록하면 PK 충돌 — 다시 시도하게 409. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "다른 사용자가 같은 설정을 바꾸는 중이에요. 다시 시도해 주세요"));
    }
}
