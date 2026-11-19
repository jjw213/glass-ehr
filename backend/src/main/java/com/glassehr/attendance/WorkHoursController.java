package com.glassehr.attendance;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일별 근로시간 집계 API(#29).
 * <ul>
 *   <li>GET  /api/attendance/daily-summaries?empId=&amp;from=&amp;to= — 저장된 집계 조회(EMP_READ_ALL)</li>
 *   <li>POST /api/attendance/daily-summaries/recalculate — 기간을 주 단위로 넓혀 다시 계산·저장(EMP_WRITE)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/attendance/daily-summaries")
public class WorkHoursController {

    private final WorkHoursService service;

    public WorkHoursController(WorkHoursService service) {
        this.service = service;
    }

    public record RecalculateRequest(
            @NotNull(message = "사원(empId)을 보내 주세요") Long empId,
            @NotNull(message = "시작 날짜(from)를 보내 주세요") LocalDate from,
            @NotNull(message = "끝 날짜(to)를 보내 주세요") LocalDate to) {
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping
    public List<DailyWorkSummaryRow> find(@RequestParam long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.find(empId, from, to);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/recalculate")
    public List<DailyWorkSummaryRow> recalculate(@Valid @RequestBody RecalculateRequest req, AuthUser user) {
        return service.recalculate(req.empId(), req.from(), req.to(), user.loginId());
    }
}
