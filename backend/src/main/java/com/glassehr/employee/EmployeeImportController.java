package com.glassehr.employee;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import com.glassehr.employee.EmployeeImportService.Confirmed;
import com.glassehr.employee.EmployeeImportService.Preview;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사원 엑셀(CSV) 일괄 등록(#20). 모두 사원 등록 권한(EMP_WRITE: 관리자·인사)만.
 * <ul>
 *   <li>GET  /api/employees/import/template — 양식(머리글만, UTF-8 BOM CSV)</li>
 *   <li>POST /api/employees/import/preview  — multipart {@code file}. 행별 오류 미리보기(저장 안 함)</li>
 *   <li>POST /api/employees/import/confirm  — 같은 파일. 다시 검사해 오류가 없을 때만 모두 등록(201), 있으면 422 + 미리보기</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/employees/import")
public class EmployeeImportController {

    private final EmployeeImportService imports;

    public EmployeeImportController(EmployeeImportService imports) {
        this.imports = imports;
    }

    @GetMapping("/template")
    @RequiresPermission(Permission.EMP_WRITE)
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("employee-import-template.csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(EmployeeImportCsv.template());
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(Permission.EMP_WRITE)
    public ResponseEntity<Preview> preview(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(imports.preview(file.getBytes()));
    }

    @PostMapping(value = "/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.EMP_WRITE)
    public Confirmed confirm(@RequestParam("file") MultipartFile file, AuthUser user) throws IOException {
        return imports.confirm(file.getBytes(), user.toActor());
    }
}
