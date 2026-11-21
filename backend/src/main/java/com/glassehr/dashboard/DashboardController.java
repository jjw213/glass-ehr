package com.glassehr.dashboard;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 대시보드 요약 API(#77). Node {@code GET /api/dashboard}를 대신한다.
 * 집계는 사원 조회 범위 안에서만 하므로 전체 조회(관리자·인사·급여)와 부서장(맡은 부서·하위부서)만 연다.
 * 일반 사원은 본인 1명뿐이라 인원 집계를 주지 않는다(403, 화면은 요청하지 않고 카드를 숨긴다).
 */
@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/dashboard/summary")
    @RequiresPermission({Permission.EMP_READ_ALL, Permission.EMP_READ_DEPT})
    public DashboardService.Summary summary(AuthUser user) {
        return service.summary(user);
    }
}
