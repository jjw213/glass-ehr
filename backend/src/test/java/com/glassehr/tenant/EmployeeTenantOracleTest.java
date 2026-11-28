package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeSummary;
import com.glassehr.employee.EmployeeDtos.RetirementRequest;
import com.glassehr.employee.EmployeeException;
import com.glassehr.employee.EmployeeService;
import com.glassehr.employee.RequestActor;
import com.glassehr.employee.Rrn;
import com.glassehr.employee.RrnAccessLog;
import com.glassehr.employee.RrnService;
import java.time.LocalDate;
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
 * V036(#123): 사원·주민등록번호의 법인 분리를 Oracle 실DB에서 검증한다.
 * "법인 A(C001) 세션으로 법인 B(ZZIT1) 사원 조회·수정 → 404/거부", 법인 안 HMAC 중복 검사, 복합 PK·FK,
 * 주민등록번호 조회 로그의 대상·주체 법인을 본다(docs/tenant-design.md 3.2·4.4·5.2).
 * 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다. 1900년대 날짜, ZZIT 코드, 명백한 가짜 번호만 쓴다.
 */
@SpringBootTest(properties = {
        "glassehr.rrn.enc-keys=1:AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
        "glassehr.rrn.current-key-version=1",
        "glassehr.rrn.hmac-key=CQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQk="})
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class EmployeeTenantOracleTest {

    private static final String A = "C001";
    private static final String B = "ZZIT1";
    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1", "C001");
    private static final Rrn R = Rrn.parse("000000-0000097");

    @Autowired
    private EmployeeService employees;

    @Autowired
    private RrnService rrns;

    @Autowired
    private JdbcTemplate jdbc;

    private long bEmp;

    @BeforeEach
    void 법인_B와_B의_사원을_넣는다() {
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order, tenant_id)"
                + " VALUES (?, 'IT 법인 가(예시)', '0000000093', 'Y', 0, 'T001')", B);
        jdbc.update("INSERT INTO biz_site (enter_cd, site_code, site_name) VALUES (?, 'S01', 'B 본사(예시)')", B);
        bEmp = insertEmployee(B, "S01-1903-901");
    }

    @Test
    void 법인_A_세션은_법인_B_사원을_목록에서_보지_못하고_조회_퇴직은_404() {
        assertThat(employees.list(A, true)).extracting(EmployeeSummary::empId).doesNotContain(bEmp);
        assertThat(employees.list(B, true)).extracting(EmployeeSummary::empId).containsExactly(bEmp);

        assertStatus(() -> employees.get(A, bEmp), HttpStatus.NOT_FOUND);
        assertStatus(() -> employees.retire(A, bEmp, new RetirementRequest(LocalDate.of(1903, 2, 1), "RESIGNATION", null)),
                HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE emp_id = ? AND retire_date IS NULL",
                Integer.class, bEmp)).isEqualTo(1);
    }

    @Test
    void 법인_A_세션은_법인_B_주민등록번호를_보지_못하고_조회_로그에_대상_주체_법인을_남긴다() {
        rrns.save(B, bEmp, R, HR);

        assertThat(rrns.masked(A, bEmp)).isEmpty();
        assertThat(rrns.masked(B, bEmp)).isPresent();
        // 조회 로그는 별도 트랜잭션(REQUIRES_NEW)으로 커밋된다. 대상 법인은 커밋된 C001 이어야 FK 대기가 없다
        // (법인 B 는 이 테스트 트랜잭션에서만 보이므로 B 세션 조회는 단위 테스트 RrnServiceTest 에서 본다).
        assertStatus(() -> rrns.reveal(A, bEmp, HR, RrnAccessLog.Purpose.VIEW), HttpStatus.NOT_FOUND);

        assertThat(jdbc.queryForList("SELECT enter_cd || '/' || accessor_enter_cd || '/' || result FROM rrn_access_log"
                + " WHERE emp_id = ? ORDER BY log_id", String.class, bEmp))
                .containsExactly(A + "/C001/NOT_FOUND");
    }

    @Test
    void 주민등록번호_중복_검사는_법인_안에서만_한다() {
        long aEmp = createInA();
        rrns.save(B, bEmp, R, HR);
        assertThat(rrns.isRegistered(A, R)).isFalse();

        // 같은 사람이 두 법인에 재직할 수 있다(전적 = 퇴직 + 다른 법인 신규 입사, docs/tenant-design.md 3.2).
        rrns.save(A, aEmp, R, HR);
        assertThat(rrns.isRegistered(A, R)).isTrue();

        long aEmp2 = createInA();
        assertStatus(() -> rrns.save(A, aEmp2, R, HR), HttpStatus.CONFLICT);
    }

    @Test
    void DB_복합_PK_FK는_다른_법인_사원을_참조하지_못하게_한다() {
        long aEmp = createInA();
        String aEmpNo = jdbc.queryForObject("SELECT emp_no FROM employee WHERE emp_id = ?", String.class, aEmp);

        // 사번은 법인마다 따로 매기므로 같은 사번이 법인 B 에도 있을 수 있다(UNIQUE (enter_cd, emp_no)).
        insertEmployee(B, aEmpNo);
        assertThatThrownBy(() -> insertEmployee(A, aEmpNo)).isInstanceOf(DataIntegrityViolationException.class);

        // 법인을 빠뜨린 사원은 DEFAULT 가 없어 거부한다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO employee (emp_no, site_code, emp_name, gender, birth_date,"
                + " address, job_description, hire_date, employment_type_code) VALUES ('S01-1903-902', 'S01',"
                + " '예시사원법인없음', 'F', DATE '1880-01-01', '가상시 예시구 샘플로 0', '시험', DATE '1903-01-01', 'REGULAR')"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 법인 A 행이 법인 B 사원을 가리킬 수 없다(로그인 계정·주민등록번호 예시).
        assertThatThrownBy(() -> jdbc.update("INSERT INTO app_user (enter_cd, login_id, emp_id, active_yn)"
                + " VALUES (?, 'zzit-cross', ?, 'Y')", A, bEmp)).isInstanceOf(DataIntegrityViolationException.class);
        // JPA flush 실패 뒤에는 영속성 컨텍스트를 더 쓰지 않도록 마지막에 둔다.
        assertThatThrownBy(() -> rrns.save(A, bEmp, R, HR)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertEmployee(String enterCd, String empNo) {
        jdbc.update("INSERT INTO employee (enter_cd, emp_no, site_code, emp_name, gender, birth_date, address,"
                + " job_description, hire_date, employment_type_code) VALUES (?, ?, 'S01', '예시사원법인나', 'M',"
                + " DATE '1880-01-01', '가상시 예시구 샘플로 0', '시험', DATE '1903-01-01', 'REGULAR')", enterCd, empNo);
        return jdbc.queryForObject("SELECT emp_id FROM employee WHERE enter_cd = ? AND emp_no = ?", Long.class,
                enterCd, empNo);
    }

    private long createInA() {
        return employees.create(A, new EmployeeCreateRequest("S01", "예시사원법인가", "F", LocalDate.of(1880, 1, 1),
                "가상시 예시구 샘플로 0", null, "시험", null, LocalDate.of(1903, 1, 1), null, null, null, "REGULAR", 2400,
                null, null, null, null, "STAFF", null, null), HR).empId();
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(EmployeeException.class,
                e -> assertThat(e.status()).isEqualTo(status));
    }
}
