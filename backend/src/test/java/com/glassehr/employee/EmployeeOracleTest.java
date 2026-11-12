package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeView;
import com.glassehr.org.OrgDeptService;
import com.glassehr.org.OrgDtos.DeptCloseRequest;
import com.glassehr.org.OrgDtos.DeptCreateRequest;
import com.glassehr.org.OrgException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 V009·JPA 매핑(ddl-auto: validate)·채번·암호문 저장·조회 로그·부서 폐지 재직자 확인을 검증한다.
 *
 * <p>실행 조건: {@code ORACLE_IT=true}와 DB_URL/DB_USER/DB_PASSWORD. 각 테스트는 롤백한다
 * (조회 로그는 별도 트랜잭션으로 커밋되므로 1900년대 가짜 사원 id 로만 남는다).
 * 암호화 키는 아래 테스트 전용 고정값(모두 같은 바이트, 운영 키 아님)이다.
 */
@SpringBootTest(properties = {
        "glassehr.rrn.enc-keys=1:AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
        "glassehr.rrn.current-key-version=1",
        "glassehr.rrn.hmac-key=CQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQk="})
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class EmployeeOracleTest {

    private static final LocalDate D0 = LocalDate.of(1902, 1, 1);
    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1");

    @Autowired
    private EmployeeService service;

    @Autowired
    private RrnService rrns;

    @Autowired
    private OrgDeptService org;

    @Autowired
    private JdbcTemplate jdbc;

    private EmployeeCreateRequest req(Long deptId, Rrn rrn) {
        return new EmployeeCreateRequest("S01", "예시사원IT", "M", LocalDate.of(1880, 1, 1), "가상시 예시구 샘플로 0",
                null, "시험", null, D0, null, null, null, "REGULAR", 2400, null, null, null, deptId, "STAFF", "MEMBER",
                rrn);
    }

    @Test
    void 등록하면_사업장_연도별_순번이_매겨지고_주민등록번호는_암호문으로만_저장된다() {
        EmployeeView a = service.create(req(null, Rrn.parse("000000-0000011")), HR);
        EmployeeView b = service.create(req(null, null), HR);
        assertThat(a.empNo()).startsWith("S01-1902-");
        int sa = Integer.parseInt(a.empNo().substring(9));
        assertThat(b.empNo()).isEqualTo(EmpNoGenerator.format("S01", 1902, sa + 1));
        assertThat(a.rrnMasked()).isEqualTo("000000-*******");

        Map<String, Object> row = jdbc.queryForMap("SELECT rrn_enc, rrn_masked, rrn_hash FROM emp_rrn WHERE emp_id = ?",
                a.empId());
        assertThat(new String((byte[]) row.get("RRN_ENC"), StandardCharsets.ISO_8859_1)).doesNotContain("0000011");
        assertThat(row.get("RRN_MASKED")).isEqualTo("000000-*******");
        assertThat(rrns.reveal(a.empId(), HR, RrnAccessLog.Purpose.VIEW)).isEqualTo(Rrn.parse("000000-0000011"));

        List<String> results = jdbc.queryForList(
                "SELECT result FROM rrn_access_log WHERE emp_id = ? ORDER BY log_id", String.class, a.empId());
        assertThat(results).endsWith("SUCCESS");
    }

    @Test
    void 같은_주민등록번호는_DB도_거부한다() {
        EmployeeView a = service.create(req(null, Rrn.parse("000000-0000012")), HR);
        EmployeeView b = service.create(req(null, null), HR);
        byte[] hash = jdbc.queryForObject("SELECT rrn_hash FROM emp_rrn WHERE emp_id = ?", byte[].class, a.empId());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO emp_rrn (emp_id, rrn_enc, rrn_masked, rrn_hash, key_version, updated_by)"
                + " VALUES (?, HEXTORAW('00'), '000000-*******', ?, 1, 'it')", b.empId(), hash))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void DB는_퇴직일_없는_보존기한과_형식이_틀린_사번을_거부한다() {
        EmployeeView a = service.create(req(null, null), HR);
        assertThatThrownBy(() -> jdbc.update("UPDATE employee SET record_retention_until = DATE '1990-01-01' WHERE emp_id = ?",
                a.empId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE employee SET emp_no = 'S01-1902-1' WHERE emp_id = ?", a.empId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 재직자가_있는_부서는_폐지할_수_없다() {
        long dept = org.create(new DeptCreateRequest("ZZIT_EMP", "시험부서", null, null, 0, D0, null)).deptId();
        service.create(req(dept, null), HR);
        assertThatThrownBy(() -> org.close(dept, new DeptCloseRequest(D0.plusMonths(1), null)))
                .isInstanceOf(OrgException.class);
    }
}
