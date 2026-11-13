package com.glassehr.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.code.CodeGroups;
import com.glassehr.code.CommonCode;
import com.glassehr.code.CommonCodeId;
import com.glassehr.code.CommonCodeRepository;
import com.glassehr.contract.ContractDtos.AlertRow;
import com.glassehr.contract.ContractDtos.AlertType;
import com.glassehr.contract.ContractDtos.ContractRequest;
import com.glassehr.contract.ContractDtos.ContractView;
import com.glassehr.contract.ContractDtos.DeliveryRequest;
import com.glassehr.contract.ContractDtos.UndeliveredRow;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 근로계약 등록·교부·알림 규칙. 저장소는 메모리 가짜.
 * 기간제 사용기간 상한의 마지막 날은 계산기를 가짜로 바꿔(시작일 + 고정 일수) 묶음·알림 규칙만 본다. 법정 만료일 정답은 다루지 않는다.
 */
class ContractServiceTest {

    /** 오늘(서울) = 2026-11-12. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final LocalDate HIRE = LocalDate.of(2024, 1, 2);

    private final Map<Long, EmploymentContract> store = new HashMap<>();
    private final Map<Long, Employee> emps = new HashMap<>();
    private final Map<CommonCodeId, CommonCode> codeStore = new HashMap<>();
    private long nextId = 1;

    private FixedTermUsePeriodCalculator useLimit;
    private EmploymentContractRepository contracts;
    private EmployeeRepository employees;
    private ContractService service;

    private Employee emp(long id, LocalDate hire, LocalDate retire) {
        Employee e = mock(Employee.class);
        when(e.getEmpId()).thenReturn(id);
        when(e.getEmpNo()).thenReturn("S01-2024-00" + id);
        when(e.getName()).thenReturn("예시사원" + id);
        when(e.getHireDate()).thenReturn(hire);
        when(e.getRetireDate()).thenReturn(retire);
        emps.put(id, e);
        return e;
    }

    private void code(String code, boolean active) {
        CommonCode c = mock(CommonCode.class);
        when(c.isActive()).thenReturn(active);
        codeStore.put(new CommonCodeId(CodeGroups.EMPLOYMENT_TYPE, code), c);
    }

    private List<EmploymentContract> sorted(Comparator<EmploymentContract> cmp) {
        List<EmploymentContract> all = new ArrayList<>(store.values());
        all.sort(cmp);
        return all;
    }

    @BeforeEach
    void setUp() {
        contracts = mock(EmploymentContractRepository.class);
        when(contracts.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<Long>getArgument(0))));
        when(contracts.findForUpdate(anyLong())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<Long>getArgument(0))));
        when(contracts.findEmpIdByContractId(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.<Long>getArgument(0))).map(EmploymentContract::getEmpId));
        when(contracts.saveAndFlush(any())).thenAnswer(inv -> {
            EmploymentContract c = inv.getArgument(0);
            if (c.getContractId() == null) {
                ReflectionTestUtils.setField(c, "contractId", nextId++);
            }
            store.put(c.getContractId(), c);
            return c;
        });
        when(contracts.findByEmpIdOrderByStartDateAsc(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            return sorted(Comparator.comparing(EmploymentContract::getStartDate)).stream()
                    .filter(c -> c.getEmpId() == id).toList();
        });
        when(contracts.findByWrittenDeliveredDateIsNullOrderByContractDateAscContractIdAsc()).thenAnswer(inv ->
                sorted(Comparator.comparing(EmploymentContract::getContractDate)
                        .thenComparing(EmploymentContract::getContractId)).stream()
                        .filter(c -> !c.isDelivered()).toList());
        when(contracts.findAllByOrderByEmpIdAscStartDateAsc()).thenAnswer(inv ->
                sorted(Comparator.comparing(EmploymentContract::getEmpId)
                        .thenComparing(EmploymentContract::getStartDate)));

        employees = mock(EmployeeRepository.class);
        when(employees.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(emps.get(inv.<Long>getArgument(0))));
        when(employees.findForUpdate(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(emps.get(inv.<Long>getArgument(0))));
        when(employees.findAllById(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().map(emps::get).filter(e -> e != null).toList();
        });

        CommonCodeRepository codes = mock(CommonCodeRepository.class);
        when(codes.findById(any())).thenAnswer(inv -> Optional.ofNullable(codeStore.get(inv.<CommonCodeId>getArgument(0))));
        code("REGULAR", true);
        code("FIXED_TERM", true);
        code("PART_TIME", true);
        code("OLD_TYPE", false);

        // 가짜 계산기: 계속근로 시작일 + 700일. 법정 만료일이 아니라 묶음·알림 규칙 검사용 값이다.
        useLimit = mock(FixedTermUsePeriodCalculator.class);
        when(useLimit.lastDayWithinLimit(any()))
                .thenAnswer(inv -> Optional.of(inv.<LocalDate>getArgument(0).plusDays(700)));

        emp(1, HIRE, null);
        service = new ContractService(contracts, employees, codes, useLimit, 30, 60, CLOCK);
    }

    private static ContractRequest req(String type, LocalDate start, LocalDate end) {
        return new ContractRequest(type, start, start, end, 2_500_000L, "MONTHLY", "기본급", "월급제", "매월 25일 계좌 이체",
                2400, "09:00~18:00, 휴게 12:00~13:00", "월~금 각 8시간", "주휴일 일요일", "연차 유급휴가", "가상시 본사",
                "백엔드 개발", null);
    }

    private static ContractRequest bare(String type, LocalDate start, LocalDate end) {
        return new ContractRequest(type, start, start, end, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    private static int status(Runnable r) {
        try {
            r.run();
        } catch (ContractException e) {
            return e.status().value();
        }
        return 200;
    }

    // ---------------------------------------------------------------- 등록·경고

    @Test
    void 명시사항이_빠져도_저장하고_누락_경고를_돌려준다() {
        ContractView v = service.create(1, bare("REGULAR", HIRE, null));
        assertThat(store).hasSize(1);
        assertThat(v.missingTerms()).extracting(ContractTermsChecker.MissingTerm::code)
                .contains("WAGE", "CONTRACT_HOURS", "HOLIDAY", "ANNUAL_LEAVE", "WORK_PLACE", "JOB_DUTY");
        // 조회해도 같은 경고가 나온다
        assertThat(service.get(v.contractId()).missingTerms()).isEqualTo(v.missingTerms());
    }

    @Test
    void 모두_적으면_경고가_없다() {
        assertThat(service.create(1, req("FIXED_TERM", HIRE, HIRE.plusYears(1))).missingTerms()).isEmpty();
    }

    @Test
    void 기간제는_종료일이_없으면_거부한다() {
        assertThat(status(() -> service.create(1, req("FIXED_TERM", HIRE, null)))).isEqualTo(400);
        assertThat(store).isEmpty();
    }

    @Test
    void 잘못된_날짜와_코드를_거부한다() {
        assertThat(status(() -> service.create(1, req("REGULAR", HIRE.minusDays(1), null)))).isEqualTo(400);
        assertThat(status(() -> service.create(1, req("REGULAR", HIRE.plusDays(5), HIRE.plusDays(4))))).isEqualTo(400);
        assertThat(status(() -> service.create(1, req("NOPE", HIRE, null)))).isEqualTo(400);
        assertThat(status(() -> service.create(1, req("OLD_TYPE", HIRE, null)))).isEqualTo(400);
        assertThat(status(() -> service.create(99, req("REGULAR", HIRE, null)))).isEqualTo(404);
    }

    @Test
    void 퇴직일_뒤에_시작하는_계약은_거부한다() {
        emp(2, HIRE, LocalDate.of(2025, 6, 30));
        assertThat(status(() -> service.create(2, req("REGULAR", LocalDate.of(2025, 7, 1), null)))).isEqualTo(400);
    }

    @Test
    void 기간이_겹치는_계약은_거부한다() {
        service.create(1, req("FIXED_TERM", HIRE, LocalDate.of(2024, 12, 31)));
        assertThat(status(() -> service.create(1, req("FIXED_TERM", LocalDate.of(2024, 12, 31),
                LocalDate.of(2025, 12, 31))))).isEqualTo(409);
        // 다음 날 시작은 겹치지 않는다
        service.create(1, req("FIXED_TERM", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        assertThat(store).hasSize(2);
    }

    @Test
    void 기간_없는_앞_계약은_새_계약_시작_전날로_끝난다() {
        long first = service.create(1, req("REGULAR", HIRE, null)).contractId();
        service.create(1, req("REGULAR", LocalDate.of(2025, 3, 1), null));
        assertThat(store.get(first).getEndDate()).isEqualTo(LocalDate.of(2025, 2, 28));
        // 같은 날이나 앞에 시작하는 계약으로는 끝내지 않는다
        assertThat(status(() -> service.create(1, req("REGULAR", LocalDate.of(2025, 3, 1), null)))).isEqualTo(409);
    }

    // ---------------------------------------------------------------- 수정·교부

    @Test
    void 수정과_교부는_사원_잠금_뒤에_계약을_잠가_읽고_잠금_전에는_계약_엔티티를_읽지_않는다() {
        long id = service.create(1, bare("REGULAR", HIRE, null)).contractId();
        clearInvocations(contracts, employees);

        service.update(id, req("REGULAR", HIRE, null));
        service.recordDelivery(id, new DeliveryRequest(HIRE));

        // 잠금 전에 엔티티를 올리면 대기 중 다른 트랜잭션이 기록한 교부일을 보지 못한다(Codex 리뷰 #61).
        verify(contracts, never()).findById(anyLong());
        InOrder order = inOrder(contracts, employees);
        for (int i = 0; i < 2; i++) {
            order.verify(contracts).findEmpIdByContractId(id);
            order.verify(employees).findForUpdate(1L);
            order.verify(contracts).findForUpdate(id);
        }
    }

    @Test
    void 교부_전에는_고치고_교부_뒤에는_고칠_수_없다() {
        long id = service.create(1, bare("REGULAR", HIRE, null)).contractId();
        ContractView v = service.update(id, req("REGULAR", HIRE, null));
        assertThat(v.missingTerms()).isEmpty();

        service.recordDelivery(id, new DeliveryRequest(HIRE));
        assertThat(store.get(id).getWrittenDeliveredDate()).isEqualTo(HIRE);
        assertThat(status(() -> service.update(id, bare("REGULAR", HIRE, null)))).isEqualTo(409);
        assertThat(status(() -> service.recordDelivery(id, new DeliveryRequest(HIRE.plusDays(1))))).isEqualTo(409);
    }

    @Test
    void 수정도_다른_계약과_겹치면_거부한다() {
        service.create(1, req("FIXED_TERM", HIRE, LocalDate.of(2024, 12, 31)));
        long second = service.create(1, req("FIXED_TERM", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .contractId();
        assertThat(status(() -> service.update(second, req("FIXED_TERM", LocalDate.of(2024, 12, 1),
                LocalDate.of(2025, 12, 31))))).isEqualTo(409);
        // 자기 자신과는 겹침으로 보지 않는다
        service.update(second, req("FIXED_TERM", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 31)));
    }

    @Test
    void 교부일은_체결일_전이나_미래로_기록할_수_없다() {
        long id = service.create(1, req("REGULAR", HIRE, null)).contractId();
        assertThat(status(() -> service.recordDelivery(id, new DeliveryRequest(HIRE.minusDays(1))))).isEqualTo(400);
        assertThat(status(() -> service.recordDelivery(id, new DeliveryRequest(TODAY.plusDays(1))))).isEqualTo(400);
        service.recordDelivery(id, new DeliveryRequest(TODAY));
        assertThat(status(() -> service.recordDelivery(999, new DeliveryRequest(TODAY)))).isEqualTo(404);
    }

    @Test
    void 미교부_목록은_교부일_없는_계약만_체결일_순으로_준다() {
        emp(2, HIRE, null);
        long a = service.create(1, req("FIXED_TERM", HIRE, LocalDate.of(2024, 12, 31))).contractId();
        long b = service.create(1, req("FIXED_TERM", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .contractId();
        long c = service.create(2, req("REGULAR", LocalDate.of(2024, 6, 1), null)).contractId();
        service.recordDelivery(a, new DeliveryRequest(HIRE));

        List<UndeliveredRow> rows = service.undelivered();
        assertThat(rows).extracting(UndeliveredRow::contractId).containsExactly(c, b);
        assertThat(rows.get(0).empName()).isEqualTo("예시사원2");
        assertThat(rows.get(1).daysSinceContract()).isEqualTo(java.time.temporal.ChronoUnit.DAYS
                .between(LocalDate.of(2025, 1, 1), TODAY));
    }

    // ---------------------------------------------------------------- 만료 알림

    private List<AlertType> types(List<AlertRow> rows, long empId) {
        return rows.stream().filter(r -> r.empId() == empId).map(AlertRow::type).toList();
    }

    @Test
    void 만료_알림은_종료일까지_알림_기간_이내만_준다() {
        // 알림 기간 30일(생성자 값). 회사 설정값이며 법정 수치가 아니다.
        emp(2, HIRE, null);
        emp(3, HIRE, null);
        service.create(1, req("PART_TIME", HIRE, TODAY.plusDays(30)));
        service.create(2, req("PART_TIME", HIRE, TODAY.plusDays(31)));
        service.create(3, req("PART_TIME", HIRE, TODAY));
        List<AlertRow> rows = service.alerts(TODAY);
        assertThat(types(rows, 1)).containsExactly(AlertType.CONTRACT_EXPIRING);
        assertThat(types(rows, 2)).isEmpty();
        assertThat(types(rows, 3)).containsExactly(AlertType.CONTRACT_EXPIRING);
        assertThat(rows.stream().filter(r -> r.empId() == 1).findFirst().orElseThrow().daysLeft()).isEqualTo(30L);
    }

    @Test
    void 기준일을_주지_않으면_오늘_기준이다() {
        service.create(1, req("PART_TIME", HIRE, TODAY.plusDays(10)));
        assertThat(service.alerts(null)).extracting(AlertRow::daysLeft).containsExactly(10L);
    }

    @Test
    void 마지막_계약만_보고_다음_계약이_있으면_앞_계약_만료는_알리지_않는다() {
        service.create(1, req("PART_TIME", HIRE, TODAY.plusDays(5)));
        service.create(1, req("REGULAR", TODAY.plusDays(6), null));
        assertThat(service.alerts(TODAY)).isEmpty();
    }

    @Test
    void 종료일이_지났는데_재직_중이면_만료로_알리고_퇴직자는_빼다() {
        emp(2, HIRE, TODAY.minusDays(20));
        service.create(1, req("PART_TIME", HIRE, TODAY.minusDays(1)));
        service.create(2, req("PART_TIME", HIRE, TODAY.minusDays(20)));
        List<AlertRow> rows = service.alerts(TODAY);
        assertThat(types(rows, 1)).containsExactly(AlertType.CONTRACT_EXPIRED);
        assertThat(rows.get(0).daysLeft()).isEqualTo(-1L);
        assertThat(types(rows, 2)).isEmpty();
    }

    @Test
    void 기간_없는_계약은_만료_알림이_없다() {
        service.create(1, req("REGULAR", HIRE, null));
        assertThat(service.alerts(TODAY)).isEmpty();
    }

    // ---------------------------------------------------------------- 기간제 사용기간 알림

    @Test
    void 공백_없이_이어진_기간제_계약은_첫_계약_시작일부터_센다() {
        LocalDate s1 = LocalDate.of(2025, 1, 1);
        service.create(1, req("FIXED_TERM", s1, LocalDate.of(2025, 6, 30)));
        service.create(1, req("FIXED_TERM", LocalDate.of(2025, 7, 1), LocalDate.of(2026, 12, 31)));
        // 가짜 계산기: s1 + 700일 = 2026-12-02. 오늘부터 20일 → 사용기간 알림 기간(60일) 이내
        List<AlertRow> rows = service.alerts(TODAY);
        AlertRow limit = rows.stream().filter(r -> r.type() == AlertType.FIXED_TERM_LIMIT).findFirst().orElseThrow();
        assertThat(limit.continuousStartDate()).isEqualTo(s1);
        assertThat(limit.dueDate()).isEqualTo(s1.plusDays(700));
        assertThat(limit.daysLeft()).isEqualTo(java.time.temporal.ChronoUnit.DAYS.between(TODAY, s1.plusDays(700)));
    }

    @Test
    void 공백이_있으면_공백_뒤_계약부터_다시_센다() {
        service.create(1, req("FIXED_TERM", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 12, 31)));
        service.create(1, req("FIXED_TERM", LocalDate.of(2025, 1, 2), LocalDate.of(2026, 12, 31)));
        List<EmploymentContract> list = new ArrayList<>(store.values());
        list.sort(Comparator.comparing(EmploymentContract::getStartDate));
        assertThat(ContractService.continuousFixedTermStart(list)).isEqualTo(LocalDate.of(2025, 1, 2));
    }

    @Test
    void 기간제가_아닌_앞_계약은_계속근로에_넣지_않는다() {
        service.create(1, req("PART_TIME", LocalDate.of(2024, 1, 2), LocalDate.of(2024, 12, 31)));
        service.create(1, req("FIXED_TERM", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31)));
        List<EmploymentContract> list = new ArrayList<>(store.values());
        list.sort(Comparator.comparing(EmploymentContract::getStartDate));
        assertThat(ContractService.continuousFixedTermStart(list)).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void 사용기간_상한이_알림_기간보다_멀면_알리지_않지만_계약이_상한을_넘으면_알린다() {
        emp(2, HIRE, null);
        // 사원1: 시작 2026-10-01, 가짜 상한 2028-08-31, 종료 2027-09-30 → 알림 없음
        service.create(1, req("FIXED_TERM", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30)));
        // 사원2: 같은 시작인데 종료가 가짜 상한보다 뒤 → 알림
        service.create(2, req("FIXED_TERM", LocalDate.of(2026, 10, 1), LocalDate.of(2028, 12, 31)));
        List<AlertRow> rows = service.alerts(TODAY);
        assertThat(types(rows, 1)).isEmpty();
        assertThat(types(rows, 2)).containsExactly(AlertType.FIXED_TERM_LIMIT);
    }

    @Test
    void 사용기간_상한이_지났어도_기간제로_재직_중이면_음수로_알린다() {
        service.create(1, req("FIXED_TERM", LocalDate.of(2024, 1, 2), LocalDate.of(2026, 12, 31)));
        AlertRow limit = service.alerts(TODAY).stream().filter(r -> r.type() == AlertType.FIXED_TERM_LIMIT)
                .findFirst().orElseThrow();
        assertThat(limit.daysLeft()).isNegative();
    }

    @Test
    void 기준값이_없으면_계산하지_않고_기준값_없음으로_알린다() {
        org.mockito.Mockito.doReturn(Optional.empty()).when(useLimit).lastDayWithinLimit(any());
        service.create(1, req("FIXED_TERM", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30)));
        List<AlertRow> rows = service.alerts(TODAY);
        assertThat(rows).extracting(AlertRow::type).containsExactly(AlertType.FIXED_TERM_LIMIT_UNKNOWN);
        assertThat(rows.get(0).dueDate()).isNull();
    }

    @Test
    void 알림_기간이_음수면_만들_수_없다() {
        assertThatThrownBy(() -> new ContractService(null, null, null, null, -1, 0, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 예외_상태_코드() {
        assertThat(ContractException.conflict("x").status()).isEqualTo(HttpStatus.CONFLICT);
    }
}
