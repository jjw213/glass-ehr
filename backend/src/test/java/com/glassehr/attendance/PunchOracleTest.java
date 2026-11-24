package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.attendance.PunchDtos.PunchRequest;
import com.glassehr.attendance.PunchDtos.PunchView;
import com.glassehr.attendance.PunchDtos.TodayView;
import com.glassehr.appointment.AppointmentService;
import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.employee.EmployeeService;
import com.glassehr.employee.RequestActor;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 V021·JPA 매핑(ddl-auto: validate)·DB 제약을 검증한다. 실행 조건: {@code ORACLE_IT=true}.
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

    @Autowired
    private AttendancePunchRepository punches;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AppointmentService assignments;

    @Autowired
    private EntityManager em;

    private long employee() {
        return employees.create("C001", new EmployeeCreateRequest("S01", "예시사원IT", "F", LocalDate.of(1880, 1, 1),
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

    /**
     * CI #97(PR #83) 회귀: JVM 기본 시간대가 UTC인데 서울 15시 이후에 기록하면, hibernate.jdbc.time_zone 설정 때문에
     * punched_at이 9시간 밀려 다음 날이 되어 ck_punch_work_date_same을 위반했다.
     * 테스트 JVM은 pom의 -Duser.timezone=Asia/Seoul로 돌기 때문에 여기서 기본 시간대를 UTC로 바꿔 그 조건을 만든다.
     * #84: 서울 자정(15:00Z) 바로 전·정각·후를 모두 확인한다. JVM 전체를 UTC로 돌리려면 {@code -Dtest.timezone=UTC}.
     */
    @ParameterizedTest(name = "{0} → 서울 {1}")
    @CsvSource({
            "2026-11-16T14:30:00Z,        2026-11-16T23:30:00",
            "2026-11-16T14:59:59.999999Z, 2026-11-16T23:59:59.999999",
            "2026-11-16T15:00:00Z,        2026-11-17T00:00:00",
            "2026-11-16T15:30:00Z,        2026-11-17T00:30:00"
    })
    void JVM_시간대가_UTC여도_서울_자정_전후_기록은_서울_날짜와_시각_그대로_저장된다(Instant at, LocalDateTime seoul) {
        long emp = employee();
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
            Clock clock = Clock.fixed(at, PunchDtos.SEOUL);
            PunchService fixed = new PunchService(punches, employeeRepository, assignments, clock);
            PunchView in = fixed.punch(emp, "it-emp", new PunchRequest(AttendancePunch.Type.IN, null));
            em.flush();

            Map<String, Object> row = jdbc.queryForMap("SELECT TO_CHAR(punched_at, 'YYYY-MM-DD HH24:MI:SS.FF6') AS at,"
                    + " TO_CHAR(work_date, 'YYYY-MM-DD') AS wd FROM attendance_punch WHERE punch_id = ?", in.punchId());
            assertThat(row.get("AT")).isEqualTo(seoul.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")));
            assertThat(row.get("WD")).isEqualTo(seoul.toLocalDate().toString());

            em.clear();
            assertThat(punches.findByEmpIdAndWorkDateOrderByPunchedAtAscPunchIdAsc(emp, seoul.toLocalDate()))
                    .singleElement()
                    .satisfies(p -> assertThat(p.getPunchedAt()).isEqualTo(seoul));
        } finally {
            TimeZone.setDefault(original);
        }
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
