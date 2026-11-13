package com.glassehr.attendance;

import com.glassehr.attendance.WorkPatternDtos.AssignRequest;
import com.glassehr.attendance.WorkPatternDtos.AssignmentView;
import com.glassehr.attendance.WorkPatternDtos.WorkPatternRequest;
import com.glassehr.attendance.WorkPatternDtos.WorkPatternView;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 근무유형·배정 API(#25).
 * <ul>
 *   <li>GET  /api/attendance/work-patterns — 근무유형 목록</li>
 *   <li>POST /api/attendance/work-patterns/check — 저장 없이 법정 기준 검사(화면 미리보기)</li>
 *   <li>POST /api/attendance/work-patterns — 근무유형 등록(위반 시 422)</li>
 *   <li>PUT  /api/attendance/work-patterns/{id} — 근무유형 수정(배정된 유형은 이름·사용 여부·비고만)</li>
 *   <li>GET  /api/attendance/employees/{empId}/work-patterns — 사원 배정 이력</li>
 *   <li>GET  /api/attendance/employees/{empId}/work-patterns/effective?date= — 기준일 배정</li>
 *   <li>POST /api/attendance/employees/{empId}/work-patterns — 배정 추가(적용 시작일부터)</li>
 * </ul>
 * 쓰기 권한(인사담당) 검사는 백엔드 인증·권한 이슈에서 붙인다. 요청자는 임시로 {@code X-Actor-Id} 헤더에서 받는다.
 */
@RestController
@RequestMapping("/api/attendance")
public class WorkPatternController {

    private static final String ANONYMOUS = "anonymous";

    private final WorkPatternService service;

    public WorkPatternController(WorkPatternService service) {
        this.service = service;
    }

    @GetMapping("/work-patterns")
    public List<WorkPatternView> list() {
        return service.list();
    }

    @PostMapping("/work-patterns/check")
    public Map<String, Object> check(@Valid @RequestBody WorkPatternRequest req) {
        WorkPatternRules.Result r = service.check(req.spec());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", r.ok());
        m.put("dailyContractMinutes", r.dailyContractMinutes());
        m.put("weeklyContractMinutes", r.weeklyContractMinutes());
        m.put("violations", WorkPatternDtos.ViolationView.of(r.violations()));
        return m;
    }

    @PostMapping("/work-patterns")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkPatternView create(@Valid @RequestBody WorkPatternRequest req) {
        return service.create(req);
    }

    @PutMapping("/work-patterns/{patternId}")
    public WorkPatternView update(@PathVariable long patternId, @Valid @RequestBody WorkPatternRequest req) {
        return service.update(patternId, req);
    }

    @GetMapping("/employees/{empId}/work-patterns")
    public List<AssignmentView> history(@PathVariable long empId) {
        return service.history(empId);
    }

    @GetMapping("/employees/{empId}/work-patterns/effective")
    public ResponseEntity<AssignmentView> effective(@PathVariable long empId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.effective(empId, date != null ? date : service.today())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/employees/{empId}/work-patterns")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentView assign(@PathVariable long empId, @Valid @RequestBody AssignRequest req,
            @RequestHeader(name = "X-Actor-Id", required = false) String actorId) {
        return service.assign(empId, req, actor(actorId));
    }

    private static String actor(String id) {
        if (id == null || id.isBlank()) {
            return ANONYMOUS;
        }
        String s = id.strip();
        return s.length() > 50 ? s.substring(0, 50) : s;
    }
}
