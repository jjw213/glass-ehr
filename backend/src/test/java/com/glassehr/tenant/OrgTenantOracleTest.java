package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.auth.DeptScope;
import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeService;
import com.glassehr.employee.RequestActor;
import com.glassehr.org.OrgDeptService;
import com.glassehr.org.OrgDtos.DeptChangeRequest;
import com.glassehr.org.OrgDtos.DeptCloseRequest;
import com.glassehr.org.OrgDtos.DeptCreateRequest;
import com.glassehr.org.OrgDtos.DeptNode;
import com.glassehr.org.OrgDtos.OrgTree;
import com.glassehr.org.OrgException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 * V035(#122): 부서·조직 이력의 법인 분리를 Oracle 실DB에서 검증한다.
 * "법인 A(C001) 세션으로 법인 B(ZZIT1) 부서 조회·수정 → 404/거부"와 복합 PK·FK 를 본다(docs/tenant-design.md 5.2).
 * 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다. 1900년대 날짜와 ZZIT 코드만 쓴다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class OrgTenantOracleTest {

    private static final String A = "C001";
    private static final String B = "ZZIT1";
    private static final LocalDate D0 = LocalDate.of(1902, 1, 1);
    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1");

    @Autowired
    private OrgDeptService org;

    @Autowired
    private EmployeeService employees;

    @Autowired
    private JdbcTemplate jdbc;

    private long bDept;

    @BeforeEach
    void 법인_B와_B의_부서를_넣는다() {
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)"
                + " VALUES (?, 'IT 법인 가(예시)', '0000000092', 'Y', 0)", B);
        // 같은 부서코드가 법인 A 에도 있을 수 있다(UNIQUE (enter_cd, dept_code)).
        jdbc.update("INSERT INTO org_dept (enter_cd, dept_code, valid_from) VALUES (?, 'ZZIT_T1', DATE '1902-01-01')", B);
        bDept = jdbc.queryForObject("SELECT dept_id FROM org_dept WHERE enter_cd = ? AND dept_code = 'ZZIT_T1'",
                Long.class, B);
        jdbc.update("INSERT INTO org_dept_hist (enter_cd, dept_id, valid_from, dept_name, change_type)"
                + " VALUES (?, ?, DATE '1902-01-01', 'B 부서(예시)', 'CREATE')", B, bDept);
    }

    @Test
    void 법인_A_세션은_법인_B_부서를_트리에서_보지_못하고_변경_폐지는_404() {
        assertThat(codes(org.tree(A, D0))).doesNotContain("ZZIT_T1");
        assertThat(codes(org.tree(B, D0))).containsExactly("ZZIT_T1");

        assertStatus(() -> org.change(A, bDept, new DeptChangeRequest("바꿈", null, null, 0, D0.plusDays(1), null)),
                HttpStatus.NOT_FOUND);
        assertStatus(() -> org.close(A, bDept, new DeptCloseRequest(D0.plusDays(1), null)), HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM org_dept_hist WHERE dept_id = ?", Integer.class, bDept))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM org_dept WHERE dept_id = ? AND valid_to IS NULL",
                Integer.class, bDept)).isEqualTo(1);
    }

    @Test
    void 법인_A는_법인_B_부서를_상위부서로_둘_수_없고_같은_부서코드는_따로_만들_수_있다() {
        assertStatus(() -> org.create(A, new DeptCreateRequest("ZZIT_T2", "하위", bDept, null, 0, D0, null)),
                HttpStatus.BAD_REQUEST);
        long a = org.create(A, new DeptCreateRequest("ZZIT_T1", "A 부서(예시)", null, null, 0, D0, null)).deptId();
        assertStatus(() -> org.change(A, a, new DeptChangeRequest("A 부서(예시)", bDept, null, 0, D0.plusDays(1), null)),
                HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM org_dept WHERE dept_code = 'ZZIT_T1'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void 부서장_범위는_현재_법인_조직도에서만_계산한다() {
        long head = employee();
        long a = org.create(A, new DeptCreateRequest("ZZIT_T3", "A 부서(예시)", null, head, 0, D0, null)).deptId();

        assertThat(DeptScope.managedBy(org.tree(A, D0), head)).containsExactly(a);
        assertThat(DeptScope.managedBy(org.tree(B, D0), head)).isEmpty();
    }

    @Test
    void DB_복합_FK는_다른_법인의_부서_상위부서_부서장을_참조하지_못하게_한다() {
        long a = org.create(A, new DeptCreateRequest("ZZIT_T4", "A 부서(예시)", null, null, 0, D0, null)).deptId();
        long emp = employee();
        // 법인 A 이력 행이 법인 B 부서를 가리킬 수 없다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_dept_hist (enter_cd, dept_id, valid_from, dept_name, change_type)"
                + " VALUES (?, ?, DATE '1902-02-01', '섞임', 'UPDATE')", A, bDept))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 법인 B 부서의 상위부서로 법인 A 부서를 둘 수 없다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_dept_hist"
                + " (enter_cd, dept_id, valid_from, dept_name, parent_dept_id, change_type)"
                + " VALUES (?, ?, DATE '1902-02-01', '섞임', ?, 'MOVE')", B, bDept, a))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 법인 B 부서의 부서장으로 법인 A 사원을 둘 수 없다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_dept_hist"
                + " (enter_cd, dept_id, valid_from, dept_name, head_emp_id, change_type)"
                + " VALUES (?, ?, DATE '1902-02-01', '섞임', ?, 'UPDATE')", B, bDept, emp))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 법인 A 사원의 현재 부서·발령 부서로 법인 B 부서를 둘 수 없다.
        assertThatThrownBy(() -> jdbc.update("UPDATE employee SET current_dept_id = ? WHERE emp_id = ?", bDept, emp))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 법인을 빠뜨린 부서는 DEFAULT 가 없어 거부한다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_dept (dept_code, valid_from)"
                + " VALUES ('ZZIT_T5', DATE '1902-01-01')")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private long employee() {
        return employees.create(A, new EmployeeCreateRequest("S01", "예시사원조직가", "F", LocalDate.of(1880, 1, 1),
                "가상시 예시구 샘플로 0", null, "시험", null, LocalDate.of(1902, 1, 1), null, null, null, "REGULAR", 2400,
                null, null, null, null, "STAFF", null, null), HR).empId();
    }

    private static List<String> codes(OrgTree t) {
        List<String> out = new ArrayList<>();
        collect(t.roots(), out);
        return out;
    }

    private static void collect(List<DeptNode> nodes, List<String> out) {
        for (DeptNode n : nodes) {
            out.add(n.deptCode());
            collect(n.children(), out);
        }
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(OrgException.class, e -> assertThat(e.status()).isEqualTo(status));
    }
}
