package com.glassehr.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.code.CodeGroups;
import com.glassehr.code.CommonCode;
import com.glassehr.code.CommonCodeId;
import com.glassehr.code.CommonCodeRepository;
import com.glassehr.contract.EmploymentContract;
import com.glassehr.contract.EmploymentContractRepository;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeDtos;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.employee.EmployeeService;
import com.glassehr.retirement.DismissalNoticeChecker.Exemption;
import com.glassehr.retirement.DismissalNoticeChecker.NoticeCheck;
import com.glassehr.retirement.DismissalNoticeChecker.Result;
import com.glassehr.retirement.RetirementDtos.CertificateItem;
import com.glassehr.retirement.RetirementDtos.CertificateRequest;
import com.glassehr.retirement.RetirementDtos.CertificateView;
import com.glassehr.retirement.RetirementDtos.ChecklistItemView;
import com.glassehr.retirement.RetirementDtos.ChecklistUpdateRequest;
import com.glassehr.retirement.RetirementDtos.RetentionExpiredRow;
import com.glassehr.retirement.RetirementDtos.RetirementRequest;
import com.glassehr.retirement.RetirementDtos.RetirementView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 퇴직 처리·사용증명서·보존기한 목록 규칙. 저장소는 메모리 가짜, 해고 예고 확인기는 가짜 결과를 돌려준다
 * (예고 일수 규칙은 DismissalNoticeCheckerTest). 법정 수치·경계 정답은 다루지 않는다.
 */
class RetirementServiceTest {

