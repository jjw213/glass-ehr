package com.glassehr.contract;

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
import com.glassehr.contract.EmploymentContract.Terms;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근로계약 이력 관리. 설계: docs/employment-contract-design.md.
 *
 * <ul>
 *   <li>근로기준법 제17조·시행령 제8조, 기간제법 제17조: 명시사항 누락을 경고한다(저장은 막지 않음, {@link ContractTermsChecker}).</li>
 *   <li>근로기준법 제17조 제2항, 기간제법 제17조: 서면 교부일을 기록하고, 교부 안 된 계약 목록을 준다.</li>
 *   <li>기간제법 제4조: 기간제 계속근로 총기간이 사용기간 상한에 가까우면 알린다({@link FixedTermUsePeriodCalculator}).</li>
 *   <li>근로기준법 제42조: 계약 행을 지우지 않는다(삭제 기능 없음).</li>
 * </ul>
 * 알림 기간(만료 30일 전 등)은 법정 수치가 아니라 회사 운영 설정이라 {@code glassehr.contract.*} 설정에서 읽는다(이슈 #17 완료 기준의 30일).
 * 쓰기 권한 검사는 백엔드 인증·권한 이슈에서 붙인다(사원 API와 같음).
 */
@Service
@Transactional(readOnly = true)
public class ContractService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final EmploymentContractRepository contracts;
    private final EmployeeRepository employees;
    private final CommonCodeRepository codes;
    private final FixedTermUsePeriodCalculator useLimit;
    private final int expiryAlertDays;
    private final int fixedTermLimitAlertDays;
    private final Clock clock;

    @Autowired
    public ContractService(EmploymentContractRepository contracts, EmployeeRepository employees,
            CommonCodeRepository codes, FixedTermUsePeriodCalculator useLimit,
            @Value("${glassehr.contract.expiry-alert-days}") int expiryAlertDays,
            @Value("${glassehr.contract.fixed-term-limit-alert-days}") int fixedTermLimitAlertDays) {
        this(contracts, employees, codes, useLimit, expiryAlertDays, fixedTermLimitAlertDays, Clock.system(SEOUL));
    }

    ContractService(EmploymentContractRepository contracts, EmployeeRepository employees, CommonCodeRepository codes,
            FixedTermUsePeriodCalculator useLimit, int expiryAlertDays, int fixedTermLimitAlertDays, Clock clock) {
        if (expiryAlertDays < 0 || fixedTermLimitAlertDays < 0) {
            throw new IllegalArgumentException("알림 기간(일)은 0 이상이어야 해요");
        }
        this.contracts = contracts;
        this.employees = employees;
        this.codes = codes;
        this.useLimit = useLimit;
        this.expiryAlertDays = expiryAlertDays;
        this.fixedTermLimitAlertDays = fixedTermLimitAlertDays;
        this.clock = clock.withZone(SEOUL);
    }

    // ---------------------------------------------------------------- 조회

    public List<ContractView> list(long empId) {
        loadEmployee(empId);
        return contracts.findByEmpIdOrderByStartDateAsc(empId).stream().map(ContractView::of).toList();
    }

    public ContractView get(long contractId) {
        return ContractView.of(load(contractId));
    }

    // ---------------------------------------------------------------- 등록·수정

    /**
     * 계약 등록. 같은 사원의 다른 계약과 기간이 겹치면 거부한다. 다만 바로 앞 계약이 기간의 정함이 없고 새 계약보다 먼저 시작했으면
     * (근로조건 변경으로 새 계약서를 쓰는 경우) 앞 계약을 새 계약 시작 전날로 끝낸다.
     */
    @Transactional
    public ContractView create(long empId, ContractRequest req) {
        Employee e = lockEmployee(empId);
        Terms t = terms(req);
        validate(t, e);
        LocalDateTime now = now();
        for (EmploymentContract other : contracts.findByEmpIdOrderByStartDateAsc(empId)) {
            if (!other.overlaps(t.startDate(), t.endDate())) {
                continue;
            }
            if (other.getEndDate() == null && other.getStartDate().isBefore(t.startDate())) {
                other.endBefore(t.startDate(), now);
                contracts.saveAndFlush(other);
                continue;
            }
            throw overlap(other);
        }
        EmploymentContract c = contracts.saveAndFlush(new EmploymentContract(empId, t, now));
        return ContractView.of(c);
    }

    /** 서면 교부 전 계약만 고친다. 교부 뒤 변경은 새 계약으로 등록한다(교부한 서면과 기록이 달라지지 않게). */
    @Transactional
    public ContractView update(long contractId, ContractRequest req) {
        EmploymentContract c = load(contractId);
        Employee e = lockEmployee(c.getEmpId());
        if (c.isDelivered()) {
            throw ContractException.conflict("서면을 교부한 계약(교부일 " + c.getWrittenDeliveredDate()
                    + ")은 고칠 수 없어요. 바뀐 근로조건은 새 계약으로 등록해 주세요");
        }
        Terms t = terms(req);
        validate(t, e);
        for (EmploymentContract other : contracts.findByEmpIdOrderByStartDateAsc(c.getEmpId())) {
            if (!other.getContractId().equals(contractId) && other.overlaps(t.startDate(), t.endDate())) {
                throw overlap(other);
            }
        }
        c.update(t, now());
        contracts.saveAndFlush(c);
        return ContractView.of(c);
    }

    /** 서면 교부일 기록(근로기준법 제17조 제2항, 기간제법 제17조). 한 번 기록하면 바꾸지 않는다. */
    @Transactional
    public ContractView recordDelivery(long contractId, DeliveryRequest req) {
        EmploymentContract c = load(contractId);
        lockEmployee(c.getEmpId());
        if (c.isDelivered()) {
            throw ContractException.conflict("이미 서면 교부일이 기록돼 있어요(" + c.getWrittenDeliveredDate() + ")");
        }
        if (req.deliveredDate().isBefore(c.getContractDate())) {
            throw ContractException.badRequest("서면 교부일은 계약 체결일(" + c.getContractDate() + ") 이후여야 해요");
        }
        if (req.deliveredDate().isAfter(today())) {
            throw ContractException.badRequest("서면 교부일은 오늘 이후로 기록할 수 없어요");
        }
        c.recordDelivery(req.deliveredDate(), now());
        contracts.saveAndFlush(c);
        return ContractView.of(c);
    }

    // ---------------------------------------------------------------- 목록·알림

    /** 서면 교부일이 없는 계약. 체결일이 오래된 순. */
    public List<UndeliveredRow> undelivered() {
        List<EmploymentContract> rows = contracts.findByWrittenDeliveredDateIsNullOrderByContractDateAscContractIdAsc();
        Map<Long, Employee> emps = employeesOf(rows);
        LocalDate today = today();
        return rows.stream().map(c -> {
            Employee e = emps.get(c.getEmpId());
            return new UndeliveredRow(c.getContractId(), c.getEmpId(), e == null ? null : e.getEmpNo(),
                    e == null ? null : e.getName(), c.getContractTypeCode(), c.getContractDate(), c.getStartDate(),
                    c.getEndDate(), ChronoUnit.DAYS.between(c.getContractDate(), today));
        }).toList();
    }

    /**
     * 기준일(asOf, 없으면 오늘) 기준 알림. 기준일에 재직 중인(퇴직일이 없거나 기준일 이후인) 사원의 마지막 계약만 본다.
     * <ul>
     *   <li>CONTRACT_EXPIRING: 종료일까지 0일 이상, 만료 알림 기간 이하</li>
     *   <li>CONTRACT_EXPIRED: 종료일이 지났는데 재직 중이고 다음 계약이 없음</li>
     *   <li>FIXED_TERM_LIMIT: 마지막 계약이 기간제이고, 끊김 없이 이어진 기간제 계약의 계속근로 시작일부터 계산한
     *       사용기간 상한의 마지막 날까지 사용기간 알림 기간 이하(지났으면 음수)이거나, 마지막 계약 종료일이 그날을 넘음</li>
     *   <li>FIXED_TERM_LIMIT_UNKNOWN: 기간제인데 legal_rate 기준값이 없어 계산 못 함</li>
     * </ul>
     */
    public List<AlertRow> alerts(LocalDate asOf) {
        LocalDate base = asOf == null ? today() : asOf;
        Map<Long, List<EmploymentContract>> byEmp = contracts.findAllByOrderByEmpIdAscStartDateAsc().stream()
                .collect(Collectors.groupingBy(EmploymentContract::getEmpId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Employee> emps = employees.findAllById(byEmp.keySet()).stream()
                .collect(Collectors.toMap(Employee::getEmpId, Function.identity()));
        List<AlertRow> out = new ArrayList<>();
        byEmp.forEach((empId, list) -> {
            Employee e = emps.get(empId);
            if (e == null || (e.getRetireDate() != null && e.getRetireDate().isBefore(base))) {
                return;
            }
            EmploymentContract last = list.get(list.size() - 1);
            if (last.getEndDate() != null) {
                long days = ChronoUnit.DAYS.between(base, last.getEndDate());
                if (days < 0) {
                    out.add(row(AlertType.CONTRACT_EXPIRED, e, last, null, last.getEndDate(), days));
                } else if (days <= expiryAlertDays) {
                    out.add(row(AlertType.CONTRACT_EXPIRING, e, last, null, last.getEndDate(), days));
                }
            }
            if (ContractTermsChecker.FIXED_TERM.equals(last.getContractTypeCode())) {
                LocalDate continuousStart = continuousFixedTermStart(list);
                Optional<LocalDate> lastDay = useLimit.lastDayWithinLimit(continuousStart);
                if (lastDay.isEmpty()) {
                    out.add(row(AlertType.FIXED_TERM_LIMIT_UNKNOWN, e, last, continuousStart, null, null));
                } else {
                    long days = ChronoUnit.DAYS.between(base, lastDay.get());
                    boolean runsPast = last.getEndDate().isAfter(lastDay.get());
                    if (days <= fixedTermLimitAlertDays || runsPast) {
                        out.add(row(AlertType.FIXED_TERM_LIMIT, e, last, continuousStart, lastDay.get(), days));
                    }
                }
            }
        });
        out.sort(Comparator.comparing(AlertRow::dueDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(AlertRow::empId).thenComparing(r -> r.type().ordinal()));
        return out;
    }

    /**
     * 마지막 계약에서 거슬러 올라가며, 앞 계약이 기간제이고 종료일 다음 날까지 다음 계약이 시작하면(공백 없음) 이어진 것으로 본다.
     * 공백이 있으면 그 뒤부터 다시 센다. 기간제법 제4조 제1항 "계속근로한 총기간"의 공백 판단 기준은 미결(docs 4장) — 지금은 공백 0일만 이어짐.
     */
    static LocalDate continuousFixedTermStart(List<EmploymentContract> sortedByStart) {
        int i = sortedByStart.size() - 1;
        LocalDate start = sortedByStart.get(i).getStartDate();
        while (i > 0) {
            EmploymentContract prev = sortedByStart.get(i - 1);
            boolean fixed = ContractTermsChecker.FIXED_TERM.equals(prev.getContractTypeCode());
            if (!fixed || prev.getEndDate() == null || prev.getEndDate().plusDays(1).isBefore(start)) {
                break;
            }
            start = prev.getStartDate();
            i--;
        }
        return start;
    }

    // ---------------------------------------------------------------- 검사

    private static AlertRow row(AlertType type, Employee e, EmploymentContract c, LocalDate continuousStart,
            LocalDate due, Long daysLeft) {
        return new AlertRow(type, e.getEmpId(), e.getEmpNo(), e.getName(), c.getContractId(), c.getEndDate(),
                continuousStart, due, daysLeft);
    }

    private Map<Long, Employee> employeesOf(List<EmploymentContract> rows) {
        List<Long> ids = rows.stream().map(EmploymentContract::getEmpId).distinct().toList();
        return employees.findAllById(ids).stream().collect(Collectors.toMap(Employee::getEmpId, Function.identity()));
    }

    private EmploymentContract load(long contractId) {
        return contracts.findById(contractId)
                .orElseThrow(() -> ContractException.notFound("근로계약이 없어요: " + contractId));
    }

    private Employee loadEmployee(long empId) {
        return employees.findById(empId).orElseThrow(() -> ContractException.notFound("사원이 없어요: " + empId));
    }

    /** 같은 사원의 계약 쓰기를 직렬화한다(겹침 검사가 엇갈려 둘 다 통과하지 않게). */
    private Employee lockEmployee(long empId) {
        return employees.findForUpdate(empId).orElseThrow(() -> ContractException.notFound("사원이 없어요: " + empId));
    }

    private void validate(Terms t, Employee e) {
        requireEmploymentType(t.contractTypeCode());
        if (t.endDate() != null && t.endDate().isBefore(t.startDate())) {
            throw ContractException.badRequest("계약 종료일은 계약 시작일 이후여야 해요");
        }
        // 기간제법 제2조 제1호: 기간의 정함이 있는 근로계약. 종료일이 없으면 사용기간 계산·만료 알림을 할 수 없다.
        if (ContractTermsChecker.FIXED_TERM.equals(t.contractTypeCode()) && t.endDate() == null) {
            throw ContractException.badRequest("기간제 계약은 계약 종료일을 입력해 주세요");
        }
        if (t.startDate().isBefore(e.getHireDate())) {
            throw ContractException.badRequest("계약 시작일은 입사일(" + e.getHireDate() + ") 이후여야 해요");
        }
        if (e.getRetireDate() != null && t.startDate().isAfter(e.getRetireDate())) {
            throw ContractException.badRequest("퇴직일(" + e.getRetireDate() + ") 뒤에 시작하는 계약은 등록할 수 없어요");
        }
    }

    private void requireEmploymentType(String code) {
        CommonCode c = codes.findById(new CommonCodeId(CodeGroups.EMPLOYMENT_TYPE, code)).orElse(null);
        if (c == null) {
            throw ContractException.badRequest("없는 고용형태 코드예요: " + code);
        }
        if (!c.isActive()) {
            throw ContractException.badRequest("사용하지 않는 고용형태 코드예요: " + code);
        }
    }

    private static ContractException overlap(EmploymentContract other) {
        return ContractException.conflict("다른 계약(" + other.getStartDate() + " ~ "
                + (other.getEndDate() == null ? "기간 없음" : other.getEndDate()) + ")과 기간이 겹쳐요");
    }

    private static Terms terms(ContractRequest r) {
        return new Terms(r.contractTypeCode().strip(), r.contractDate(), r.startDate(), r.endDate(), r.wageAmount(),
                blankToNull(r.wageUnitCode()), blankToNull(r.wageComponents()), blankToNull(r.wageCalcMethod()),
                blankToNull(r.wagePaymentMethod()), r.weeklyContractMinutes(), blankToNull(r.workHoursText()),
                blankToNull(r.workDaysText()), blankToNull(r.holidayText()), blankToNull(r.leaveText()),
                blankToNull(r.workPlace()), blankToNull(r.jobDuty()), blankToNull(r.otherTerms()));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
