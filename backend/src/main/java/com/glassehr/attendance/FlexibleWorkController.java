package com.glassehr.attendance;

import com.glassehr.attendance.FlexibleWorkService.AssignRequest;
import com.glassehr.attendance.FlexibleWorkService.AssignView;
import com.glassehr.attendance.FlexibleWorkService.Detail;
import com.glassehr.attendance.FlexibleWorkService.PlanInput;
import com.glassehr.attendance.FlexibleWorkService.Settlement;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import java.util.List;
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
 * 유연근무제 API(#38).
 * <ul>
 *   <li>POST /api/attendance/flex-work — 배정(인사). 서면합의가 전제인 제도는 유효한 합의서가 없으면 422</li>
 *   <li>GET /api/attendance/flex-work?empId= — 배정 목록(인사)</li>
 *   <li>GET /api/attendance/flex-work/me — 내 배정</li>
 *   <li>GET /api/attendance/flex-work/{id} — 배정과 근무계획(인사 또는 본인)</li>
 *   <li>PUT /api/attendance/flex-work/{id}/plan — 근무계획 교체(탄력적은 인사, 선택적은 본인·인사)</li>
 *   <li>GET /api/attendance/flex-work/{id}/settlement — 단위기간·정산기간 정산(인사 또는 본인)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/attendance/flex-work")
public class FlexibleWorkController {

    private final FlexibleWorkService service;

    public FlexibleWorkController(FlexibleWorkService service) {
        this.service = service;
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssignView assign(@RequestBody AssignRequest req, AuthUser user) {
        return service.assign(user.enterCd(), req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping
    public List<AssignView> list(@RequestParam(required = false) Long empId, AuthUser user) {
        return service.list(user.enterCd(), empId);
    }

    @RequiresPermission
    @GetMapping("/me")
    public List<AssignView> mine(AuthUser user) {
        return service.mine(user);
    }

    @RequiresPermission
    @GetMapping("/{id}")
    public Detail detail(@PathVariable long id, AuthUser user) {
        return service.detail(id, user);
    }

    @RequiresPermission
    @PutMapping("/{id}/plan")
    public Detail savePlan(@PathVariable long id, @RequestBody List<PlanInput> plan, AuthUser user) {
        return service.savePlan(id, plan, user);
    }

    @RequiresPermission
    @GetMapping("/{id}/settlement")
    public Settlement settlement(@PathVariable long id, AuthUser user) {
        return service.settle(id, user);
    }
}
