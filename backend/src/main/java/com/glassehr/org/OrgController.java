package com.glassehr.org;

import com.glassehr.org.OrgDtos.DeptChangeRequest;
import com.glassehr.org.OrgDtos.DeptCloseRequest;
import com.glassehr.org.OrgDtos.DeptCreateRequest;
import com.glassehr.org.OrgDtos.DeptView;
import com.glassehr.org.OrgDtos.OrgTree;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
 * 조직(부서) API.
 * <ul>
 *   <li>GET  /api/org/tree?date=YYYY-MM-DD — 기준일 조직 트리(date 생략 시 서울 기준 오늘)</li>
 *   <li>POST /api/org/depts — 부서 신설</li>
 *   <li>PUT  /api/org/depts/{deptId} — 부서 변경(이름·상위부서·부서장·정렬순서, 적용일부터)</li>
 *   <li>POST /api/org/depts/{deptId}/close — 부서 폐지(폐지일부터)</li>
 * </ul>
 * 이력을 지우는 API는 두지 않는다(이력은 추가로만 쌓음). 쓰기 권한(인사담당) 검사는 백엔드 인증·권한 이슈에서 붙인다.
 */
@RestController
@RequestMapping("/api/org")
public class OrgController {

    private final OrgDeptService service;

    public OrgController(OrgDeptService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    public OrgTree tree(@RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.tree(date != null ? date : service.today());
    }

    @PostMapping("/depts")
    @ResponseStatus(HttpStatus.CREATED)
    public DeptView create(@Valid @RequestBody DeptCreateRequest req) {
        return service.create(req);
    }

    @PutMapping("/depts/{deptId}")
    public DeptView change(@PathVariable long deptId, @Valid @RequestBody DeptChangeRequest req) {
        return service.change(deptId, req);
    }

    @PostMapping("/depts/{deptId}/close")
    public DeptView close(@PathVariable long deptId, @Valid @RequestBody DeptCloseRequest req) {
        return service.close(deptId, req);
    }
}
