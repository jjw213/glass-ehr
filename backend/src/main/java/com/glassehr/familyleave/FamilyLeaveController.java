package com.glassehr.familyleave;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import com.glassehr.familyleave.FamilyLeaveDtos.BalanceView;
import com.glassehr.familyleave.FamilyLeaveDtos.CreateRequest;
import com.glassehr.familyleave.FamilyLeaveDtos.DecisionRequest;
import com.glassehr.familyleave.FamilyLeaveDtos.RequestView;
import com.glassehr.familyleave.FamilyLeaveRequest.Status;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 육아휴직·육아기 근로시간 단축·가족돌봄휴직 API(#37). 설계: docs/family-leave-design.md.
 * <ul>
 *   <li>POST /api/employees/{empId}/family-leaves — 신청 등록(서면 신청을 인사 담당자가 입력)</li>
 *   <li>GET  /api/employees/{empId}/family-leaves — 사원의 신청 목록</li>
 *   <li>GET  /api/employees/{empId}/family-leave-balance?asOf= — 남은 사용 가능 기간</li>
 *   <li>GET  /api/family-leaves?status= — 전체 신청(승인 대기 목록 등)</li>
 *   <li>GET  /api/family-leaves/{reqId} — 신청 1건</li>
 *   <li>POST /api/family-leaves/{reqId}/approve · /reject · /cancel — 승인(발령·근무유형 생성)·반려·취소</li>
 * </ul>
 * 권한(#19): 조회는 전체 사원 조회(관리자·인사·급여), 등록·승인·반려·취소는 사원 쓰기(관리자·인사).
 */
@RestController
public class FamilyLeaveController {

    private final FamilyLeaveService service;

    public FamilyLeaveController(FamilyLeaveService service) {
        this.service = service;
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/api/employees/{empId}/family-leaves")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView create(@PathVariable long empId, @Valid @RequestBody CreateRequest req, AuthUser user) {
        return service.create(user.enterCd(), empId, req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/employees/{empId}/family-leaves")
    public List<RequestView> listByEmployee(@PathVariable long empId, AuthUser user) {
        return service.listByEmployee(user.enterCd(), empId);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/employees/{empId}/family-leave-balance")
    public BalanceView balance(@PathVariable long empId,
            @RequestParam(name = "asOf", required = false) LocalDate asOf, AuthUser user) {
        return service.balance(user.enterCd(), empId, asOf);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/family-leaves")
    public List<RequestView> search(@RequestParam(name = "status", required = false) Status status, AuthUser user) {
        return service.search(user.enterCd(), status);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/api/family-leaves/{reqId}")
    public RequestView get(@PathVariable long reqId, AuthUser user) {
        return service.get(user.enterCd(), reqId);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/api/family-leaves/{reqId}/approve")
    public RequestView approve(@PathVariable long reqId, AuthUser user) {
        return service.approve(user.enterCd(), reqId, user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/api/family-leaves/{reqId}/reject")
    public RequestView reject(@PathVariable long reqId, @Valid @RequestBody DecisionRequest req, AuthUser user) {
        return service.reject(user.enterCd(), reqId, req.note(), user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/api/family-leaves/{reqId}/cancel")
    public RequestView cancel(@PathVariable long reqId, @Valid @RequestBody(required = false) DecisionRequest req,
            AuthUser user) {
        return service.cancel(user.enterCd(), reqId, req == null ? null : req.note(), user.loginId());
    }
}
