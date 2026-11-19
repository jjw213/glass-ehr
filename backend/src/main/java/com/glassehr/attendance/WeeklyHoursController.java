package com.glassehr.attendance;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주 52시간 모니터링 API(#31). 이번 주(서울 기준 월~일)만 본다.
 * <ul>
 *   <li>GET /api/attendance/weekly-hours/me — 내 누적 근로시간·한도·상태(로그인 사원 본인)</li>
 *   <li>GET /api/attendance/weekly-hours/team — 부서별 표(전체 조회 권한은 전체, 부서장은 맡은 부서·하위부서)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/attendance/weekly-hours")
public class WeeklyHoursController {

    private final WeeklyHoursService service;

    public WeeklyHoursController(WeeklyHoursService service) {
        this.service = service;
    }

    @RequiresPermission
    @GetMapping("/me")
    public WeeklyHoursService.MyWeek mine(AuthUser user) {
        return service.mine(user);
    }

    @RequiresPermission({Permission.EMP_READ_ALL, Permission.EMP_READ_DEPT})
    @GetMapping("/team")
    public WeeklyHoursService.TeamWeek team(AuthUser user) {
        return service.team(user);
    }
}
