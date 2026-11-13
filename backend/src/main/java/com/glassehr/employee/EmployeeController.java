package com.glassehr.employee;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.PrivacyAccessLog;
import com.glassehr.auth.PrivacyAccessLogger;
import com.glassehr.auth.PrivacyAudit;
import com.glassehr.auth.RequiresPermission;
import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeSummary;
import com.glassehr.employee.EmployeeDtos.EmployeeUpdateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeView;
import com.glassehr.employee.EmployeeDtos.RetirementRequest;
import com.glassehr.employee.EmployeeDtos.RrnRequest;
import com.glassehr.employee.EmployeeDtos.RrnReveal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * 사원(근로자 명부) API. 권한은 API 단에서 검사한다(#19, docs/auth-design.md).
 * <ul>
 *   <li>GET  /api/employees?includeRetired=false — 목록(주소·주민등록번호 없음). 데이터 범위: 전체 / 부서장은 맡은 부서·하위부서 / 본인</li>
 *   <li>GET  /api/employees/export?includeRetired=false — 목록 CSV 내려받기(대상마다 다운로드 로그)</li>
 *   <li>GET  /api/employees/{empId} — 상세(주민등록번호는 마스킹 값). 인적사항 원문 권한이 없으면 서버에서 가린다.
 *       인적사항 원문을 돌려줄 때·범위 밖 거부·대상 없음은 개인정보 조회 로그를 남긴다.</li>
 *   <li>POST /api/employees — 입사 등록(사번 자동 채번)</li>
 *   <li>PUT  /api/employees/{empId} — 기본정보 수정(소속·직급·직책은 받지 않음: 인사발령으로만 바뀜)</li>
 *   <li>POST /api/employees/{empId}/retire — 해고·퇴직·사망 처리(보존기한 계산)</li>
 *   <li>PUT  /api/employees/{empId}/rrn — 주민등록번호 등록·정정</li>
 *   <li>GET  /api/employees/{empId}/rrn?purpose=VIEW — 원문 조회(인사·급여 담당만, 매번 조회 로그)</li>
 * </ul>
 * 사원을 지우는 API는 두지 않는다(근로기준법 제42조 보존).
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService service;
    private final RrnService rrns;
    private final PrivacyAccessLogger privacyLog;

    public EmployeeController(EmployeeService service, RrnService rrns, PrivacyAccessLogger privacyLog) {
        this.service = service;
        this.rrns = rrns;
        this.privacyLog = privacyLog;
    }

    private List<EmployeeSummary> visible(AuthUser user, boolean includeRetired) {
        return service.list(includeRetired).stream()
                .filter(e -> user.canSeeEmployee(e.empId(), e.currentDeptId()))
                .toList();
    }

    @GetMapping
    @RequiresPermission({Permission.EMP_READ_ALL, Permission.EMP_READ_DEPT, Permission.EMP_READ_SELF})
    public List<EmployeeSummary> list(@RequestParam(name = "includeRetired", defaultValue = "false") boolean includeRetired,
            AuthUser user) {
        return visible(user, includeRetired);
    }

    /**
     * 목록 CSV. 사번·성명·성별·소속부서ID·직급·직책·고용형태·입사일·퇴직일(주소·생년월일·주민등록번호는 넣지 않음).
     * 파일을 만들기 전에 대상마다 다운로드 로그를 남긴다(로그를 못 남기면 파일도 나가지 않음).
     */
    @GetMapping("/export")
    @RequiresPermission(Permission.EMP_EXPORT)
    @PrivacyAudit(item = PrivacyAccessLog.Item.EMP_LIST, action = PrivacyAccessLog.Action.DOWNLOAD)
    public ResponseEntity<byte[]> export(@RequestParam(name = "includeRetired", defaultValue = "false") boolean includeRetired,
            AuthUser user, HttpServletRequest http) {
        List<EmployeeSummary> rows = visible(user, includeRetired);
        privacyLog.logEach(user, rows.stream().map(EmployeeSummary::empId).toList(), PrivacyAccessLog.Item.EMP_LIST,
                PrivacyAccessLog.Action.DOWNLOAD, http.getRequestURI());
        byte[] body = EmployeeCsv.write(rows).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("employees.csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/{empId}")
    @RequiresPermission({Permission.EMP_READ_ALL, Permission.EMP_READ_DEPT, Permission.EMP_READ_SELF})
    @PrivacyAudit(item = PrivacyAccessLog.Item.EMP_PERSONAL, action = PrivacyAccessLog.Action.VIEW)
    public EmployeeView get(@PathVariable long empId, AuthUser user, HttpServletRequest http) {
        String path = http.getRequestURI();
        EmployeeView v;
        try {
            v = service.get(empId);
        } catch (EmployeeException e) {
            if (e.status() == HttpStatus.NOT_FOUND) {
                privacyLog.log(user, empId, PrivacyAccessLog.Item.EMP_PERSONAL, PrivacyAccessLog.Action.VIEW,
                        PrivacyAccessLog.Result.NOT_FOUND, path);
            }
            throw e;
        }
        if (!user.canSeeEmployee(empId, v.currentDeptId())) {
            privacyLog.log(user, empId, PrivacyAccessLog.Item.EMP_PERSONAL, PrivacyAccessLog.Action.VIEW,
                    PrivacyAccessLog.Result.DENIED, path);
            throw EmployeeException.forbidden("이 사원의 정보를 볼 권한이 없어요");
        }
        if (!user.canSeePersonal(empId)) {
            return v.withoutPersonal();
        }
        privacyLog.log(user, empId, PrivacyAccessLog.Item.EMP_PERSONAL, PrivacyAccessLog.Action.VIEW,
                PrivacyAccessLog.Result.SUCCESS, path);
        return v;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.EMP_WRITE)
    public EmployeeView create(@Valid @RequestBody EmployeeCreateRequest req, AuthUser user) {
        return service.create(req, user.toActor());
    }

    @PutMapping("/{empId}")
    @RequiresPermission(Permission.EMP_WRITE)
    public EmployeeView update(@PathVariable long empId, @Valid @RequestBody EmployeeUpdateRequest req) {
        return service.update(empId, req);
    }

    @PostMapping("/{empId}/retire")
    @RequiresPermission(Permission.EMP_WRITE)
    public EmployeeView retire(@PathVariable long empId, @Valid @RequestBody RetirementRequest req) {
        return service.retire(empId, req);
    }

    @PutMapping("/{empId}/rrn")
    @RequiresPermission(Permission.EMP_WRITE)
    public Map<String, String> saveRrn(@PathVariable long empId, @Valid @RequestBody RrnRequest req, AuthUser user) {
        service.load(empId);
        return Map.of("rrnMasked", rrns.save(empId, req.rrn(), user.toActor()));
    }

    /**
     * 원문 조회. 응답은 캐시하지 않는다(docs/rrn-design.md 3장). RRN_REVEAL 권한이 없으면 여기서 막고 거부 로그를 남긴다.
     * 통과한 요청은 RrnService 가 역할을 다시 확인하고 rrn_access_log 에 남긴다.
     */
    @GetMapping("/{empId}/rrn")
    @RequiresPermission(Permission.RRN_REVEAL)
    @PrivacyAudit(item = PrivacyAccessLog.Item.RRN, action = PrivacyAccessLog.Action.VIEW)
    public ResponseEntity<RrnReveal> revealRrn(@PathVariable long empId,
            @RequestParam(name = "purpose", defaultValue = "VIEW") RrnAccessLog.Purpose purpose, AuthUser user) {
        Rrn rrn = rrns.reveal(empId, user.toActor(), purpose);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(new RrnReveal(empId, rrn.plain()));
    }
}
