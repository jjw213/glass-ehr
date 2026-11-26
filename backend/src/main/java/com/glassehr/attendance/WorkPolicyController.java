package com.glassehr.attendance;

import com.glassehr.attendance.WorkPolicyDtos.DeleteResult;
import com.glassehr.attendance.WorkPolicyDtos.WorkPolicyRequest;
import com.glassehr.attendance.WorkPolicyDtos.WorkPolicyView;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 근무 정책(기록·인정 규칙) API(#159). 법인은 세션({@link AuthUser#enterCd()})에서만 정한다.
 * <ul>
 *   <li>GET    /api/attendance/work-policies?includeHidden= — 정책 목록(로그인 사용자 모두, 개인정보 없음)</li>
 *   <li>POST   /api/attendance/work-policies — 등록(patternId 없으면 법인 기본 정책). 법정 한도 위반 422, 중복 409</li>
 *   <li>PUT    /api/attendance/work-policies/{id} — 수정</li>
 *   <li>DELETE /api/attendance/work-policies/{id} — 쓰인 정책은 숨김, 아니면 삭제</li>
 * </ul>
 * 등록·수정·삭제는 근무유형과 같은 {@link Permission#EMP_WRITE}.
 */
@RestController
@RequestMapping("/api/attendance/work-policies")
public class WorkPolicyController {

    private final WorkPolicyService service;

    public WorkPolicyController(WorkPolicyService service) {
        this.service = service;
    }

    @RequiresPermission
    @GetMapping
    public List<WorkPolicyView> list(@RequestParam(defaultValue = "false") boolean includeHidden, AuthUser user) {
        return service.list(user.enterCd(), includeHidden);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkPolicyView create(@Valid @RequestBody WorkPolicyRequest req, AuthUser user) {
        return service.create(user.enterCd(), req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PutMapping("/{policyId}")
    public WorkPolicyView update(@PathVariable long policyId, @Valid @RequestBody WorkPolicyRequest req,
            AuthUser user) {
        return service.update(user.enterCd(), policyId, req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @DeleteMapping("/{policyId}")
    public DeleteResult delete(@PathVariable long policyId, AuthUser user) {
        return service.delete(user.enterCd(), policyId, user.loginId());
    }
}
