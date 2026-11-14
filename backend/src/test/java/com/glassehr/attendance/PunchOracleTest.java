package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.attendance.PunchDtos.PunchRequest;
import com.glassehr.attendance.PunchDtos.PunchView;
import com.glassehr.attendance.PunchDtos.TodayView;
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
 * Oracle 실DB에서 V019·JPA 매핑(ddl-auto: validate)·DB 제약을 검증한다. 실행 조건: {@code ORACLE_IT=true}.
 * 사원은 가짜 값이며 각 테스트는 롤백한다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class PunchOracleTest {

    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1");

    @Autowired
    private PunchService service;

    @Autowired
    private EmployeeService employees;

    @Autowired
    private JdbcTemplate jdbc;

    private long employee() {
        return employees.create(new EmployeeCreateRequest("S01", "예시사원IT", "F", LocalDate.of(1880, 1, 1),
                "가상시 예시구 샘플로 0", null, "시험", null, LocalDate.of(1903, 1, 1), null, null, null, "REGULAR", 2400,
                null, null, null, null, "STAFF", null, null), HR).empId();
    }

    @Test
    void 출근_퇴근을_기록하고_오늘_요약을_읽는다() {
        long emp = employee();
        PunchView in = service.punch(emp, "it-emp", new PunchRequest(AttendancePunch.Type.IN, null));
        PunchView dup = service.punch(emp, "it-emp", new PunchRequest(AttendancePunch.Type.IN, null));
        assertThat(in.check()).isNull();
        assertThat(dup.check()).isEqualTo(AttendancePunch.Check.DUPLICATE_IN);
        TodayView t = service.today(emp);
        assertThat(t.punches()).hasSize(2);
        assertThat(t.firstInAt()).isEqualTo(in.punchedAt());
        assertThat(t.working()).isTrue();
    }

    @Test
    void DB는_근무일과_기록_날짜가_다르거나_판정과_종류가_맞지_않으면_거부한다() {
        long emp = employee();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO attendance_punch (emp_id, punch_type, punched_at, work_date,"
                + " source, created_by) VALUES (?, 'IN', TIMESTAMP '2099-03-02 00:30:00', DATE '2099-03-01', 'WEB', 'it')",
                emp)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO attendance_punch (emp_id, punch_type, punched_at, work_date,"
                + " punch_check, source, created_by) VALUES (?, 'IN', TIMESTAMP '2099-03-02 09:00:00', DATE '2099-03-02',"
                + " 'OUT_WITHOUT_IN', 'WEB', 'it')", emp)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
