package com.glassehr.tenant;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.LoginService;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 관리자의 법인 관리(#130). 설계: docs/tenant-design.md 결정 4·6장 7번, docs/saas-design.md 2.1.
 * <ul>
 *   <li>목록·사용 중지는 세션 고객사의 법인만 다룬다. 다른 고객사 법인은 없는 것과 같다(404).</li>
 *   <li>새 법인은 세션 고객사에 소속시키고, 같은 고객사의 원본 법인에서 기준 데이터를 복사한 뒤 첫 관리자(ADMIN) 계정을 만든다.
 *       모두 한 트랜잭션이라 중간에 실패하면 법인 행까지 전부 되돌린다.</li>
 * </ul>
 * 복사하는 것: 공통코드(그룹·코드·휴가 종류 상세), 사용 중 사업장(코드·이름만, 사번 순번은 새 법인에서 1부터),
 * 지금·앞으로 유효한 결재 양식(단계 포함), 사용 중 근무유형 정의, 공휴일·대체공휴일·회사휴일.
 * 복사하지 않는 것: 사원·조직·발령·근태·결재 문서 등 업무 데이터, 근로자대표 서면합의와 그것이 필요한 휴일 대체(REPLACEMENT)
 * (서면합의는 법인마다 따로 해야 함 — 근로기준법 제55조 제2항 단서), 특정 사원을 결재자로 지정한 결재 양식(원본 법인 사원이라
 * 새 법인에서 결재자를 찾을 수 없음 — 응답의 skippedTemplates 로 알린다), 법령 기준값(legal_rate, 전 법인 공통).
 */
@Service
public class EnterCompanyAdminService {

    private static final Pattern BIZ_REG_NO = Pattern.compile("^[0-9]{10}$");
    static final int MAX_NAME = 100;
    static final int MAX_LOGIN_ID = 50;
    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 200;
    static final String FIRST_ADMIN_ROLE = "ADMIN";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EnterCompanyAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.clock = Clock.system(ZoneId.of("Asia/Seoul"));
    }

    public record CompanyView(String enterCd, String enterName, String bizRegNo, boolean active, int sortOrder) {
    }

    /** 새 법인 요청. 법인 코드는 만들 법인의 코드라 본문으로 받는다(세션 법인과 무관). */
    public record CreateRequest(String enterCd, String enterName, String bizRegNo, String sourceEnterCd,
            String adminLoginId, String adminPassword) {
    }

    /** copied: 표 이름 → 복사한 행 수. 비밀번호는 돌려주지 않는다. */
    public record CreateResult(CompanyView company, Map<String, Integer> copied, List<String> skippedTemplates,
            String adminLoginId) {
    }

    public record ActiveRequest(Boolean active) {
    }

    public static class AdminException extends RuntimeException {
        private final HttpStatus status;

        AdminException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }

    private static final String VIEW_SQL = "SELECT enter_cd, enter_name, biz_reg_no, use_yn, sort_order FROM enter_company ";

    private CompanyView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CompanyView(rs.getString("enter_cd"), rs.getString("enter_name"), rs.getString("biz_reg_no"),
                "Y".equals(rs.getString("use_yn")), rs.getInt("sort_order"));
    }

    /** 세션 고객사의 법인 목록(정렬 순서·코드 순, 사용 중지 포함). */
    public List<CompanyView> list(AuthUser user) {
        return jdbc.query(VIEW_SQL + "WHERE tenant_id = ? ORDER BY sort_order, enter_cd", (rs, i) -> view(rs),
                user.tenantId());
    }

    private CompanyView find(String tenantId, String enterCd) {
        if (!EnterCompany.isValidCode(enterCd)) {
            return null;
        }
        List<CompanyView> found = jdbc.query(VIEW_SQL + "WHERE enter_cd = ? AND tenant_id = ?", (rs, i) -> view(rs),
                enterCd, tenantId);
        return found.isEmpty() ? null : found.get(0);
    }

    @Transactional
    public CreateResult create(AuthUser user, CreateRequest req) {
        if (req == null) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "요청 본문이 없어요");
        }
        String enterCd = req.enterCd() == null ? null : req.enterCd().strip();
        String name = req.enterName() == null ? "" : req.enterName().strip();
        String bizRegNo = req.bizRegNo() == null ? "" : req.bizRegNo().replace("-", "").strip();
        String loginId = req.adminLoginId() == null ? "" : req.adminLoginId().strip();
        String password = req.adminPassword() == null ? "" : req.adminPassword();
        if (!EnterCompany.isValidCode(enterCd)) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "법인 코드는 영문 대문자·숫자 2~10자예요");
        }
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "법인 이름은 1~" + MAX_NAME + "자로 입력해 주세요");
        }
        if (!BIZ_REG_NO.matcher(bizRegNo).matches()) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "사업자등록번호는 숫자 10자리예요");
        }
        if (loginId.isEmpty() || loginId.length() > MAX_LOGIN_ID) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "관리자 아이디는 1~" + MAX_LOGIN_ID + "자로 입력해 주세요");
        }
        if (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new AdminException(HttpStatus.BAD_REQUEST,
                    "관리자 초기 비밀번호는 " + MIN_PASSWORD + "~" + MAX_PASSWORD + "자로 입력해 주세요");
        }
        // 원본은 같은 고객사 법인만. 다른 고객사 법인은 존재 여부도 드러내지 않는다(404).
        String source = req.sourceEnterCd() == null ? null : req.sourceEnterCd().strip();
        if (find(user.tenantId(), source) == null) {
            throw new AdminException(HttpStatus.NOT_FOUND, "복사할 원본 법인을 찾을 수 없어요");
        }
        // 법인 코드·사업자등록번호는 전체(모든 고객사)에서 유일하다(V031 PK·UNIQUE). 겹치면 409.
        Integer dup = jdbc.queryForObject("SELECT COUNT(*) FROM enter_company WHERE enter_cd = ? OR biz_reg_no = ?",
                Integer.class, enterCd, bizRegNo);
        if (dup != null && dup > 0) {
            throw new AdminException(HttpStatus.CONFLICT, "이미 쓰는 법인 코드나 사업자등록번호예요");
        }
        Integer maxSort = jdbc.queryForObject("SELECT NVL(MAX(sort_order), 0) FROM enter_company WHERE tenant_id = ?",
                Integer.class, user.tenantId());
        int sortOrder = (maxSort == null ? 0 : maxSort) + 10;
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order, tenant_id)"
                + " VALUES (?, ?, ?, 'Y', ?, ?)", enterCd, name, bizRegNo, sortOrder, user.tenantId());

        Map<String, Integer> copied = new LinkedHashMap<>();
        copied.put("common_code_group", jdbc.update("INSERT INTO common_code_group (enter_cd, group_code, group_name,"
                + " description, sort_order, use_yn, system_yn, edit_page) SELECT ?, group_code, group_name, description,"
                + " sort_order, use_yn, system_yn, edit_page FROM common_code_group WHERE enter_cd = ?", enterCd, source));
        copied.put("common_code", jdbc.update("INSERT INTO common_code (enter_cd, group_code, code, code_name, description,"
                + " sort_order, use_yn, system_yn, process_type) SELECT ?, group_code, code, code_name, description,"
                + " sort_order, use_yn, system_yn, process_type FROM common_code WHERE enter_cd = ?", enterCd, source));
        copied.put("attendance_leave_type", jdbc.update("INSERT INTO attendance_leave_type (enter_cd, group_code, code,"
                + " unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn, proof_required_yn, annual_limit_days, gender_limit,"
                + " legal_basis) SELECT ?, group_code, code, unit_day_yn, unit_half_day_yn, unit_hour_yn, paid_yn,"
                + " proof_required_yn, annual_limit_days, gender_limit, legal_basis FROM attendance_leave_type"
                + " WHERE enter_cd = ?", enterCd, source));
        // 사업장은 코드·이름만. 사번 순번(emp_no_seq)은 복사하지 않아 새 법인에서 처음부터 매긴다(EmpNoGenerator).
        copied.put("biz_site", jdbc.update("INSERT INTO biz_site (enter_cd, site_code, site_name, use_yn)"
                + " SELECT ?, site_code, site_name, 'Y' FROM biz_site WHERE enter_cd = ? AND use_yn = 'Y'", enterCd, source));
        copied.put("work_pattern", jdbc.update("INSERT INTO work_pattern (enter_cd, pattern_code, pattern_name,"
                + " work_start_min, work_end_min, break_start_min, break_minutes, day_types, daily_contract_minutes,"
                + " weekly_contract_minutes, use_yn, note, worker_type_code, full_time_baseline_yn) SELECT ?, pattern_code,"
                + " pattern_name, work_start_min, work_end_min, break_start_min, break_minutes, day_types,"
                + " daily_contract_minutes, weekly_contract_minutes, use_yn, note, worker_type_code, full_time_baseline_yn"
                + " FROM work_pattern WHERE enter_cd = ? AND use_yn = 'Y'", enterCd, source));
        // 휴일 대체(REPLACEMENT)는 원본 법인의 근로자대표 서면합의가 있어야 하는 행이라 복사하지 않는다(근로기준법 제55조 제2항 단서).
        copied.put("holiday", jdbc.update("INSERT INTO holiday (enter_cd, holiday_date, holiday_name, holiday_kind, paid_yn,"
                + " source_ref, verified_date, note, created_by) SELECT ?, holiday_date, holiday_name, holiday_kind, paid_yn,"
                + " source_ref, verified_date, note, ? FROM holiday WHERE enter_cd = ? AND holiday_kind <> 'REPLACEMENT'",
                enterCd, user.loginId(), source));

        List<String> skipped = new ArrayList<>();
        int templates = copyTemplates(source, enterCd, user.loginId(), skipped);
        copied.put("approval_template", templates);

        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("INSERT INTO app_user (enter_cd, login_id, emp_id, active_yn, password_hash, password_changed_at)"
                + " VALUES (?, ?, NULL, 'Y', ?, ?)", enterCd, loginId, LoginService.hash(password), Timestamp.valueOf(now));
        jdbc.update("INSERT INTO app_user_role (enter_cd, login_id, role_code) VALUES (?, ?, ?)", enterCd, loginId,
                FIRST_ADMIN_ROLE);
        return new CreateResult(new CompanyView(enterCd, name, bizRegNo, true, sortOrder), copied, skipped, loginId);
    }

    private record TemplateRow(long id, String requestType, java.sql.Date validFrom, java.sql.Date validTo,
            boolean namesEmployee) {
    }

    /** 지금·앞으로 유효한 결재 양식(종료일이 없거나 오늘 이후)과 단계를 새 template_id 로 복사한다. */
    private int copyTemplates(String source, String target, String by, List<String> skipped) {
        java.sql.Date today = java.sql.Date.valueOf(LocalDate.now(clock));
        List<TemplateRow> rows = jdbc.query("SELECT t.template_id, t.request_type, t.valid_from, t.valid_to,"
                + " (SELECT COUNT(*) FROM approval_template_step s WHERE s.enter_cd = t.enter_cd"
                + " AND s.template_id = t.template_id AND s.approver_kind = 'EMPLOYEE') AS emp_steps"
                + " FROM approval_template t WHERE t.enter_cd = ? AND (t.valid_to IS NULL OR t.valid_to > ?)"
                + " ORDER BY t.request_type, t.valid_from",
                (rs, i) -> new TemplateRow(rs.getLong("template_id"), rs.getString("request_type"),
                        rs.getDate("valid_from"), rs.getDate("valid_to"), rs.getInt("emp_steps") > 0),
                source, today);
        int n = 0;
        for (TemplateRow t : rows) {
            if (t.namesEmployee()) {
                skipped.add(t.requestType());
                continue;
            }
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement("INSERT INTO approval_template (enter_cd, request_type,"
                        + " valid_from, valid_to, created_by) VALUES (?, ?, ?, ?, ?)", new String[] {"template_id"});
                ps.setString(1, target);
                ps.setString(2, t.requestType());
                ps.setDate(3, t.validFrom());
                ps.setDate(4, t.validTo());
                ps.setString(5, by);
                return ps;
            }, key);
            Number newId = key.getKey();
            if (newId == null) {
                throw new IllegalStateException("결재 양식 ID를 받지 못했어요");
            }
            jdbc.update("INSERT INTO approval_template_step (enter_cd, template_id, step_no, approver_kind, approver_ref,"
                    + " skip_if_self) SELECT ?, ?, step_no, approver_kind, approver_ref, skip_if_self"
                    + " FROM approval_template_step WHERE enter_cd = ? AND template_id = ?",
                    target, newId.longValue(), source, t.id());
            n++;
        }
        return n;
    }

    /**
     * 사용 중지·다시 사용. 세션 고객사 법인만(아니면 404). 지금 로그인한 법인(주체)과 보고 있는 법인은 중지할 수 없다
     * (중지하면 그 세션이 바로 끊기고 다시 들어올 수 없게 된다).
     */
    @Transactional
    public CompanyView setActive(AuthUser user, String enterCd, ActiveRequest req) {
        if (req == null || req.active() == null) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "사용 여부(active)를 보내 주세요");
        }
        if (find(user.tenantId(), enterCd) == null) {
            throw new AdminException(HttpStatus.NOT_FOUND, "법인을 찾을 수 없어요");
        }
        if (!req.active() && (enterCd.equals(user.homeEnterCd()) || enterCd.equals(user.enterCd()))) {
            throw new AdminException(HttpStatus.CONFLICT, "로그인한 법인이나 지금 보고 있는 법인은 사용 중지할 수 없어요");
        }
        jdbc.update("UPDATE enter_company SET use_yn = ?, updated_at = SYSTIMESTAMP WHERE enter_cd = ? AND tenant_id = ?",
                req.active() ? "Y" : "N", enterCd, user.tenantId());
        return find(user.tenantId(), enterCd);
    }
}