    /** 오늘(서울) = 2026-11-16. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate HIRE = LocalDate.of(2024, 1, 2);
    private static final LocalDate RETIRE = LocalDate.of(2026, 11, 30);

    private final Map<Long, Employee> emps = new HashMap<>();
    private final Map<Long, RetirementCase> caseStore = new HashMap<>();
    private final List<RetirementChecklistItem> itemStore = new ArrayList<>();
    private final Map<Long, EmploymentCertificate> certStore = new HashMap<>();
    private final Map<CommonCodeId, CommonCode> codeStore = new HashMap<>();
    private final List<EmploymentContract> contractStore = new ArrayList<>();
    private long nextId = 1;

    private EmployeeService employeeService;
    private EmployeeRepository employees;
    private DismissalNoticeChecker checker;
    private RetirementService service;

    private Employee emp(long id, LocalDate retire, String reason, LocalDate until) {
        Employee e = mock(Employee.class);
        when(e.getEnterCd()).thenReturn("C001");
        when(e.getEmpId()).thenReturn(id);
        when(e.getEmpNo()).thenReturn("S01-2024-00" + id);
        when(e.getName()).thenReturn("예시사원" + id);
        when(e.getHireDate()).thenReturn(HIRE);
        when(e.getRetireDate()).thenReturn(retire);
        when(e.getRetireReasonCode()).thenReturn(reason);
        when(e.getRecordRetentionUntil()).thenReturn(until);
        when(e.getJobDescription()).thenReturn("백엔드 개발");
        when(e.getJobGradeCode()).thenReturn("STAFF");
        when(e.getJobTitleCode()).thenReturn(null);
        emps.put(id, e);
        return e;
    }

    private void code(String group, String code, String name) {
        CommonCode c = mock(CommonCode.class);
        when(c.getCodeName()).thenReturn(name);
        codeStore.put(new CommonCodeId("C001", group, code), c);
    }

    private void contract(long empId, LocalDate start, Long wage) {
        EmploymentContract c = mock(EmploymentContract.class);
        when(c.terms()).thenReturn(new EmploymentContract.Terms("REGULAR", start, start, null, wage,
                wage == null ? null : "MONTHLY", null, null, null, null, null, null, null, null, null, null, null));
        when(c.getEmpId()).thenReturn(empId);
        contractStore.add(c);
    }

    @BeforeEach
    void setUp() {
        employeeService = mock(EmployeeService.class);
        employees = mock(EmployeeRepository.class);
        when(employees.findByEnterCdAndEmpId(any(), anyLong())).thenAnswer(inv -> Optional.ofNullable(emps.get(inv.<Long>getArgument(1))));

        RetirementCaseRepository cases = mock(RetirementCaseRepository.class);
        when(cases.saveAndFlush(any())).thenAnswer(inv -> {
            RetirementCase c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "caseId", nextId++);
            caseStore.put(c.getEmpId(), c);
            return c;
        });
        when(cases.findByEnterCdAndEmpId(any(), anyLong())).thenAnswer(inv -> Optional.ofNullable(caseStore.get(inv.<Long>getArgument(1))));
        when(cases.existsByEnterCdAndEmpId(any(), anyLong())).thenAnswer(inv -> caseStore.containsKey(inv.<Long>getArgument(1)));

        RetirementChecklistItemRepository checklist = mock(RetirementChecklistItemRepository.class);
        when(checklist.saveAll(any())).thenAnswer(inv -> {
            Iterable<RetirementChecklistItem> it = inv.getArgument(0);
            it.forEach(itemStore::add);
            return List.copyOf(itemStore);
        });
        when(checklist.findByEnterCdAndCaseIdOrderBySortOrderAsc(any(), anyLong())).thenAnswer(inv -> itemStore.stream()
                .filter(i -> i.getCaseId().equals(inv.<Long>getArgument(1))).toList());
        when(checklist.findByEnterCdAndCaseIdAndItemCode(any(), anyLong(), any())).thenAnswer(inv -> itemStore.stream()
                .filter(i -> i.getCaseId().equals(inv.<Long>getArgument(1)) &&i.getItemCode().equals(inv.getArgument(2)))
                .findFirst());

        EmploymentCertificateRepository certs = mock(EmploymentCertificateRepository.class);
        when(certs.saveAndFlush(any())).thenAnswer(inv -> {
            EmploymentCertificate c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "certId", nextId++);
            certStore.put(c.getCertId(), c);
            return c;
        });
        when(certs.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(certStore.get(inv.<Long>getArgument(0))));

        EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
        when(contracts.findByEnterCdAndEmpIdOrderByStartDateAsc(any(), anyLong())).thenAnswer(inv -> contractStore.stream()
                .filter(c -> c.getEmpId().equals(inv.<Long>getArgument(1))).toList());

        CommonCodeRepository codes = mock(CommonCodeRepository.class);
        when(codes.findByEnterCdAndGroupCodeAndCode(any(), any(), any())).thenAnswer(inv -> Optional.ofNullable(codeStore.get(
                new CommonCodeId(inv.<String>getArgument(0), inv.<String>getArgument(1), inv.<String>getArgument(2)))));
        code(CodeGroups.JOB_GRADE, "STAFF", "사원");
        code(CodeGroups.RETIREMENT_REASON, "VOLUNTARY", "자발적 퇴사(개인 사정)");

        checker = mock(DismissalNoticeChecker.class);
        service = new RetirementService(employeeService, employees, cases, checklist, certs, contracts, codes, checker,
                CLOCK);
    }

    private static RetirementRequest req(String reason, LocalDate notice, LocalDate written, Exemption exempt) {
        return new RetirementRequest(1L, RETIRE, reason, null, notice, written, exempt);
    }

    private static void assertStatus(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(RetirementException.class,
                e -> assertThat(e.status()).isEqualTo(status));
    }

    // ------------------------------------------------------------ 체크리스트

    @Test
    void 자진퇴사는_사직서와_공통_항목_체크리스트를_만든다() {
        emp(1, RETIRE, "VOLUNTARY", RETIRE.plusYears(1));
        RetirementView v = service.register("C001", req(" VOLUNTARY ", null, null, null), "hr1");

        ArgumentCaptor<EmployeeDtos.RetirementRequest> sent = ArgumentCaptor.forClass(EmployeeDtos.RetirementRequest.class);
        verify(employeeService).retire(eq("C001"), eq(1L), sent.capture());
        assertThat(sent.getValue().reasonCode()).isEqualTo("VOLUNTARY");
        assertThat(sent.getValue().retireDate()).isEqualTo(RETIRE);
        verify(checker, never()).check(any(), any(), any(), any());

        assertThat(v.notice().check()).isEqualTo(NoticeCheck.NOT_APPLICABLE);
        assertThat(v.checklist()).extracting(ChecklistItemView::itemCode)
                .startsWith("RESIGNATION_LETTER").contains("HANDOVER", "SETTLEMENT", "CERTIFICATE_GUIDE")
                .doesNotContain("DISMISSAL_NOTICE");
        assertThat(v.checklist()).noneMatch(ChecklistItemView::done);
        assertThat(v.recordRetentionUntil()).isEqualTo(RETIRE.plusYears(1));
        assertThat(v.createdBy()).isEqualTo("hr1");
    }

    @Test
    void 유형마다_체크리스트가_다르다() {
        assertThat(service.template("CONTRACT_EXPIRED")).extracting(i -> i.itemCode()).contains("CONTRACT_END_CHECK");
        assertThat(service.template("RETIREMENT_AGE")).extracting(i -> i.itemCode()).contains("RETIREMENT_AGE_CHECK");
        assertThat(service.template("DISMISSAL")).extracting(i -> i.itemCode())
                .contains("DISMISSAL_CAUSE", "DISMISSAL_NOTICE", "DISMISSAL_WRITTEN", "NOTICE_ALLOWANCE");
        assertThat(service.template("OTHER")).extracting(i -> i.itemCode())
                .containsExactly("HANDOVER", "ASSET_RETURN", "SETTLEMENT", "INSURANCE_LOSS", "CERTIFICATE_GUIDE",
                        "RECORD_RETENTION");
        assertThat(service.template("DISMISSAL")).filteredOn(i -> i.itemCode().equals("DISMISSAL_WRITTEN"))
                .singleElement().extracting(i -> i.legalBasis()).isEqualTo("근로기준법 제27조");
    }

    @Test
    void 체크리스트_완료_표시와_해제() {
        emp(1, RETIRE, "VOLUNTARY", RETIRE.plusYears(1));
        service.register("C001", req("VOLUNTARY", null, null, null), "hr1");
        RetirementView v = service.updateChecklist("C001", 1, "HANDOVER", new ChecklistUpdateRequest(true, " 인계 완료 "), "hr2");
        ChecklistItemView h = v.checklist().stream().filter(i -> i.itemCode().equals("HANDOVER")).findFirst().orElseThrow();
        assertThat(h.done()).isTrue();
        assertThat(h.doneBy()).isEqualTo("hr2");
        assertThat(h.note()).isEqualTo("인계 완료");

        v = service.updateChecklist("C001", 1, "HANDOVER", new ChecklistUpdateRequest(false, null), "hr2");
        h = v.checklist().stream().filter(i -> i.itemCode().equals("HANDOVER")).findFirst().orElseThrow();
        assertThat(h.done()).isFalse();
        assertThat(h.doneBy()).isNull();

        assertStatus(() -> service.updateChecklist("C001", 1, "NOPE", new ChecklistUpdateRequest(true, null), "hr2"),
                HttpStatus.NOT_FOUND);
        assertStatus(() -> service.updateChecklist("C001", 9, "HANDOVER", new ChecklistUpdateRequest(true, null), "hr2"),
                HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------ 해고

    @Test
    void 해고는_예고일이_없으면_400이고_퇴직_기록을_하지_않는다() {
        emp(1, RETIRE, "DISMISSAL", RETIRE.plusYears(1));
        assertStatus(() -> service.register("C001", req("DISMISSAL", null, RETIRE.minusDays(1), null), "hr1"),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.register("C001", req("DISMISSAL", RETIRE.minusDays(1), null, null), "hr1"),
                HttpStatus.BAD_REQUEST);
        verify(employeeService, never()).retire(any(), anyLong(), any());
    }

    @Test
    void 해고_예고일과_서면통지일은_해고일보다_늦을_수_없다() {
        emp(1, RETIRE, "DISMISSAL", RETIRE.plusYears(1));
        assertStatus(() -> service.register("C001", req("DISMISSAL", RETIRE.plusDays(1), RETIRE, null), "hr1"),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.register("C001", req("DISMISSAL", RETIRE, RETIRE.plusDays(1), null), "hr1"),
                HttpStatus.BAD_REQUEST);
        verify(employeeService, never()).retire(any(), anyLong(), any());
    }

    @Test
    void 해고가_아니면_예고_항목을_보낼_수_없다() {
        emp(1, RETIRE, "VOLUNTARY", RETIRE.plusYears(1));
        assertStatus(() -> service.register("C001", req("VOLUNTARY", RETIRE.minusDays(1), null, null), "hr1"),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.register("C001", req("VOLUNTARY", null, null, Exemption.UNAVOIDABLE), "hr1"),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void 해고_예고_미달이면_경고하고_저장하며_수당_검토_항목을_넣는다() {
        emp(1, RETIRE, "DISMISSAL", RETIRE.plusYears(1));
        LocalDate notice = RETIRE.minusDays(3);
        when(checker.check(HIRE, notice, RETIRE, null)).thenReturn(
                new Result(NoticeCheck.SHORT, 10, 3, 7, List.of("해고 예고 기간이 7일 모자라요")));
        RetirementView v = service.register("C001", req("DISMISSAL", notice, RETIRE.minusDays(2), null), "hr1");

        InOrder order = inOrder(employeeService, checker);
        order.verify(employeeService).retire(eq("C001"), eq(1L), any());
        order.verify(checker).check(HIRE, notice, RETIRE, null);
        assertThat(v.notice().check()).isEqualTo(NoticeCheck.SHORT);
        assertThat(v.notice().shortfallDays()).isEqualTo(7);
        assertThat(v.notice().noticeDays()).isEqualTo(3);
        assertThat(v.notice().writtenNoticeDate()).isEqualTo(RETIRE.minusDays(2));
        assertThat(v.warnings()).containsExactly("해고 예고 기간이 7일 모자라요");
        assertThat(v.checklist()).extracting(ChecklistItemView::itemCode).contains("NOTICE_ALLOWANCE");
        assertThat(v.checklist()).filteredOn(i -> i.itemCode().startsWith("DISMISSAL_NOTICE")
                || i.itemCode().equals("DISMISSAL_WRITTEN")).hasSize(2).allMatch(ChecklistItemView::done);
        assertThat(v.checklist()).filteredOn(i -> i.itemCode().equals("DISMISSAL_CAUSE"))
                .singleElement().matches(i -> !i.done());

        // 다시 조회하면 저장된 값이 나오고 경고는 등록 응답에만 있다
        RetirementView again = service.get("C001", 1);
        assertThat(again.notice().check()).isEqualTo(NoticeCheck.SHORT);
        assertThat(again.warnings()).isEmpty();
    }

    @Test
    void 해고_예고_충족이면_수당_검토_항목이_없다() {
        emp(1, RETIRE, "DISMISSAL", RETIRE.plusYears(1));
        when(checker.check(any(), any(), any(), any())).thenReturn(new Result(NoticeCheck.OK, 10, 12, null, List.of()));
        RetirementView v = service.register("C001", req("DISMISSAL", RETIRE.minusDays(12), RETIRE.minusDays(12), null), "hr1");
        assertThat(v.checklist()).extracting(ChecklistItemView::itemCode).doesNotContain("NOTICE_ALLOWANCE");
        assertThat(v.warnings()).isEmpty();
    }

    @Test
    void 이미_퇴직_처리가_있으면_409() {
        emp(1, RETIRE, "VOLUNTARY", RETIRE.plusYears(1));
        service.register("C001", req("VOLUNTARY", null, null, null), "hr1");
        assertStatus(() -> service.register("C001", req("VOLUNTARY", null, null, null), "hr1"), HttpStatus.CONFLICT);
    }

    @Test
    void 퇴직_처리_기록이_없으면_404() {
        assertStatus(() -> service.get("C001", 1), HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------ 보존기한

    @Test
    void 보존기한_다음_날부터_경과_목록에_나온다() {
        LocalDate asOf = LocalDate.of(2026, 11, 16);
        Employee e = emp(5, LocalDate.of(2023, 11, 15), "VOLUNTARY", LocalDate.of(2026, 11, 10));
        when(employees.findByEnterCdAndRecordRetentionUntilBeforeOrderByRecordRetentionUntilAscEmpNoAsc("C001", asOf))
                .thenReturn(List.of(e));
        List<RetentionExpiredRow> rows = service.retentionExpired("C001", null); // 기본 = 오늘(서울)
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.empId()).isEqualTo(5);
            assertThat(r.recordRetentionUntil()).isEqualTo(LocalDate.of(2026, 11, 10));
            assertThat(r.daysOverdue()).isEqualTo(6);
        });
    }

    // ------------------------------------------------------------ 사용증명서

    @Test
    void 사용증명서는_요구한_항목만_적는다() {
        emp(1, RETIRE, "VOLUNTARY", RETIRE.plusYears(1));
        contract(1, HIRE, 3_000_000L);
        CertificateView v = service.issueCertificate("C001", 1,
                new CertificateRequest(LocalDate.of(2026, 12, 1), EnumSet.of(CertificateItem.USE_PERIOD), " 이직 "),
                "hr1");
        assertThat(v.items()).containsExactly(CertificateItem.USE_PERIOD);
        assertThat(v.empName()).isEqualTo("예시사원1");
        assertThat(v.useFrom()).isEqualTo(HIRE);
        assertThat(v.useTo()).isEqualTo(RETIRE);
        assertThat(v.jobType()).isNull();
        assertThat(v.positionText()).isNull();
        assertThat(v.wageAmount()).isNull();
        assertThat(v.retireReason()).isNull();
        assertThat(v.purpose()).isEqualTo("이직");
        assertThat(v.issuedBy()).isEqualTo("hr1");

        EmploymentCertificate saved = certStore.get(v.certId());
        assertThat(saved.getRequestedItems()).isEqualTo("USE_PERIOD");
        assertThat(saved.getWageAmount()).isNull();
        assertThat(saved.getJobType()).isNull();
    }

    @Test
    void 사용증명서_전체_항목() {
        emp(1, RETIRE, "VOLUNTARY", RETIRE.plusYears(1));
        contract(1, HIRE, 2_800_000L);
        contract(1, LocalDate.of(2025, 1, 1), 3_100_000L);
        contract(1, LocalDate.of(2027, 1, 1), 9_999_999L); // 퇴직일 뒤 시작 계약은 쓰지 않음
        CertificateView v = service.issueCertificate("C001", 1, new CertificateRequest(LocalDate.of(2026, 12, 1),
                EnumSet.allOf(CertificateItem.class), null), "hr1");
        assertThat(v.items()).containsExactly(CertificateItem.values());
        assertThat(v.jobType()).isEqualTo("백엔드 개발");
        assertThat(v.positionText()).isEqualTo("사원");
        assertThat(v.wageAmount()).isEqualTo(3_100_000L);
        assertThat(v.wageUnitCode()).isEqualTo("MONTHLY");
        assertThat(v.retireReason()).isEqualTo("자발적 퇴사(개인 사정)");
        assertThat(service.certificate("C001", v.certId())).isEqualTo(v);
    }

    @Test
    void 퇴직_예정자는_청구일_기준_계약의_임금을_쓴다() {
        LocalDate futureRetire = LocalDate.of(2026, 12, 31);
        emp(1, futureRetire, "VOLUNTARY", futureRetire.plusYears(3));
        contract(1, HIRE, 2_800_000L);
        contract(1, LocalDate.of(2026, 12, 1), 3_300_000L); // 청구일 뒤·퇴직일 전에 시작하는 계약은 아직 쓰지 않음
        CertificateView v = service.issueCertificate("C001", 1, new CertificateRequest(LocalDate.of(2026, 11, 17),
                EnumSet.of(CertificateItem.WAGE), null), "hr1");
        assertThat(v.wageAmount()).isEqualTo(2_800_000L);
    }

    @Test
    void 재직자는_사용기간_끝이_비고_퇴직사유를_요구하면_400() {
        emp(1, null, null, null);
        CertificateView v = service.issueCertificate("C001", 1, new CertificateRequest(LocalDate.of(2026, 11, 16),
                EnumSet.of(CertificateItem.USE_PERIOD), null), "hr1");
        assertThat(v.useTo()).isNull();
        assertStatus(() -> service.issueCertificate("C001", 1, new CertificateRequest(LocalDate.of(2026, 11, 16),
                EnumSet.of(CertificateItem.RETIRE_REASON), null), "hr1"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void 임금이_적힌_계약이_없으면_409() {
        emp(1, RETIRE, "VOLUNTARY", RETIRE.plusYears(1));
        contract(1, HIRE, null);
        assertStatus(() -> service.issueCertificate("C001", 1, new CertificateRequest(LocalDate.of(2026, 12, 1),
                EnumSet.of(CertificateItem.WAGE), null), "hr1"), HttpStatus.CONFLICT);
    }

    @Test
    void 없는_사원이나_증명서는_404() {
        assertStatus(() -> service.issueCertificate("C001", 9, new CertificateRequest(LocalDate.of(2026, 12, 1),
                EnumSet.of(CertificateItem.USE_PERIOD), null), "hr1"), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.certificate("C001", 99), HttpStatus.NOT_FOUND);
    }
}
