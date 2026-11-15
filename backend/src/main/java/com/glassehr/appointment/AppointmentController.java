package com.glassehr.appointment;

import com.glassehr.appointment.AppointmentDtos.AppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.appointment.AppointmentDtos.AssignmentView;
import com.glassehr.appointment.AppointmentDtos.CancelRequest;
import com.glassehr.appointment.AppointmentDtos.LeavePeriodView;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인사발령 API. 사유는 필수다(근로기준법 제23조 제1항).
 * 권한은 근로계약 API와 같게 조회는 {@code EMP_READ_ALL}, 등록·수정·확정·삭제는 {@code EMP_WRITE}.
 * <ul>
 *   <li>GET    /api/employees/{empId}/appointments — 사원의 발령 이력(발령일 순)</li>
 *   <li>POST   /api/employees/{empId}/appointments — 발령 등록(미확정)</li>
 *   <li>GET    /api/appointments?confirmed=&amp;type=&amp;from=&amp;to= — 발령 목록</li>
 *   <li>GET    /api/appointments/{apptId} — 발령 1건</li>
 *   <li>PUT    /api/appointments/{apptId} — 발령 수정(미확정만)</li>
 *   <li>POST   /api/appointments/{apptId}/confirm — 발령 확정</li>
 *   <li>DELETE /api/appointments/{apptId} — 발령 삭제(미확정만)</li>
 *   <li>POST   /api/appointments/{apptId}/cancel — 확정 발령을 되돌리는 취소 발령 등록(미확정, 확정은 confirm)</li>
 *   <li>GET    /api/employees/{empId}/assignment?date= — 기준일 소속·직급·직책·재직상태</li>
 *   <li>GET    /api/employees/{empId}/leave-periods?from=&amp;to= — 사원 휴직 기간</li>
 *   <li>GET    /api/leave-periods?from=&amp;to= — 전체 사원 휴직 기간(근태 집계용)</li>
 * </ul>
 */
@RestController
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/employees/{empId}/appointments")
    public List<AppointmentView> listByEmployee(@PathVariable long empId) {
        return service.listByEmployee(empId);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/api/employees/{empId}/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentView create(@PathVariable long empId, @Valid @RequestBody AppointmentRequest req) {
        return service.create(empId, req);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/appointments")
    public List<AppointmentView> search(@RequestParam(name = "confirmed", required = false) Boolean confirmed,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {
        return service.search(confirmed, type, from, to);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/appointments/{apptId}")
    public AppointmentView get(@PathVariable long apptId) {
        return service.get(apptId);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PutMapping("/api/appointments/{apptId}")
    public AppointmentView update(@PathVariable long apptId, @Valid @RequestBody AppointmentRequest req) {
        return service.update(apptId, req);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/api/appointments/{apptId}/confirm")
    public AppointmentView confirm(@PathVariable long apptId) {
        return service.confirm(apptId);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/api/appointments/{apptId}/cancel")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentView cancel(@PathVariable long apptId, @Valid @RequestBody CancelRequest req) {
        return service.cancel(apptId, req);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/employees/{empId}/assignment")
    public AssignmentView assignment(@PathVariable long empId, @RequestParam(name = "date") LocalDate date) {
        return service.assignmentOn(empId, date);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/employees/{empId}/leave-periods")
    public List<LeavePeriodView> leavePeriods(@PathVariable long empId, @RequestParam(name = "from") LocalDate from,
            @RequestParam(name = "to") LocalDate to) {
        return service.leavePeriods(empId, from, to);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/leave-periods")
    public List<LeavePeriodView> allLeavePeriods(@RequestParam(name = "from") LocalDate from,
            @RequestParam(name = "to") LocalDate to) {
        return service.leavePeriods(from, to);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @DeleteMapping("/api/appointments/{apptId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long apptId) {
        service.delete(apptId);
    }
}
