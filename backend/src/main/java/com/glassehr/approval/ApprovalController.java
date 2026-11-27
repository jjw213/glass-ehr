package com.glassehr.approval;

import com.glassehr.approval.ApprovalDtos.ActionRequest;
import com.glassehr.approval.ApprovalDtos.DocDetail;
import com.glassehr.approval.ApprovalDtos.DocSummary;
import com.glassehr.approval.ApprovalDtos.RequestTypeView;
import com.glassehr.approval.ApprovalDtos.TemplateRequest;
import com.glassehr.approval.ApprovalDtos.TemplateView;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 결재 API(#30).
 * <ul>
 *   <li>GET  /api/approvals/request-types — 결재 신청 유형</li>
 *   <li>GET  /api/approvals/inbox — 결재함(지금 내 차례)</li>
 *   <li>GET  /api/approvals/processed — 내가 처리한 문서</li>
 *   <li>GET  /api/approvals/mine — 내가 올린 문서</li>
 *   <li>GET  /api/approvals/{id} — 상세(단계·처리 이력)</li>
 *   <li>POST /api/approvals/{id}/approve · /reject · /withdraw</li>
 *   <li>GET  /api/approvals/templates?requestType= · POST /api/approvals/templates — 결재선 설정</li>
 * </ul>
 * 결재 처리·조회는 로그인 사용자 누구나 부를 수 있고, 차례·본인 여부는 서비스가 문서 스냅샷으로 검사한다.
 * 결재선 설정은 {@link Permission#CODE_WRITE}(관리자·인사). 결재 올리기는 각 모듈 신청 API 가 한다.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;
    private final ApprovalTemplateService templateService;

    public ApprovalController(ApprovalService service, ApprovalTemplateService templateService) {
        this.service = service;
        this.templateService = templateService;
    }

    @RequiresPermission
    @GetMapping("/request-types")
    public List<RequestTypeView> requestTypes() {
        return service.requestTypes();
    }

    @RequiresPermission
    @GetMapping("/inbox")
    public List<DocSummary> inbox(AuthUser user) {
        return service.inbox(user);
    }

    @RequiresPermission
    @GetMapping("/processed")
    public List<DocSummary> processed(AuthUser user) {
        return service.processed(user);
    }

    @RequiresPermission
    @GetMapping("/mine")
    public List<DocSummary> mine(AuthUser user) {
        return service.mine(user);
    }

    @RequiresPermission
    @GetMapping("/{id}")
    public DocDetail get(@PathVariable long id, AuthUser user) {
        return service.get(id, user);
    }

    @RequiresPermission
    @PostMapping("/{id}/approve")
    public DocDetail approve(@PathVariable long id, @Valid @RequestBody(required = false) ActionRequest req,
            AuthUser user) {
        return service.approve(id, user, req == null ? null : req.comment());
    }

    @RequiresPermission
    @PostMapping("/{id}/reject")
    public DocDetail reject(@PathVariable long id, @Valid @RequestBody ActionRequest req, AuthUser user) {
        return service.reject(id, user, req.comment());
    }

    @RequiresPermission
    @PostMapping("/{id}/withdraw")
    public DocDetail withdraw(@PathVariable long id, AuthUser user) {
        return service.withdraw(id, user);
    }

    @RequiresPermission(Permission.CODE_WRITE)
    @GetMapping("/templates")
    public List<TemplateView> templates(@RequestParam String requestType, AuthUser user) {
        return templateService.list(user.enterCd(), requestType);
    }

    @RequiresPermission(Permission.CODE_WRITE)
    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateView createTemplate(@Valid @RequestBody TemplateRequest req, AuthUser user) {
        return templateService.create(req, user);
    }
}
