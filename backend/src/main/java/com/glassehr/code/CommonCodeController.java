package com.glassehr.code;

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeGroupView;
import com.glassehr.code.CodeDtos.CodeUpdateRequest;
import com.glassehr.code.CodeDtos.CodeView;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
 * 공통코드 API.
 * <ul>
 *   <li>GET  /api/codes?groups=A,B — 여러 그룹의 사용 중인 코드(화면 공통 훅 useCodes)</li>
 *   <li>GET  /api/code-groups — 코드그룹 목록</li>
 *   <li>GET  /api/code-groups/{group}/codes?includeInactive=true — 관리 화면용 코드 목록</li>
 *   <li>POST /api/code-groups/{group}/codes — 코드 추가</li>
 *   <li>PUT  /api/code-groups/{group}/codes/{code} — 코드 수정(코드값 제외)</li>
 * </ul>
 * 삭제 API는 두지 않는다. 사원·발령 이력이 코드를 참조하므로 사용 여부로 끈다.
 * 조회는 로그인 사용자 모두, 추가·수정은 CODE_WRITE 권한(인사담당·관리자, #19).
 */
@RestController
@RequestMapping("/api")
public class CommonCodeController {

    private final CommonCodeService service;

    public CommonCodeController(CommonCodeService service) {
        this.service = service;
    }

    @GetMapping("/codes")
    @RequiresPermission
    public Map<String, List<CodeView>> activeCodes(@RequestParam("groups") String groups) {
        List<String> list = Arrays.stream(groups.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        return service.activeCodes(list);
    }

    @GetMapping("/code-groups")
    @RequiresPermission
    public List<CodeGroupView> groups() {
        return service.listGroups();
    }

    @GetMapping("/code-groups/{groupCode}/codes")
    @RequiresPermission
    public List<CodeView> codes(@PathVariable String groupCode,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return service.listCodes(groupCode, includeInactive);
    }

    @PostMapping("/code-groups/{groupCode}/codes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.CODE_WRITE)
    public CodeView create(@PathVariable String groupCode, @Valid @RequestBody CodeCreateRequest req) {
        return service.create(groupCode, req);
    }

    @PutMapping("/code-groups/{groupCode}/codes/{code}")
    @RequiresPermission(Permission.CODE_WRITE)
    public CodeView update(@PathVariable String groupCode, @PathVariable String code,
            @Valid @RequestBody CodeUpdateRequest req) {
        return service.update(groupCode, code, req);
    }
}
