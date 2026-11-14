package com.glassehr.attendance;

import com.glassehr.attendance.PunchDtos.PunchRequest;
import com.glassehr.attendance.PunchDtos.PunchView;
import com.glassehr.attendance.PunchDtos.TodayView;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 출퇴근 기록 API(#28).
 * <ul>
 *   <li>POST /api/attendance/punches — 내 출근·퇴근 한 건 추가(서버 시각 기록)</li>
 *   <li>GET  /api/attendance/punches/today — 오늘 내 기록과 출근 시각·근무 경과 시간</li>
 * </ul>
 * 원시 기록 수정·삭제 API는 두지 않는다(정정은 별도 이슈). 권한(#19): 로그인 사용자 본인 기록만.
 * 사원은 서버가 확인한 {@link AuthUser#empId()}로 정한다.
 */
@RestController
@RequestMapping("/api/attendance/punches")
public class PunchController {

    private final PunchService service;

    public PunchController(PunchService service) {
        this.service = service;
    }

    @RequiresPermission
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PunchView punch(@Valid @RequestBody PunchRequest req, AuthUser user) {
        return service.punch(user.empId(), user.loginId(), req);
    }

    @RequiresPermission
    @GetMapping("/today")
    public TodayView today(AuthUser user) {
        return service.today(user.empId());
    }
}
