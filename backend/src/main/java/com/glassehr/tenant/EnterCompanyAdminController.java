package com.glassehr.tenant;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import com.glassehr.tenant.EnterCompanyAdminService.ActiveRequest;
import com.glassehr.tenant.EnterCompanyAdminService.AdminException;
import com.glassehr.tenant.EnterCompanyAdminService.CompanyView;
import com.glassehr.tenant.EnterCompanyAdminService.CreateRequest;
import com.glassehr.tenant.EnterCompanyAdminService.CreateResult;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그룹 관리자 법인 관리 API(#130). 권한 {@link Permission#ENTER_COMPANY_MANAGE}(GROUP_ADMIN, V052).
 * <ul>
 *   <li>GET /api/admin/enter-companies — 세션 고객사의 법인 목록</li>
 *   <li>POST /api/admin/enter-companies — {enterCd, enterName, bizRegNo, sourceEnterCd, adminLoginId, adminPassword}
 *       → 201 새 법인·복사 건수·건너뛴 결재 양식. 원본이 다른 고객사 법인이면 404, 코드·사업자등록번호 중복 409</li>
 *   <li>PUT /api/admin/enter-companies/{enterCd}/active — {active} 사용 중지·다시 사용</li>
 * </ul>
 * 고객사는 세션에서만 꺼낸다(본문·쿼리로 받지 않음).
 */
@RestController
@RequestMapping("/api/admin/enter-companies")
public class EnterCompanyAdminController {

    private final EnterCompanyAdminService service;

    public EnterCompanyAdminController(EnterCompanyAdminService service) {
        this.service = service;
    }

    @RequiresPermission(Permission.ENTER_COMPANY_MANAGE)
    @GetMapping
    public List<CompanyView> list(AuthUser user) {
        return service.list(user);
    }

    @RequiresPermission(Permission.ENTER_COMPANY_MANAGE)
    @PostMapping
    public ResponseEntity<CreateResult> create(@RequestBody CreateRequest req, AuthUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user, req));
    }

    @RequiresPermission(Permission.ENTER_COMPANY_MANAGE)
    @PutMapping("/{enterCd}/active")
    public CompanyView setActive(@PathVariable String enterCd, @RequestBody ActiveRequest req, AuthUser user) {
        return service.setActive(user, enterCd, req);
    }

    @ExceptionHandler(AdminException.class)
    ResponseEntity<Map<String, String>> admin(AdminException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "요청 형식이 올바르지 않아요"));
    }

    /** 같은 법인 코드·사업자등록번호를 동시에 만들면 PK·UNIQUE 충돌 — 트랜잭션은 전부 되돌려진다. */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, String>> conflict(DuplicateKeyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "이미 쓰는 법인 코드나 사업자등록번호예요"));
    }
}
