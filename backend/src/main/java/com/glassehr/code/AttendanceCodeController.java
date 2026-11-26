package com.glassehr.code;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import com.glassehr.code.AttendanceCodeDtos.AttendanceCodeCreateRequest;
import com.glassehr.code.AttendanceCodeDtos.AttendanceCodeUpdateRequest;
import com.glassehr.code.AttendanceCodeDtos.AttendanceCodeView;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 근태코드 API(#143). {group} 은 LEAVE_TYPE(휴가 종류)·ATTENDANCE_TYPE(근태 구분)만.
 * <ul>
 *   <li>GET    /api/attendance-codes/{group} — 목록(사용 안 함 포함, 참조 건수·휴가 상세 설정·법정 기준값)</li>
 *   <li>GET    /api/attendance-codes/{group}/{code} — 코드 하나(휴가 상세 설정 조회)</li>
 *   <li>POST   /api/attendance-codes/{group} — 추가(휴가는 회사 약정·무급만)</li>
 *   <li>PUT    /api/attendance-codes/{group}/{code} — 수정(휴가 상세 설정 포함)</li>
 *   <li>DELETE /api/attendance-codes/{group}/{code} — 삭제(공통코드 삭제 규칙)</li>
 * </ul>
 * 사용처 보기는 공통코드 API(/api/code-groups/{group}/codes/{code}/usages)를 그대로 쓴다.
 * 조회는 로그인 사용자 모두, 쓰기는 공통코드와 같은 CODE_WRITE 권한. 세션의 현재 법인만 다룬다.
 */
@RestController
@RequestMapping("/api/attendance-codes")
public class AttendanceCodeController {

    private final AttendanceCodeService service;

    public AttendanceCodeController(AttendanceCodeService service) {
        this.service = service;
    }

    @GetMapping("/{groupCode}")
    @RequiresPermission
    public List<AttendanceCodeView> list(@PathVariable String groupCode, AuthUser user) {
        return service.list(user.enterCd(), groupCode);
    }

    @GetMapping("/{groupCode}/{code}")
    @RequiresPermission
    public AttendanceCodeView get(@PathVariable String groupCode, @PathVariable String code, AuthUser user) {
        return service.get(user.enterCd(), groupCode, code);
    }

    @PostMapping("/{groupCode}")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.CODE_WRITE)
    public AttendanceCodeView create(@PathVariable String groupCode,
            @Valid @RequestBody AttendanceCodeCreateRequest req, AuthUser user) {
        return service.create(user.enterCd(), groupCode, req);
    }

    @PutMapping("/{groupCode}/{code}")
    @RequiresPermission(Permission.CODE_WRITE)
    public AttendanceCodeView update(@PathVariable String groupCode, @PathVariable String code,
            @Valid @RequestBody AttendanceCodeUpdateRequest req, AuthUser user) {
        return service.update(user.enterCd(), groupCode, code, req);
    }

    @DeleteMapping("/{groupCode}/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.CODE_WRITE)
    public void delete(@PathVariable String groupCode, @PathVariable String code, AuthUser user) {
        service.delete(user.enterCd(), groupCode, code);
    }
}
