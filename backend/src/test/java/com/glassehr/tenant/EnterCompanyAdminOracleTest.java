package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.auth.AuthUser;
import com.glassehr.tenant.EnterCompanyAdminService.ActiveRequest;
import com.glassehr.tenant.EnterCompanyAdminService.AdminException;
import com.glassehr.tenant.EnterCompanyAdminService.CompanyView;
import com.glassehr.tenant.EnterCompanyAdminService.CreateRequest;
import com.glassehr.tenant.EnterCompanyAdminService.CreateResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #130 새 법인 만들기와 기준 데이터 복사를 Oracle 실DB에서 검증한다. 원본 법인 ZZIT1(T001)에 기준 데이터를 넣고 새 법인 ZZIT2 로
 * 복사한다. 다른 고객사(ZT9) 법인 ZZIT3 은 원본·목록·사용 중지 모두 404/안 보임. 실행 조건: {@code ORACLE_IT=true}.
 * 각 테스트는 롤백한다. 1900년대 날짜, ZZ 코드, 예시 사업자등록번호만 쓴다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class EnterCompanyAdminOracleTest {

    private static final String SRC = "ZZIT1";
    private static final String NEW = "ZZIT2";
    private static final String OTHER = "ZZIT3";
    private static final String PW = "example-pass-1";

    private final AuthUser group = new AuthUser("T001", "C001", "C001", "it.group", null, Set.of("GROUP_ADMIN"),
            Set.of(), Set.of(), "127.0.0.1");

    @Autowired
    private EnterCompanyAdminService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager tx;

    @BeforeEach
    void 원본_법인과_다른_고객사_법인을_넣는다() {
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order, tenant_id)"
                + " VALUES (?, 'IT 원본 법인(예시)', '0000000191', 'Y', 0, 'T001')", SRC);
        jdbc.update("INSERT INTO tenant (tenant_id, tenant_name, slug, status) VALUES ('ZT9', 'IT 고객사(예시)', 'zt9-it', 'ACTIVE')");
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order, tenant_id)"
                + " VALUES (?, 'IT 다른 고객사 법인(예시)', '0000000192', 'Y', 0, 'ZT9')", OTHER);

        jdbc.update("INSERT INTO common_code_group (enter_cd, group_code, group_name, sort_order, use_yn)"
                + " VALUES (?, 'ZZ_IT_GROUP', 'IT 그룹', 0, 'Y')", SRC);
        jdbc.update("INSERT INTO common_code (enter_cd, group_code, code, code_name, sort_order, use_yn)"
                + " VALUES (?, 'ZZ_IT_GROUP', 'ZZ_A', 'IT 코드', 0, 'Y')", SRC);
        jdbc.update("INSERT INTO biz_site (enter_cd, site_code, site_name, use_yn) VALUES (?, 'S01', 'IT 본사', 'Y')", SRC);
        jdbc.update("INSERT INTO biz_site (enter_cd, site_code, site_name, use_yn) VALUES (?, 'S02', 'IT 폐쇄', 'N')", SRC);
        jdbc.update("INSERT INTO emp_no_seq (enter_cd, site_code, hire_year, last_seq) VALUES (?, 'S01', 1901, 7)", SRC);
        jdbc.update("INSERT INTO work_pattern (enter_cd, pattern_code, pattern_name, work_start_min, work_end_min,"
                + " break_start_min, break_minutes, day_types, daily_contract_minutes, weekly_contract_minutes, use_yn)"
                + " VALUES (?, 'ZZ_DAY', 'IT 주간', 540, 1080, 720, 60, 'WWWWWOH', 480, 2400, 'Y')", SRC);
        jdbc.update("INSERT INTO holiday (enter_cd, holiday_date, holiday_name, holiday_kind, paid_yn, source_ref,"
                + " verified_date, created_by) VALUES (?, DATE '1901-03-01', 'IT 공휴일', 'PUBLIC', 'Y', 'IT 예시 출처',"
                + " DATE '1901-01-01', 'it')", SRC);
        jdbc.update("INSERT INTO holiday (enter_cd, holiday_date, holiday_name, holiday_kind, paid_yn, created_by)"
                + " VALUES (?, DATE '1901-05-02', 'IT 창립기념일', 'COMPANY', 'N', 'it')", SRC);
        template("ZZ_IT_ROLE", "DATE '1901-01-01'", "NULL", "ROLE", "'HR'");
        template("ZZ_IT_EMP", "DATE '1901-01-01'", "NULL", "EMPLOYEE", "'1'");
        template("ZZ_IT_OLD", "DATE '1900-01-01'", "DATE '1900-12-31'", "DEPT_HEAD", "NULL");
    }

    private void template(String type, String from, String to, String kind, String ref) {
        jdbc.update("INSERT INTO approval_template (enter_cd, request_type, valid_from, valid_to, created_by)"
                + " VALUES (?, ?, " + from + ", " + to + ", 'it')", SRC, type);
        Long id = jdbc.queryForObject("SELECT template_id FROM approval_template WHERE enter_cd = ? AND request_type = ?",
                Long.class, SRC, type);
        jdbc.update("INSERT INTO approval_template_step (enter_cd, template_id, step_no, approver_kind, approver_ref)"
                + " VALUES (?, ?, 1, ?, " + ref + ")", SRC, id, kind);
    }

    private int count(String table, String enterCd) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE enter_cd = ?", Integer.class, enterCd);
        return n == null ? 0 : n;
    }

    private CreateResult create(String cd, String biz, String source) {
        return service.create(group, new CreateRequest(cd, "IT 새 법인(예시)", biz, source, "it.admin", PW));
    }

    @Test
    void 새_법인은_세션_고객사에_속하고_기준_데이터만_복사된다() {
        CreateResult r = create(NEW, "000-00-00193", SRC);

        assertThat(jdbc.queryForObject("SELECT tenant_id FROM enter_company WHERE enter_cd = ?", String.class, NEW))
                .isEqualTo("T001");
        assertThat(r.company().bizRegNo()).isEqualTo("0000000193");
        assertThat(count("common_code_group", NEW)).isEqualTo(count("common_code_group", SRC));
        assertThat(count("common_code", NEW)).isEqualTo(count("common_code", SRC)).isPositive();
        assertThat(jdbc.queryForList("SELECT site_code FROM biz_site WHERE enter_cd = ?", String.class, NEW))
                .containsExactly("S01");
        assertThat(count("emp_no_seq", NEW)).as("사번 순번은 새로").isZero();
        assertThat(jdbc.queryForList("SELECT pattern_code FROM work_pattern WHERE enter_cd = ?", String.class, NEW))
                .containsExactly("ZZ_DAY");
        assertThat(jdbc.queryForList("SELECT holiday_name FROM holiday WHERE enter_cd = ? ORDER BY holiday_date",
                String.class, NEW)).containsExactly("IT 공휴일", "IT 창립기념일");
        assertThat(jdbc.queryForList("SELECT request_type FROM approval_template WHERE enter_cd = ?", String.class, NEW))
                .containsExactly("ZZ_IT_ROLE");
        assertThat(r.skippedTemplates()).containsExactly("ZZ_IT_EMP");
        assertThat(jdbc.queryForObject("SELECT s.approver_ref FROM approval_template_step s JOIN approval_template t"
                + " ON t.enter_cd = s.enter_cd AND t.template_id = s.template_id WHERE s.enter_cd = ?", String.class, NEW))
                .isEqualTo("HR");
        // 업무 데이터는 복사하지 않는다.
        for (String t : List.of("employee", "org_dept", "worker_rep_agreement", "approval_doc", "attendance_punch")) {
            assertThat(count(t, NEW)).as(t).isZero();
        }
        // 첫 관리자: 새 법인의 ADMIN, 비밀번호는 BCrypt 해시로만.
        assertThat(jdbc.queryForObject("SELECT role_code FROM app_user_role WHERE enter_cd = ? AND login_id = 'it.admin'",
                String.class, NEW)).isEqualTo("ADMIN");
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE enter_cd = ? AND login_id = 'it.admin'",
                String.class, NEW);
        assertThat(hash).startsWith("$2").doesNotContain(PW);
        assertThat(service.list(group)).extracting(CompanyView::enterCd).contains("C001", SRC, NEW).doesNotContain(OTHER);
    }

    @Test
    void 다른_고객사_법인은_원본으로_쓸_수_없고_목록_사용중지에서도_없다() {
        assertThatThrownBy(() -> create(NEW, "0000000193", OTHER)).isInstanceOf(AdminException.class)
                .satisfies(e -> assertThat(((AdminException) e).status().value()).isEqualTo(404));
        assertThat(count("common_code", NEW)).isZero();
        assertThatThrownBy(() -> service.setActive(group, OTHER, new ActiveRequest(false)))
                .isInstanceOf(AdminException.class)
                .satisfies(e -> assertThat(((AdminException) e).status().value()).isEqualTo(404));
        assertThat(jdbc.queryForObject("SELECT use_yn FROM enter_company WHERE enter_cd = ?", String.class, OTHER))
                .isEqualTo("Y");
        assertThat(service.setActive(group, SRC, new ActiveRequest(false)).active()).isFalse();
    }

    @Test
    void 법인_코드_중복은_409이고_복사된_행끼리도_다른_법인을_가리킬_수_없다() {
        assertThatThrownBy(() -> create(SRC, "0000000193", SRC)).isInstanceOf(AdminException.class)
                .satisfies(e -> assertThat(((AdminException) e).status().value()).isEqualTo(409));
        create(NEW, "0000000193", SRC);
        Long srcTemplate = jdbc.queryForObject("SELECT template_id FROM approval_template WHERE enter_cd = ?"
                + " AND request_type = 'ZZ_IT_ROLE'", Long.class, SRC);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO approval_template_step (enter_cd, template_id, step_no,"
                + " approver_kind, approver_ref) VALUES (?, ?, 2, 'ROLE', 'HR')", NEW, srcTemplate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 중간에_실패하면_새_법인_행까지_전부_되돌린다() {
        // 커밋된 C001 을 원본으로, 별도 트랜잭션 안에서 만든 뒤 실패시킨다.
        TransactionTemplate inner = new TransactionTemplate(tx);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> inner.executeWithoutResult(s -> {
            service.create(group, new CreateRequest("ZZIT4", "IT 실패 법인(예시)", "0000000194", "C001", "it.admin", PW));
            throw new IllegalStateException("IT 강제 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM enter_company WHERE enter_cd = 'ZZIT4'", Integer.class)).isZero();
        assertThat(count("common_code", "ZZIT4")).isZero();
        assertThat(count("app_user", "ZZIT4")).isZero();
    }
}
