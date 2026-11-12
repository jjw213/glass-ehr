package com.glassehr.employee;

import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeSummary;
import com.glassehr.employee.EmployeeDtos.EmployeeUpdateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeView;
import com.glassehr.employee.EmployeeDtos.RetirementRequest;
import com.glassehr.employee.EmployeeDtos.RrnRequest;
import com.glassehr.employee.EmployeeDtos.RrnReveal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
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
 * 사원(근로자 명부) API.
 * <ul>
 *   <li>GET  /api/employees?includeRetired=false — 목록(주소·주민등록번호 없음)</li>
 *   <li>GET  /api/employees/{empId} — 상세(주민등록번호는 마스킹 값)</li>
 *   <li>POST /api/employees — 입사 등록(사번 자동 채번)</li>
 *   <li>PUT  /api/employees/{empId} — 기본정보 수정(소속·직급·직책은 받지 않음: 인사발령으로만 바뀜)</li>
 *   <li>POST /api/employees/{empId}/retire — 해고·퇴직·사망 처리(보존기한 계산)</li>
 *   <li>PUT  /api/employees/{empId}/rrn — 주민등록번호 등록·정정</li>
 *   <li>GET  /api/employees/{empId}/rrn?purpose=VIEW — 원문 조회(인사·급여 담당만, 매번 조회 로그)</li>
 * </ul>
 * 사원을 지우는 API는 두지 않는다(근로기준법 제42조 보존). 요청자는 임시로 X-Actor-Id·X-Actor-Role 헤더에서 읽는다
 * ({@link RequestActor} 참고 — 인증 이슈에서 교체).
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    static final String ACTOR_ID = "X-Actor-Id";
    static final String ACTOR_ROLE = "X-Actor-Role";

    private final EmployeeService service;
    private final RrnService rrns;

    public EmployeeController(EmployeeService service, RrnService rrns) {
        this.service = service;
        this.rrns = rrns;
    }

    private static RequestActor actor(String id, String role, HttpServletRequest http) {
        return RequestActor.of(id, role, http.getRemoteAddr());
    }

    @GetMapping
    public List<EmployeeSummary> list(@RequestParam(name = "includeRetired", defaultValue = "false") boolean includeRetired) {
        return service.list(includeRetired);
    }

    @GetMapping("/{empId}")
    public EmployeeView get(@PathVariable long empId) {
        return service.get(empId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeView create(@Valid @RequestBody EmployeeCreateRequest req,
            @RequestHeader(name = ACTOR_ID, required = false) String actorId,
            @RequestHeader(name = ACTOR_ROLE, required = false) String actorRole, HttpServletRequest http) {
        return service.create(req, actor(actorId, actorRole, http));
    }

    @PutMapping("/{empId}")
    public EmployeeView update(@PathVariable long empId, @Valid @RequestBody EmployeeUpdateRequest req) {
        return service.update(empId, req);
    }

    @PostMapping("/{empId}/retire")
    public EmployeeView retire(@PathVariable long empId, @Valid @RequestBody RetirementRequest req) {
        return service.retire(empId, req);
    }

    @PutMapping("/{empId}/rrn")
    public Map<String, String> saveRrn(@PathVariable long empId, @Valid @RequestBody RrnRequest req,
            @RequestHeader(name = ACTOR_ID, required = false) String actorId,
            @RequestHeader(name = ACTOR_ROLE, required = false) String actorRole, HttpServletRequest http) {
        service.load(empId);
        return Map.of("rrnMasked", rrns.save(empId, req.rrn(), actor(actorId, actorRole, http)));
    }

    /** 원문 조회. 응답은 캐시하지 않는다(docs/rrn-design.md 3장). */
    @GetMapping("/{empId}/rrn")
    public ResponseEntity<RrnReveal> revealRrn(@PathVariable long empId,
            @RequestParam(name = "purpose", defaultValue = "VIEW") RrnAccessLog.Purpose purpose,
            @RequestHeader(name = ACTOR_ID, required = false) String actorId,
            @RequestHeader(name = ACTOR_ROLE, required = false) String actorRole, HttpServletRequest http) {
        Rrn rrn = rrns.reveal(empId, actor(actorId, actorRole, http), purpose);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(new RrnReveal(empId, rrn.plain()));
    }
}
