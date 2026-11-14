package com.glassehr.attendance;

import com.glassehr.attendance.HolidayDtos.AgreementRequest;
import com.glassehr.attendance.HolidayDtos.AgreementView;
import com.glassehr.attendance.HolidayDtos.CompanyHolidayRequest;
import com.glassehr.attendance.HolidayDtos.HolidayView;
import com.glassehr.attendance.HolidayDtos.ImportResult;
import com.glassehr.attendance.HolidayDtos.SubstitutionRequest;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.auth.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 휴일 달력 API(#26).
 * <ul>
 *   <li>GET    /api/attendance/holidays?year= — 그 해 휴일 달력(기본 올해)</li>
 *   <li>POST   /api/attendance/holidays/import — 사람이 넣은 공휴일 CSV 불러오기(text/csv, {@link PublicHolidayCsv})</li>
 *   <li>POST   /api/attendance/holidays/company — 회사휴일 등록</li>
 *   <li>POST   /api/attendance/holidays/substitutions — 휴일 대체 등록(근로자대표 서면합의 필수)</li>
 *   <li>DELETE /api/attendance/holidays/{id} — 앞으로 올 회사휴일·휴일 대체 취소</li>
 *   <li>GET    /api/attendance/agreements — 휴일 대체 서면합의 목록</li>
 *   <li>POST   /api/attendance/agreements — 서면합의 등록</li>
 * </ul>
 * 권한(#19): 달력 조회는 로그인 사용자 모두(개인정보 없음). 그 밖의 등록·취소와 서면합의 조회는
 * {@link Permission#EMP_WRITE}(관리자·인사). 등록자는 서버가 확인한 {@link AuthUser}에서 받는다.
 */
@RestController
@RequestMapping("/api/attendance")
public class HolidayController {

    private final HolidayService service;

    public HolidayController(HolidayService service) {
        this.service = service;
    }

    @RequiresPermission
    @GetMapping("/holidays")
    public List<HolidayView> year(@RequestParam(name = "year", required = false) Integer year) {
        return service.year(year != null ? year : service.today().getYear());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping(value = "/holidays/import", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    public ImportResult importCsv(@RequestBody String csv, AuthUser user) {
        return service.importPublicHolidays(csv, user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/holidays/company")
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayView company(@Valid @RequestBody CompanyHolidayRequest req, AuthUser user) {
        return service.createCompanyHoliday(req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/holidays/substitutions")
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayView substitute(@Valid @RequestBody SubstitutionRequest req, AuthUser user) {
        return service.substitute(req, user.loginId());
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @DeleteMapping("/holidays/{holidayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long holidayId) {
        service.delete(holidayId);
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @GetMapping("/agreements")
    public List<AgreementView> agreements() {
        return service.agreements();
    }

    @RequiresPermission(Permission.EMP_WRITE)
    @PostMapping("/agreements")
    @ResponseStatus(HttpStatus.CREATED)
    public AgreementView createAgreement(@Valid @RequestBody AgreementRequest req, AuthUser user) {
        return service.createAgreement(req, user.loginId());
    }
}
