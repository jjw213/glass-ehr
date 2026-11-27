package com.glassehr.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeService;
import com.glassehr.employee.RequestActor;
import com.glassehr.retirement.RetirementDtos.CertificateItem;
import com.glassehr.retirement.RetirementDtos.CertificateRequest;
import com.glassehr.retirement.RetirementDtos.CertificateView;
import java.time.LocalDate;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 V022·JPA 매핑(ddl-auto: validate)·DB 제약을 검증한다. 실행 조건: {@code ORACLE_IT=true}.
 * 사원은 가짜 값이며 각 테스트는 롤백한다. legal_rate 값은 넣지 않는다(퇴직 처리 등록 경로는 단위 테스트에서 본다).
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class RetirementOracleTest {

    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1", "C001");

    @Autowired
    private RetirementService service;

    @Autowired
    private EmployeeService employees;

    @Autowired
    private JdbcTemplate jdbc;

    private long employee() {
        return employees.create("C001", new EmployeeCreateRequest("S01", "예시사원IT", "F", LocalDate.of(1880, 1, 1),
                "가상시 예시구 샘플로 0", null, "시험 업무", null, LocalDate.of(1903, 1, 1), null, null, null, "REGULAR",
                2400, null, null, null, null, "STAFF", null, null), HR).empId();
    }

    @Test
    void 사용증명서는_요구한_항목만_저장한다() {
        long emp = employee();
        CertificateView v = service.issueCertificate("C001", emp, new CertificateRequest(LocalDate.of(1903, 6, 1),
                EnumSet.of(CertificateItem.USE_PERIOD, CertificateItem.JOB_TYPE), "예시"), "it-hr");
        assertThat(service.certificate("C001", v.certId())).isEqualTo(v);
        assertThat(v.jobType()).isEqualTo("시험 업무");
        assertThat(v.positionText()).isNull();
        assertThat(jdbc.queryForObject("SELECT requested_items FROM employment_certificate WHERE cert_id = ?",
                String.class, v.certId())).isEqualTo("USE_PERIOD,JOB_TYPE");
        assertThat(service.certificates("C001", emp)).hasSize(1);
    }

    @Test
    void DB는_예고일이나_서면통지일_없는_해고를_거부한다() {
        long emp = employee();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO retirement_case (emp_id, retire_reason_code, retire_date,"
                + " dismissal_notice_date, notice_check, created_by) VALUES (?, 'DISMISSAL', DATE '1903-06-30',"
                + " DATE '1903-06-01', 'UNKNOWN', 'it')", emp)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO retirement_case (emp_id, retire_reason_code, retire_date,"
                + " notice_check, created_by) VALUES (?, 'VOLUNTARY', DATE '1903-06-30', 'OK', 'it')", emp))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO retirement_case (emp_id, retire_reason_code, retire_date, dismissal_notice_date,"
                + " written_notice_date, notice_check, created_by) VALUES (?, 'DISMISSAL', DATE '1903-06-30',"
                + " DATE '1903-06-01', DATE '1903-06-01', 'UNKNOWN', 'it')", emp);
        assertThat(service.get("C001", emp).notice().check()).isEqualTo(DismissalNoticeChecker.NoticeCheck.UNKNOWN);
    }
}
