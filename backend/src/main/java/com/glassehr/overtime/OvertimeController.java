package com.glassehr.overtime;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import com.glassehr.overtime.OvertimeDtos.AuthRequest;
import com.glassehr.overtime.OvertimeDtos.AuthView;
import com.glassehr.overtime.OvertimeDtos.CreateRequest;
import com.glassehr.overtime.OvertimeDtos.RequestView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연장·휴일근로 신청 API(#30). 결재 처리(승인·반려·회수)는 /api/approvals 를 쓴다.
 * <ul>
 *   <li>POST /api/overtime-requests — 본인 신청(결재 올림)</li>
 *   <li>GET  /api/overtime-requests/mine — 내 신청</li>
 *   <li>GET·POST /api/special-overtime-auths — 특별연장근로 인가 정보(근로기준법 제53조 제4항)</li>
 * </ul>
 */
@RestController
public class OvertimeController {

    private final OvertimeService service;

    public OvertimeController(OvertimeService service) {
        this.service = service;
    }

    @RequiresPermission
    @PostMapping("/api/overtime-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView create(@Valid @RequestBody CreateRequest req, AuthUser user) {
        return service.create(req, user);
    }

    @RequiresPermission
    @GetMapping("/api/overtime-requests/mine")
    public List<RequestView> mine(AuthUser user) {
        return service.mine(user);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/special-overtime-auths")
    public List<AuthView> auths() {
        return service.auths();
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/api/special-overtime-auths")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthView createAuth(@Valid @RequestBody AuthRequest req, AuthUser user) {
        return service.createAuth(req, user);
    }
}
