package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeService;
import com.glassehr.employee.RequestActor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 V022·MyBatis 매퍼(DailyWorkSummaryMapper)·DB 제약을 검증한다. 실행 조건: {@code ORACLE_IT=true}.
 * 사원·날짜는 가짜 값이며 각 테스트는 롤백한다. 값은 저장 경로 확인용 입력(법령 정답 아님).
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class WorkHoursOracleTest {

    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1");
    private static final LocalDate D = LocalDate.of(2099, 3, 2);

    @Autowired
    private DailyWorkSummaryMapper mapper;

    @Autowired
    private EmployeeService employees;

    private long employee() {
        return employees.create(new EmployeeCreateRequest("S01", "예시사원IT", "F", LocalDate.of(1880, 1, 1),
                "가상시 예시구 샘플로 0", null, "시험", null, LocalDate.of(1903, 1, 1), null, null, null, "REGULAR", 2400,
                null, null, null, null, "STAFF", null, null), HR).empId();
    }

    private static DailyWorkSummaryRow row(long emp, LocalDate d, int worked, int regular, int overtime) {
        return new DailyWorkSummaryRow(emp, d, "WORKDAY", null, 480, "HALF_DAY_AM", 240,
                d.atTime(22, 0), d.plusDays(1).atTime(7, 0), 60, worked, regular, overtime, 420, 0, 0,
                new BigDecimal("11"), new BigDecimal("22.5"), new BigDecimal("33"), new BigDecimal("44"),
                "MISSING_OUT", "it-hr", LocalDateTime.of(2099, 3, 10, 10, 0, 0, 123_456_000));
    }

    @Test
    void 저장한_행을_그대로_읽고_기간으로_지운다() {
        long emp = employee();
        DailyWorkSummaryRow a = row(emp, D, 480, 480, 0);
        DailyWorkSummaryRow b = row(emp, D.plusDays(1), 540, 480, 60);
        mapper.insert(a);
        mapper.insert(b);
        assertThat(mapper.findRange(emp, D, D.plusDays(6)))
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("overtimeRate", "nightRate", "holidayRate",
                        "holidayExcessRate")
                .containsExactly(a, b);
        assertThat(mapper.findRange(emp, D, D).get(0).nightRate()).isEqualByComparingTo("22.5");
        assertThat(mapper.deleteRange(emp, D, D)).isEqualTo(1);
        assertThat(mapper.findRange(emp, D, D.plusDays(6))).hasSize(1);
    }

    @Test
    void 분류_합이_실근로와_다르거나_같은_날이_두_번이면_거부() {
        long emp = employee();
        assertThatThrownBy(() -> mapper.insert(row(emp, D, 500, 480, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
        mapper.insert(row(emp, D, 480, 480, 0));
        assertThatThrownBy(() -> mapper.insert(row(emp, D, 480, 480, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
