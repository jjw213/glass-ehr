package com.glassehr.retirement;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import com.glassehr.retirement.RetirementDtos.CertificateRequest;
import com.glassehr.retirement.RetirementDtos.CertificateView;
import com.glassehr.retirement.RetirementDtos.ChecklistTemplateItem;
import com.glassehr.retirement.RetirementDtos.ChecklistUpdateRequest;
import com.glassehr.retirement.RetirementDtos.RetentionExpiredRow;
import com.glassehr.retirement.RetirementDtos.RetirementRequest;
import com.glassehr.retirement.RetirementDtos.RetirementView;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 퇴직 처리·사용증명서 API(#18). 설계: docs/retirement-design.md.
 * <ul>
 *   <li>GET  /api/retirements/checklist-template?reasonCode= — 유형별 체크리스트 미리보기</li>
 *   <li>POST /api/retirements — 퇴직 처리 등록(근로자 명부 퇴직 기록 + 체크리스트 + 해고 예고 확인)</li>
 *   <li>GET  /api/retirements/{empId} — 퇴직 처리 1건</li>
 *   <li>PUT  /api/retirements/{empId}/checklist/{itemCode} — 체크리스트 완료 표시</li>
 *   <li>GET  /api/retirements/retention-expired?asOf= — 보존기한이 지난 퇴직자</li>
 *   <li>POST /api/employees/{empId}/employment-certificates — 사용증명서 발급</li>
 *   <li>GET  /api/employees/{empId}/employment-certificates — 발급 이력</li>
 *   <li>GET  /api/employment-certificates/{certId} — 사용증명서 1건(인쇄 화면)</li>
 * </ul>
 * 권한(#19): 조회는 전체 사원 조회(관리자·인사·급여), 등록·발급·완료 표시는 사원 쓰기(관리자·인사).
 */
@RestController
public class RetirementController {

    private final RetirementService service;

    public RetirementController(RetirementService service) {
        this.service = service;
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @GetMapping("/api/retirements/checklist-template")
    public List<ChecklistTemplateItem> template(@RequestParam("reasonCode") String reasonCode) {
        return service.template(reasonCode);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/api/retirements")
    @ResponseStatus(HttpStatus.CREATED)
    public RetirementView register(@Valid @RequestBody RetirementRequest req, AuthUser user) {
        return service.register(user.enterCd(), req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/retirements/retention-expired")
    public List<RetentionExpiredRow> retentionExpired(@RequestParam(name = "asOf", required = false) LocalDate asOf,
            AuthUser user) {
        return service.retentionExpired(user.enterCd(), asOf);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/retirements/{empId}")
    public RetirementView get(@PathVariable long empId, AuthUser user) {
        return service.get(user.enterCd(), empId);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PutMapping("/api/retirements/{empId}/checklist/{itemCode}")
    public RetirementView updateChecklist(@PathVariable long empId, @PathVariable String itemCode,
            @Valid @RequestBody ChecklistUpdateRequest req, AuthUser user) {
        return service.updateChecklist(user.enterCd(), empId, itemCode, req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/api/employees/{empId}/employment-certificates")
    @ResponseStatus(HttpStatus.CREATED)
    public CertificateView issue(@PathVariable long empId, @Valid @RequestBody CertificateRequest req, AuthUser user) {
        return service.issueCertificate(user.enterCd(), empId, req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/employees/{empId}/employment-certificates")
    public List<CertificateView> certificates(@PathVariable long empId, AuthUser user) {
        return service.certificates(user.enterCd(), empId);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/employment-certificates/{certId}")
    public CertificateView certificate(@PathVariable long certId, AuthUser user) {
        return service.certificate(user.enterCd(), certId);
    }
}
