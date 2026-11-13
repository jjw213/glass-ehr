package com.glassehr.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.contract.ContractDtos.ContractRequest;
import com.glassehr.contract.ContractDtos.ContractView;
import com.glassehr.contract.ContractDtos.DeliveryRequest;
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
 * Oracle 실DB에서 V012·JPA 매핑(ddl-auto: validate)·DB 제약을 검증한다. 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다.
 * 법정 기간 계산은 다루지 않는다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class ContractOracleTest {

    private static final LocalDate D0 = LocalDate.of(1903, 1, 1);
    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1");

    @Autowired
    private ContractService service;

    @Autowired
    private EmployeeService employees;

    @Autowired
    private JdbcTemplate jdbc;

    private long employee() {
        return employees.create(new EmployeeCreateRequest("S01", "예시사원IT", "F", LocalDate.of(1880, 1, 1),
                "가상시 예시구 샘플로 0", null, "시험", null, D0, null, null, null, "REGULAR", 2400, null, null, null,
                null, null, null, null), HR).empId();
    }

    private static ContractRequest req(String type, LocalDate start, LocalDate end) {
        return new ContractRequest(type, start, start, end, 2_500_000L, "MONTHLY", "기본급", "월급제", "계좌 이체", 2400,
                "09:00~18:00", null, "일요일", "연차 유급휴가", "가상시 본사", "시험", null);
    }

    @Test
    void 저장_조회_교부가_된다() {
        long emp = employee();
        ContractView v = service.create(emp, req("FIXED_TERM", D0, D0.plusMonths(6)));
        service.recordDelivery(v.contractId(), new DeliveryRequest(D0));
        assertThat(service.list(emp)).singleElement().satisfies(c -> {
            assertThat(c.wageAmount()).isEqualTo(2_500_000L);
            assertThat(c.writtenDeliveredDate()).isEqualTo(D0);
        });
        assertThat(service.undelivered()).noneMatch(r -> r.contractId() == v.contractId());
    }

    @Test
    void DB는_종료일_없는_기간제와_체결일_전_교부일을_거부한다() {
        long emp = employee();
        ContractView v = service.create(emp, req("FIXED_TERM", D0, D0.plusMonths(6)));
        assertThatThrownBy(() -> jdbc.update("UPDATE employment_contract SET end_date = NULL WHERE contract_id = ?",
                v.contractId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE employment_contract SET written_delivered_date = DATE '1902-12-31' WHERE contract_id = ?",
                v.contractId())).isInstanceOf(DataIntegrityViolationException.class);
    }
}
