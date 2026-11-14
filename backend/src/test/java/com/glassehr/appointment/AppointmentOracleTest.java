package com.glassehr.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.appointment.AppointmentDtos.AppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeService;
import com.glassehr.employee.RequestActor;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 V014·JPA 매핑(ddl-auto: validate)·시퀀스 채번·DB 제약을 검증한다. 실행 조건: {@code ORACLE_IT=true}.
 * 각 테스트는 롤백한다(시퀀스 번호는 되돌아가지 않는다).
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class AppointmentOracleTest {

    private static final LocalDate D0 = LocalDate.of(1903, 1, 1);
    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1");

    @Autowired
    private AppointmentService service;

    @Autowired
    private EmployeeService employees;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.glassehr.org.OrgDeptService orgs;

    private long employee() {
        return employees.create(new EmployeeCreateRequest("S01", "예시사원IT", "F", LocalDate.of(1880, 1, 1),
                "가상시 예시구 샘플로 0", null, "시험", null, D0, null, null, null, "REGULAR", 2400, null, null, null,
                null, "STAFF", null, null), HR).empId();
    }

    @Test
    void 휴직_등록_확정_조회가_된다() {
        long emp = employee();
        AppointmentView v = service.create(emp, new AppointmentRequest("LEAVE", D0.plusDays(10), null, null, null,
                "육아휴직 신청", D0.plusDays(10), D0.plusMonths(6)));
        assertThat(v.apptNo()).matches(AppointmentNoGenerator.APPT_NO_PATTERN);
        service.confirm(v.apptId());
        assertThat(service.listByEmployee(emp)).singleElement().satisfies(a -> {
            assertThat(a.confirmed()).isTrue();
            assertThat(a.confirmedAt()).isNotNull();
            assertThat(a.after().status()).isEqualTo(EmpStatus.ON_LEAVE);
            assertThat(a.leaveEndDate()).isEqualTo(D0.plusMonths(6));
        });
    }

    @Test
    void 미래_전입이_확정된_빈_부서는_폐지할_수_없다() {
        long emp = employee();
        long dept = orgs.create(new com.glassehr.org.OrgDtos.DeptCreateRequest(
                "IT_FUTURE_APPT", "예시 미래 전입 부서", null, null, 0, D0, "IT 가상 부서")).deptId();
        LocalDate future = LocalDate.of(2199, 1, 1);
        long appt = service.create(emp, new AppointmentRequest("TRANSFER", future, dept, null, null,
                "IT 미래 전입", null, null)).apptId();
        service.confirm(appt);
        assertThatThrownBy(() -> orgs.close(dept, new com.glassehr.org.OrgDtos.DeptCloseRequest(
                future.minusDays(1), "IT 폐지 시도")))
                .isInstanceOf(com.glassehr.org.OrgException.class).hasMessageContaining("소속 재직자 1명");
    }

    @Test
    void DB는_빈_사유와_휴직일_없는_휴직과_확정일시_없는_확정을_거부한다() {
        long emp = employee();
        AppointmentView v = service.create(emp, new AppointmentRequest("LEAVE", D0.plusDays(1), null, null, null,
                "사유", D0.plusDays(1), D0.plusDays(2)));
        assertThatThrownBy(() -> jdbc.update("UPDATE hr_appointment SET reason = ' ' WHERE appt_id = ?", v.apptId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE hr_appointment SET leave_end_date = NULL WHERE appt_id = ?",
                v.apptId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE hr_appointment SET confirmed_yn = 'Y' WHERE appt_id = ?",
                v.apptId())).isInstanceOf(DataIntegrityViolationException.class);
    }
}
