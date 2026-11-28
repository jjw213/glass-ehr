package com.glassehr.code;

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeGroupView;
import com.glassehr.code.CodeDtos.CodeListItem;
import com.glassehr.code.CodeDtos.CodeUpdateRequest;
import com.glassehr.code.CodeDtos.CodeView;
import com.glassehr.code.CodeDtos.GroupCreateRequest;
import com.glassehr.code.CodeDtos.GroupUpdateRequest;
import com.glassehr.code.CodeDtos.UsagePage;
import com.glassehr.code.CodeDtos.UsageSummary;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
 * 공통코드 API.
 * <ul>
 *   <li>GET    /api/codes?groups=A,B — 여러 그룹의 사용 중인 코드(화면 공통 훅 useCodes)</li>
 *   <li>GET    /api/code-groups — 코드그룹 목록(시스템 여부·편집 화면 포함)</li>
 *   <li>POST   /api/code-groups — 사용자 그룹 추가(#140)</li>
 *   <li>PUT    /api/code-groups/{group} — 그룹 이름·설명·순서·사용 여부 수정(그룹코드 제외)</li>
 *   <li>DELETE /api/code-groups/{group} — 사용자 그룹 삭제(코드 참조 중이면 409)</li>
 *   <li>GET    /api/code-groups/{group}/codes?includeInactive=true — 관리 화면용 코드 목록(참조 건수 포함)</li>
 *   <li>GET    /api/code-groups/{group}/sys-types — 처리방식 목록(#141, 서버 고정)</li>
 *   <li>POST   /api/code-groups/{group}/codes — 코드 추가</li>
 *   <li>PUT    /api/code-groups/{group}/codes/{code} — 코드 수정(코드값 제외)</li>
 *   <li>DELETE /api/code-groups/{group}/codes/{code} — 참조 없는 사용자 코드 삭제</li>
 *   <li>GET    /api/code-groups/{group}/codes/{code}/usages[?table=..&page=..] — 사용처 건수·행 목록</li>
 * </ul>
 * 조회는 로그인 사용자 모두, 추가·수정·삭제는 CODE_WRITE 권한(인사담당·관리자, #19).
 * 사용처 행 목록(사번·이름)은 EMP_READ_ALL 권한이 있을 때만, 없으면 건수만(403).
 * 모두 세션의 현재 법인 코드만 다룬다(#121). 다른 법인의 그룹·코드는 404.
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
    public Map<String, List<CodeView>> activeCodes(@RequestParam("groups") String groups, AuthUser user) {
        List<String> list = Arrays.stream(groups.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        return service.activeCodes(user.enterCd(), list);
    }

    @GetMapping("/code-groups")
    @RequiresPermission
    public List<CodeGroupView> groups(AuthUser user) {
        return service.listGroups(user.enterCd());
    }

    @PostMapping("/code-groups")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.CODE_WRITE)
    public CodeGroupView createGroup(@Valid @RequestBody GroupCreateRequest req, AuthUser user) {
        return service.createGroup(user.enterCd(), req);
    }

    @PutMapping("/code-groups/{groupCode}")
    @RequiresPermission(Permission.CODE_WRITE)
    public CodeGroupView updateGroup(@PathVariable String groupCode, @Valid @RequestBody GroupUpdateRequest req,
            AuthUser user) {
        return service.updateGroup(user.enterCd(), groupCode, req);
    }

    @DeleteMapping("/code-groups/{groupCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.CODE_WRITE)
    public void deleteGroup(@PathVariable String groupCode, AuthUser user) {
        service.deleteGroup(user.enterCd(), groupCode);
    }

    @GetMapping("/code-groups/{groupCode}/codes")
    @RequiresPermission
    public List<CodeListItem> codes(@PathVariable String groupCode,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive, AuthUser user) {
        return service.listCodes(user.enterCd(), groupCode, includeInactive);
    }

    /** 그룹의 처리방식 목록(#141). 서버 고정 목록이고 처리방식이 없는 그룹은 빈 목록. */
    @GetMapping("/code-groups/{groupCode}/sys-types")
    @RequiresPermission
    public List<CodeSysTypes.SysType> sysTypes(@PathVariable String groupCode, AuthUser user) {
        return service.sysTypes(user.enterCd(), groupCode);
    }

    @PostMapping("/code-groups/{groupCode}/codes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.CODE_WRITE)
    public CodeView create(@PathVariable String groupCode, @Valid @RequestBody CodeCreateRequest req, AuthUser user) {
        return service.create(user.enterCd(), groupCode, req);
    }

    @PutMapping("/code-groups/{groupCode}/codes/{code}")
    @RequiresPermission(Permission.CODE_WRITE)
    public CodeView update(@PathVariable String groupCode, @PathVariable String code,
            @Valid @RequestBody CodeUpdateRequest req, AuthUser user) {
        return service.update(user.enterCd(), groupCode, code, req);
    }

    @DeleteMapping("/code-groups/{groupCode}/codes/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.CODE_WRITE)
    public void delete(@PathVariable String groupCode, @PathVariable String code, AuthUser user) {
        service.deleteCode(user.enterCd(), groupCode, code);
    }

    /** table 이 없으면 테이블별 건수, 있으면 그 테이블의 행 목록(EMP_READ_ALL 필요). */
    @GetMapping("/code-groups/{groupCode}/codes/{code}/usages")
    @RequiresPermission
    public Object usages(@PathVariable String groupCode, @PathVariable String code,
            @RequestParam(name = "table", required = false) String table,
            @RequestParam(name = "page", defaultValue = "0") int page, AuthUser user) {
        boolean rowsVisible = user.has(Permission.EMP_READ_ALL);
        if (table == null || table.isBlank()) {
            UsageSummary summary = service.usages(user.enterCd(), groupCode, code, rowsVisible);
            return summary;
        }
        if (!rowsVisible) {
            throw new CodeException(HttpStatus.FORBIDDEN, "사용처 사원 목록은 전체 사원 조회 권한이 있어야 볼 수 있어요. 건수만 볼 수 있어요");
        }
        UsagePage rows = service.usageRows(user.enterCd(), groupCode, code, table, page);
        return rows;
    }
}
