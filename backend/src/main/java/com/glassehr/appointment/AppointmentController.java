package com.glassehr.appointment;

import com.glassehr.appointment.AppointmentDtos.AppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.AppointmentView;
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
 * <ul>
 *   <li>GET    /api/employees/{empId}/appointments — 사원의 발령 이력(발령일 순)</li>
 *   <li>POST   /api/employees/{empId}/appointments — 발령 등록(미확정)</li>
 *   <li>GET    /api/appointments?confirmed=&amp;type=&amp;from=&amp;to= — 발령 목록</li>
 *   <li>GET    /api/appointments/{apptId} — 발령 1건</li>
 *   <li>PUT    /api/appointments/{apptId} — 발령 수정(미확정만)</li>
 *   <li>POST   /api/appointments/{apptId}/confirm — 발령 확정</li>
 *   <li>DELETE /api/appointments/{apptId} — 발령 삭제(미확정만)</li>
 * </ul>
 */
@RestController
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping("/api/employees/{empId}/appointments")
    public List<AppointmentView> listByEmployee(@PathVariable long empId) {
        return service.listByEmployee(empId);
    }

    @PostMapping("/api/employees/{empId}/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentView create(@PathVariable long empId, @Valid @RequestBody AppointmentRequest req) {
        return service.create(empId, req);
    }

    @GetMapping("/api/appointments")
    public List<AppointmentView> search(@RequestParam(name = "confirmed", required = false) Boolean confirmed,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {
        return service.search(confirmed, type, from, to);
    }

    @GetMapping("/api/appointments/{apptId}")
    public AppointmentView get(@PathVariable long apptId) {
        return service.get(apptId);
    }

    @PutMapping("/api/appointments/{apptId}")
    public AppointmentView update(@PathVariable long apptId, @Valid @RequestBody AppointmentRequest req) {
        return service.update(apptId, req);
    }

    @PostMapping("/api/appointments/{apptId}/confirm")
    public AppointmentView confirm(@PathVariable long apptId) {
        return service.confirm(apptId);
    }

    @DeleteMapping("/api/appointments/{apptId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long apptId) {
        service.delete(apptId);
    }
}
