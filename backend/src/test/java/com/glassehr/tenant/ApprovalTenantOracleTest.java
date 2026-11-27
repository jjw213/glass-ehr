package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.approval.ApprovalException;
import com.glassehr.approval.ApprovalService;
import com.glassehr.approval.ApprovalDtos.DocSummary;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.familyleave.FamilyLeaveException;
import com.glassehr.familyleave.FamilyLeaveService;
import com.glassehr.overtime.OvertimeDtos.AuthRequest;
import com.glassehr.overtime.OvertimeService;
import com.glassehr.overtime.SpecialOvertimeAuth;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V046(#127): 결재·연장근로·특별연장근로 인가·가족돌봄휴직 신청의 법인 분리를 Oracle 실DB에서 검증한다.
 * "법인 A(C001) 세션으로 법인 B(ZZIT4) 데이터 조회·수정 → 404/거부"와 복합 PK·FK 를 본다(docs/tenant-design.md 3.2).
 * 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다. 1900년대 날짜, ZZIT 코드만 쓴다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class ApprovalTenantOracleTest {

    private static final String A = "C001";
    private static final String B = "ZZIT4";
    private static final String TYPE = "ZZIT_TENANT";

    @Autowired
    private ApprovalService approvals;

    @Autowired
    private OvertimeService overtime;

    @Autowired
    private FamilyLeaveService familyLeave;

    @Autowired
    private JdbcTemplate jdbc;

    private long aEmp;
    private long bEmp;
    private long bTemplate;
    private long bDoc;
    private long bStep;
    private long bOvertime;
    private long bAuth;
    private long bLeave;

    @BeforeEach
    void 법인_B와_B의_결재_신청을_넣는다() {
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)"
                + " VALUES (?, 'IT 법인 라(예시)', '0000000094', 'Y', 0)", B);
        jdbc.update("INSERT INTO biz_site (enter_cd, site_code, site_name) VALUES (?, 'S01', 'B 본사(예시)')", B);
        aEmp = insertEmployee(A, "S01-1903-941");
        bEmp = insertEmployee(B, "S01-1903-942");

        jdbc.update("INSERT INTO approval_template (enter_cd, request_type, valid_from, created_by)"
                + " VALUES (?, ?, DATE '1904-01-01', 'it')", B, TYPE);
        bTemplate = jdbc.queryForObject("SELECT template_id FROM approval_template WHERE enter_cd = ?"
                + " AND request_type = ?", Long.class, B, TYPE);
        jdbc.update("INSERT INTO approval_template_step (enter_cd, template_id, step_no, approver_kind, approver_ref)"
                + " VALUES (?, ?, 1, 'EMPLOYEE', ?)", B, bTemplate, String.valueOf(bEmp));
        jdbc.update("INSERT INTO approval_doc (enter_cd, request_type, request_ref, title, requester_emp_id,"
                + " template_id, status, current_step_no, submitted_at) VALUES (?, ?, 1, 'B 문서', ?, ?,"
                + " 'IN_PROGRESS', 1, TIMESTAMP '1904-01-05 09:00:00')", B, TYPE, bEmp, bTemplate);
        bDoc = jdbc.queryForObject("SELECT approval_id FROM approval_doc WHERE enter_cd = ? AND request_type = ?",
                Long.class, B, TYPE);
        jdbc.update("INSERT INTO approval_doc_step (enter_cd, approval_id, step_no, approver_kind, approver_ref,"
                + " step_status) VALUES (?, ?, 1, 'EMPLOYEE', ?, 'WAITING')", B, bDoc, String.valueOf(bEmp));
        bStep = jdbc.queryForObject("SELECT step_id FROM approval_doc_step WHERE enter_cd = ? AND approval_id = ?",
                Long.class, B, bDoc);
        jdbc.update("INSERT INTO approval_doc_step_approver (enter_cd, step_id, emp_id) VALUES (?, ?, ?)",
                B, bStep, bEmp);
        jdbc.update("INSERT INTO approval_action_log (enter_cd, approval_id, action, actor_emp_id, acted_at)"
                + " VALUES (?, ?, 'SUBMIT', ?, TIMESTAMP '1904-01-05 09:00:00')", B, bDoc, bEmp);

        jdbc.update("INSERT INTO overtime_request (enter_cd, emp_id, work_kind, work_date, start_at, end_at,"
                + " break_minutes, planned_minutes, reason, status, requested_at) VALUES (?, ?, 'OVERTIME',"
                + " DATE '1904-01-05', TIMESTAMP '1904-01-05 18:00:00', TIMESTAMP '1904-01-05 20:00:00', 0, 120,"
                + " 'B 연장', 'IN_PROGRESS', TIMESTAMP '1904-01-04 09:00:00')", B, bEmp);
        bOvertime = jdbc.queryForObject("SELECT request_id FROM overtime_request WHERE enter_cd = ? AND emp_id = ?",
                Long.class, B, bEmp);

        jdbc.update("INSERT INTO special_overtime_auth (enter_cd, auth_kind, auth_no, labor_office, authorized_on,"
                + " period_from, period_to, reason, worker_consent, created_by) VALUES (?, 'PRIOR', 'ZZIT-AUTH-1',"
                + " '가상지청', DATE '1904-01-01', DATE '1904-01-02', DATE '1904-01-10', 'B 인가', 'Y', 'it')", B);
        bAuth = jdbc.queryForObject("SELECT auth_id FROM special_overtime_auth WHERE enter_cd = ? AND auth_no = ?",
                Long.class, B, "ZZIT-AUTH-1");
        jdbc.update("INSERT INTO special_overtime_auth_emp (enter_cd, auth_id, emp_id) VALUES (?, ?, ?)",
                B, bAuth, bEmp);

        jdbc.update("INSERT INTO family_leave_request (enter_cd, emp_id, leave_kind, family_relation, start_date,"
                + " end_date, reason, status, requested_by, requested_at) VALUES (?, ?, 'FAMILY_CARE_LEAVE', '부모',"
                + " DATE '1904-02-01', DATE '1904-02-10', 'B 돌봄', 'REQUESTED', 'it',"
                + " TIMESTAMP '1904-01-05 09:00:00')", B, bEmp);
        bLeave = jdbc.queryForObject("SELECT req_id FROM family_leave_request WHERE enter_cd = ? AND emp_id = ?",
                Long.class, B, bEmp);
    }

    @Test
    void 법인_A_세션은_법인_B_결재_문서를_보거나_처리하지_못하고_결재함에도_나오지_않는다() {
        AuthUser hrA = user(A, aEmp);
        assertApproval(() -> approvals.get(bDoc, hrA), HttpStatus.NOT_FOUND);
        assertApproval(() -> approvals.approve(bDoc, hrA, "섞임"), HttpStatus.NOT_FOUND);
        assertApproval(() -> approvals.reject(bDoc, hrA, "섞임"), HttpStatus.NOT_FOUND);
        assertApproval(() -> approvals.withdraw(bDoc, hrA), HttpStatus.NOT_FOUND);

        // 결재함·내 신청은 현재 법인 문서만. 세션 법인이 A 이면 B 사원 번호여도 B 문서가 나오지 않는다.
        AuthUser spoofed = user(A, bEmp);
        assertThat(approvals.inbox(spoofed)).extracting(DocSummary::approvalId).doesNotContain(bDoc);
        assertThat(approvals.mine(spoofed)).extracting(DocSummary::approvalId).doesNotContain(bDoc);
        assertThat(approvals.inbox(user(B, bEmp))).extracting(DocSummary::approvalId).contains(bDoc);
        assertThat(approvals.findByRequest(A, TYPE, 1)).isNull();

        assertThat(jdbc.queryForObject("SELECT status FROM approval_doc WHERE approval_id = ?", String.class, bDoc))
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void 법인_A_세션은_법인_B_연장근로_신청과_특별연장근로_인가를_보지_못하고_B_사원을_대상으로_넣지_못한다() {
        AuthUser spoofed = user(A, bEmp);
        assertThat(overtime.mine(spoofed)).extracting(r -> r.requestId()).doesNotContain(bOvertime);
        assertThat(overtime.auths(user(A, aEmp))).extracting(a -> a.authId()).doesNotContain(bAuth);

        AuthRequest req = new AuthRequest(SpecialOvertimeAuth.Kind.PRIOR, "ZZIT-AUTH-2", "가상지청",
                LocalDate.of(1904, 1, 1), LocalDate.of(1904, 1, 2), LocalDate.of(1904, 1, 10), "섞임",
                Set.of(bEmp), true);
        assertApproval(() -> overtime.createAuth(req, user(A, aEmp)), HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM special_overtime_auth WHERE auth_no = 'ZZIT-AUTH-2'",
                Integer.class)).isZero();
    }

    @Test
    void 법인_A_세션은_법인_B_가족돌봄휴직_신청을_조회_승인_반려_취소하지_못한다() {
        assertFamily(() -> familyLeave.get(A, bLeave), HttpStatus.NOT_FOUND);
        assertFamily(() -> familyLeave.approve(A, bLeave, "it-a"), HttpStatus.NOT_FOUND);
        assertFamily(() -> familyLeave.reject(A, bLeave, "섞임", "it-a"), HttpStatus.NOT_FOUND);
        assertFamily(() -> familyLeave.cancel(A, bLeave, "섞임", "it-a"), HttpStatus.NOT_FOUND);
        assertFamily(() -> familyLeave.listByEmployee(A, bEmp), HttpStatus.NOT_FOUND);
        assertThat(familyLeave.search(A, null)).extracting(r -> r.reqId()).doesNotContain(bLeave);
        assertThat(familyLeave.get(B, bLeave).reqId()).isEqualTo(bLeave);
        assertThat(jdbc.queryForObject("SELECT status FROM family_leave_request WHERE req_id = ?", String.class,
                bLeave)).isEqualTo("REQUESTED");
    }

    @Test
    void DB_복합_키는_다른_법인_결재_문서와_인가를_참조하지_못하게_하고_법인을_빠뜨리면_거부한다() {
        // 법인 A 행이 법인 B 템플릿·문서·단계·인가를 가리킬 수 없다(V046 복합 FK).
        assertRejected("INSERT INTO approval_template_step (enter_cd, template_id, step_no, approver_kind,"
                + " approver_ref) VALUES (?, ?, 2, 'EMPLOYEE', '1')", A, bTemplate);
        assertRejected("INSERT INTO approval_doc_step (enter_cd, approval_id, step_no, approver_kind, approver_ref,"
                + " step_status) VALUES (?, ?, 2, 'EMPLOYEE', '1', 'WAITING')", A, bDoc);
        assertRejected("INSERT INTO approval_doc_step_approver (enter_cd, step_id, emp_id) VALUES (?, ?, ?)",
                A, bStep, aEmp);
        assertRejected("INSERT INTO approval_action_log (enter_cd, approval_id, action, actor_emp_id, acted_at)"
                + " VALUES (?, ?, 'SUBMIT', ?, TIMESTAMP '1904-01-05 09:00:00')", A, bDoc, aEmp);
        assertRejected("INSERT INTO special_overtime_auth_emp (enter_cd, auth_id, emp_id) VALUES (?, ?, ?)",
                A, bAuth, aEmp);
        // 결재선·연장근로·가족돌봄휴직에 다른 법인 사원을 넣을 수 없다(V036 사원 복합 FK).
        assertRejected("INSERT INTO approval_doc_step_approver (enter_cd, step_id, emp_id) VALUES (?, ?, ?)",
                B, bStep, aEmp);
        assertRejected("INSERT INTO overtime_request (enter_cd, emp_id, work_kind, work_date, start_at, end_at,"
                + " break_minutes, planned_minutes, reason, status, requested_at) VALUES (?, ?, 'OVERTIME',"
                + " DATE '1904-01-06', TIMESTAMP '1904-01-06 18:00:00', TIMESTAMP '1904-01-06 19:00:00', 0, 60,"
                + " '섞임', 'IN_PROGRESS', TIMESTAMP '1904-01-04 09:00:00')", A, bEmp);
        assertRejected("INSERT INTO family_leave_request (enter_cd, emp_id, leave_kind, family_relation, start_date,"
                + " end_date, reason, status, requested_by, requested_at) VALUES (?, ?, 'FAMILY_CARE_LEAVE', '부모',"
                + " DATE '1904-03-01', DATE '1904-03-02', '섞임', 'REQUESTED', 'it', TIMESTAMP '1904-01-05 09:00:00')",
                A, bEmp);
        // DEFAULT 를 지웠으므로 법인을 빠뜨린 행은 거부한다.
        assertRejected("INSERT INTO special_overtime_auth (auth_kind, auth_no, labor_office, authorized_on,"
                + " period_from, period_to, reason, worker_consent, created_by) VALUES ('PRIOR', 'ZZIT-AUTH-3',"
                + " '가상지청', DATE '1904-01-01', DATE '1904-01-02', DATE '1904-01-10', '누락', 'Y', 'it')");
        // 법인이 다르면 같은 신청 유형·인가 번호를 써도 된다(UNIQUE 에 법인).
        jdbc.update("INSERT INTO approval_template (enter_cd, request_type, valid_from, created_by)"
                + " VALUES (?, ?, DATE '1904-01-01', 'it')", A, TYPE);
        jdbc.update("INSERT INTO special_overtime_auth (enter_cd, auth_kind, auth_no, labor_office, authorized_on,"
                + " period_from, period_to, reason, worker_consent, created_by) VALUES (?, 'PRIOR', 'ZZIT-AUTH-1',"
                + " '가상지청', DATE '1904-01-01', DATE '1904-01-02', DATE '1904-01-10', 'A 인가', 'Y', 'it')", A);
        // PK 에 법인이 들어갔다.
        for (String pk : List.of("PK_APPROVAL_TEMPLATE", "PK_APPROVAL_TEMPLATE_STEP", "PK_APPROVAL_DOC",
                "PK_APPROVAL_DOC_STEP", "PK_APPROVAL_DSTEP_APPROVER", "PK_APPROVAL_ACTION_LOG",
                "PK_SPECIAL_OVERTIME_AUTH", "PK_SPECIAL_OVERTIME_AUTH_EMP", "PK_OVERTIME_REQUEST",
                "PK_FAMILY_LEAVE_REQUEST")) {
            assertThat(jdbc.queryForObject("SELECT column_name FROM user_cons_columns WHERE constraint_name = ?"
                    + " AND position = 1", String.class, pk)).as(pk).isEqualTo("ENTER_CD");
        }
    }

    private void assertRejected(String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertEmployee(String enterCd, String empNo) {
        jdbc.update("INSERT INTO employee (enter_cd, emp_no, site_code, emp_name, gender, birth_date, address,"
                + " job_description, hire_date, employment_type_code) VALUES (?, ?, 'S01', '예시사원결재', 'M',"
                + " DATE '1880-01-01', '가상시 예시구 샘플로 0', '시험', DATE '1903-01-01', 'REGULAR')", enterCd, empNo);
        return jdbc.queryForObject("SELECT emp_id FROM employee WHERE enter_cd = ? AND emp_no = ?", Long.class,
                enterCd, empNo);
    }

    private static AuthUser user(String enterCd, long empId) {
        return new AuthUser(enterCd, enterCd, "it-hr", empId, Set.of("HR"),
                Set.of(Permission.EMP_READ_ALL, Permission.EMP_WRITE), Set.of(), "127.0.0.1");
    }

    private static void assertApproval(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApprovalException.class,
                e -> assertThat(e.status()).isEqualTo(status));
    }

    private static void assertFamily(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(FamilyLeaveException.class,
                e -> assertThat(e.status()).isEqualTo(status));
    }
}
