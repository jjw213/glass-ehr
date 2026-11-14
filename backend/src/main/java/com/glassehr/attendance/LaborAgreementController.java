package com.glassehr.attendance;

import com.glassehr.attendance.LaborAgreementDtos.AgreementDetail;
import com.glassehr.attendance.LaborAgreementDtos.AgreementRequest;
import com.glassehr.attendance.LaborAgreementDtos.AgreementView;
import com.glassehr.attendance.LaborAgreementDtos.CheckResult;
import com.glassehr.attendance.LaborAgreementDtos.ExpiringView;
import com.glassehr.attendance.LaborAgreementDtos.TypeView;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 근로자대표 서면합의서 API(#27).
 * <ul>
 *   <li>GET    /api/attendance/agreements/types — 합의 유형과 근거 조문</li>
 *   <li>GET    /api/attendance/agreements — 목록</li>
 *   <li>GET    /api/attendance/agreements/expiring?days= — 만료 예정 알림(기본 설정 일수)</li>
 *   <li>GET    /api/attendance/agreements/check?type=&amp;from=&amp;until=&amp;siteCode= — 유효 합의서 확인</li>
 *   <li>GET    /api/attendance/agreements/{id} — 상세(첨부 목록 포함)</li>
 *   <li>POST   /api/attendance/agreements — 등록</li>
 *   <li>PUT    /api/attendance/agreements/{id} — 수정(유형은 못 바꿈)</li>
 *   <li>POST   /api/attendance/agreements/{id}/files — 첨부(PDF·PNG·JPG)</li>
 *   <li>GET    /api/attendance/agreements/{id}/files/{fileId} — 첨부 내려받기</li>
 *   <li>DELETE /api/attendance/agreements/{id}/files/{fileId} — 첨부 삭제</li>
 * </ul>
 * 권한: 조회는 {@link Permission#EMP_READ_ALL}(관리자·인사·급여), 등록·수정·첨부는 {@link Permission#EMP_WRITE}(관리자·인사).
 * 근무유형(#25)과 같은 구분이다.
 */
@RestController
@RequestMapping("/api/attendance/agreements")
public class LaborAgreementController {

    private final LaborAgreementService service;
    private final LaborAgreementGuard guard;

    public LaborAgreementController(LaborAgreementService service, LaborAgreementGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/types")
    public List<TypeView> types() {
        return Arrays.stream(AgreementType.values()).map(t -> new TypeView(t, t.label(), t.article())).toList();
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping
    public List<AgreementView> list() {
        return service.list();
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/expiring")
    public ExpiringView expiring(@RequestParam(name = "days", required = false) Integer days) {
        return service.expiring(days);
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/check")
    public CheckResult check(@RequestParam("type") AgreementType type,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "until", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until,
            @RequestParam(name = "siteCode", required = false) String siteCode) {
        return guard.check(type, from, until, siteCode == null || siteCode.isBlank() ? null : siteCode.trim());
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/{agreementId}")
    public AgreementDetail get(@PathVariable long agreementId) {
        return service.get(agreementId);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgreementView create(@Valid @RequestBody AgreementRequest req, AuthUser user) {
        return service.create(req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PutMapping("/{agreementId}")
    public AgreementView update(@PathVariable long agreementId, @Valid @RequestBody AgreementRequest req,
            AuthUser user) {
        return service.update(agreementId, req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping(value = "/{agreementId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AgreementFileView upload(@PathVariable long agreementId, @RequestParam("file") MultipartFile file,
            AuthUser user) throws IOException {
        return service.addFile(agreementId, file.getOriginalFilename(), file.getBytes(), user.loginId());
    }

    @RequiresPermission(Permission.EMP_READ_ALL)
    @GetMapping("/{agreementId}/files/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable long agreementId, @PathVariable long fileId) {
        LaborAgreementFile f = service.file(agreementId, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(f.getContent());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @DeleteMapping("/{agreementId}/files/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(@PathVariable long agreementId, @PathVariable long fileId) {
        service.deleteFile(agreementId, fileId);
    }
}
