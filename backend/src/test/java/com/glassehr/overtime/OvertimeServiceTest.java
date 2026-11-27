package com.glassehr.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.approval.ApprovalException;
import com.glassehr.approval.ApprovalService;
import com.glassehr.attendance.WorkPolicyRules;
import com.glassehr.attendance.WorkPolicyService;
import com.glassehr.auth.AuthUser;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.overtime.OvertimeDtos.AuthRequest;
import com.glassehr.overtime.OvertimeDtos.CreateRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class OvertimeServiceTest {

    static final long EMP = 7L;
    static final String ENTER = "C001";
    /** 2099-03-10 10:00 서울 */
    static final Clock CLOCK = Clock.fixed(Instant.parse("2099-03-10T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    static final LocalDateTime EVENING = LocalDateTime.of(2099, 3, 10, 19, 0);

    OvertimeRequestRepository requests;
    SpecialOvertimeAuthRepository auths;
    ApprovalService approvals;
    EmployeeRepository employees;
    WorkPolicyService policies;
    OvertimeService service;
    AuthUser me;

    @BeforeEach
    void setUp() {
        requests = mock(OvertimeRequestRepository.class);
        auths = mock(SpecialOvertimeAuthRepository.class);
        approvals = mock(ApprovalService.class);
        employees = mock(EmployeeRepository.class);
        policies = mock(WorkPolicyService.class);
        when(policies.forEmployee(any(), anyLong(), any())).thenReturn(WorkPolicyRules.SYSTEM_DEFAULT);
        service = new OvertimeService(requests, auths, approvals, employees, policies, CLOCK);
        me = mock(AuthUser.class);
        when(me.enterCd()).thenReturn(ENTER);
        when(me.empId()).thenReturn(EMP);
        when(me.loginId()).thenReturn("emp7");
        when(employees.findForUpdate(ENTER, EMP)).thenReturn(Optional.of(mock(Employee.class)));
        when(requests.saveAndFlush(any())).thenAnswer(inv -> {
            OvertimeRequest r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "requestId", 11L);
            return r;
        });
        when(approvals.submit(eq(ENTER), eq("OVERTIME"), eq(11L), any(), eq(EMP))).thenReturn(99L);
    }

    static CreateRequest create(LocalDateTime start, LocalDateTime end, Integer breaks) {
        return new CreateRequest(OvertimeRequest.Kind.OVERTIME, start, end, breaks, " 출시 대응 ");
    }

    @Test
    void 신청을_저장하고_결재로_올린다() {
        var v = service.create(create(EVENING, EVENING.plusHours(3), 30), me);
        assertThat(v.requestId()).isEqualTo(11L);
        assertThat(v.approvalId()).isEqualTo(99L);
        assertThat(v.plannedMinutes()).isEqualTo(150);
        assertThat(v.workDate()).isEqualTo(LocalDate.of(2099, 3, 10));
        assertThat(v.status()).isEqualTo(OvertimeRequest.Status.IN_PROGRESS);
        assertThat(v.reason()).isEqualTo("출시 대응");
        verify(approvals).submit(ENTER, "OVERTIME", 11L, "연장근로 2099-03-10 19:00~22:00", EMP);
        verify(employees).findForUpdate(ENTER, EMP);
    }

    private static WorkPolicyRules.Policy overtimePolicy(WorkPolicyRules.Allowance overtime) {
        WorkPolicyRules.Policy d = WorkPolicyRules.SYSTEM_DEFAULT;
        return new WorkPolicyRules.Policy(overtime, d.night(), d.holiday(), true, 1, 0, WorkPolicyRules.Rounding.UP,
                0, 0, true, WorkPolicyRules.MissingOut.IN_ONLY, true);
    }

    @Test
    void 근무_정책이_연장_허용_안_함이면_신청을_거부한다() {
        when(policies.forEmployee(ENTER, EMP, LocalDate.of(2099, 3, 10)))
                .thenReturn(overtimePolicy(WorkPolicyRules.Allowance.DENY));
        assertThatThrownBy(() -> service.create(create(EVENING, EVENING.plusHours(2), 0), me))
                .isInstanceOfSatisfying(ApprovalException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("허용하지 않아요");
        verify(requests, never()).saveAndFlush(any());
        verify(approvals, never()).submit(any(), any(), anyLong(), any(), anyLong());
    }

    @Test
    void 근무_정책_주_한도를_넘는_신청은_거부한다() {
        when(policies.forEmployee(ENTER, EMP, LocalDate.of(2099, 3, 10))).thenReturn(overtimePolicy(
                new WorkPolicyRules.Allowance(WorkPolicyRules.AllowMode.LIMIT, null, 180, null)));
        OvertimeRequest prior = mock(OvertimeRequest.class);
        when(prior.getWorkKind()).thenReturn(OvertimeRequest.Kind.OVERTIME);
        when(prior.getWorkDate()).thenReturn(LocalDate.of(2099, 3, 9));
        when(prior.getPlannedMinutes()).thenReturn(120);
        when(requests.findByEnterCdAndEmpIdAndWorkDateBetweenAndStatusIn(
                eq(ENTER), eq(EMP), any(), any(), any())).thenReturn(List.of(prior));

        assertThatThrownBy(() -> service.create(create(EVENING, EVENING.plusHours(2), 0), me))
                .hasMessageContaining("한도");
        assertThat(service.create(create(EVENING, EVENING.plusHours(1), 0), me).plannedMinutes()).isEqualTo(60);
    }

    @Test
    void 사전_신청이_아니거나_시간이_맞지_않으면_거부한다() {
        assertThatThrownBy(() -> service.create(create(EVENING.minusHours(10), EVENING, 0), me))
                .hasMessageContaining("사전 신청");
        assertThatThrownBy(() -> service.create(create(EVENING, EVENING, 0), me)).hasMessageContaining("종료 시각");
        assertThatThrownBy(() -> service.create(create(EVENING, EVENING.plusHours(1), 60), me))
                .hasMessageContaining("휴게시간");
        verify(approvals, never()).submit(any(), any(), anyLong(), any(), anyLong());
    }

    @Test
    void 같은_시간대에_진행_중이거나_승인된_신청이_있으면_409() {
        when(requests.findOverlapping(eq(ENTER), eq(EMP), any(), any(), any()))
                .thenReturn(List.of(mock(OvertimeRequest.class)));
        assertThatThrownBy(() -> service.create(create(EVENING, EVENING.plusHours(2), 0), me))
                .isInstanceOfSatisfying(ApprovalException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 결재선이_없어_결재를_올리지_못하면_예외가_그대로_나가_신청도_되돌려진다() {
        doThrow(ApprovalException.rejected("결재선이 설정되지 않아")).when(approvals)
                .submit(any(), any(), anyLong(), any(), anyLong());
        assertThatThrownBy(() -> service.create(create(EVENING, EVENING.plusHours(2), 0), me))
                .isInstanceOfSatisfying(ApprovalException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void 특별연장근로_인가는_근로자_동의가_있어야_등록한다() {
        AuthRequest noConsent = new AuthRequest(SpecialOvertimeAuth.Kind.PRIOR, "A-1", "서울지방고용노동청",
                LocalDate.of(2099, 3, 1), LocalDate.of(2099, 3, 2), LocalDate.of(2099, 3, 8), "설비 고장", Set.of(EMP),
                false);
        assertThatThrownBy(() -> service.createAuth(noConsent, me)).hasMessageContaining("제53조 제4항");

        AuthRequest ok = new AuthRequest(SpecialOvertimeAuth.Kind.POST, " A-1 ", "서울지방고용노동청",
                LocalDate.of(2099, 3, 1), LocalDate.of(2099, 3, 2), LocalDate.of(2099, 3, 8), "설비 고장", Set.of(EMP),
                true);
        com.glassehr.employee.Employee target = mock(com.glassehr.employee.Employee.class);
        when(employees.findByEnterCdAndEmpIdIn(ENTER, Set.of(EMP))).thenReturn(List.of(target));
        when(auths.save(any())).thenAnswer(inv -> {
            SpecialOvertimeAuth saved = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "authId", 31L); // DB 시퀀스 부여 흉내
            return saved;
        });
        var v = service.createAuth(ok, me);
        assertThat(v.authNo()).isEqualTo("A-1");
        assertThat(v.empIds()).containsExactly(EMP);
        assertThat(v.createdBy()).isEqualTo("emp7");
    }

    @Test
    void 결재_결과를_신청_상태에_반영하고_승인_전에_한도를_검사한다() {
        OvertimeLimitChecker limits = mock(OvertimeLimitChecker.class);
        OvertimeApprovalHandler handler = new OvertimeApprovalHandler(requests, employees, limits, CLOCK);
        OvertimeRequest r = new OvertimeRequest(ENTER, EMP, OvertimeRequest.Kind.OVERTIME, EVENING,
                EVENING.plusHours(1), 0, "사유", EVENING.minusDays(1));
        when(requests.findForUpdate(ENTER, 11L)).thenReturn(Optional.of(r));
        com.glassehr.employee.Employee emp = mock(com.glassehr.employee.Employee.class);
        when(emp.getEnterCd()).thenReturn(ENTER);
        when(employees.findForUpdate(ENTER, EMP)).thenReturn(Optional.of(emp));

        doThrow(new ApprovalException(HttpStatus.UNPROCESSABLE_ENTITY, "한도")).when(limits).check(ENTER, r);
        assertThatThrownBy(() -> handler.onApproved(ENTER, 11L)).isInstanceOf(ApprovalException.class);
        assertThat(r.getStatus()).isEqualTo(OvertimeRequest.Status.IN_PROGRESS);
        verify(employees).findForUpdate(ENTER, EMP);
        verify(limits).check(ENTER, r);

        handler.onRejected(ENTER, 11L);
        assertThat(r.getStatus()).isEqualTo(OvertimeRequest.Status.REJECTED);
        assertThat(r.getDecidedAt()).isEqualTo(LocalDateTime.of(2099, 3, 10, 10, 0));
        assertThatThrownBy(() -> handler.onWithdrawn(ENTER, 11L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 다른_법인_결재_결과는_신청을_바꾸지_않는다() {
        OvertimeApprovalHandler handler = new OvertimeApprovalHandler(requests, employees,
                mock(OvertimeLimitChecker.class), CLOCK);
        OvertimeRequest r = new OvertimeRequest(ENTER, EMP, OvertimeRequest.Kind.OVERTIME, EVENING,
                EVENING.plusHours(1), 0, "사유", EVENING.minusDays(1));
        when(requests.findForUpdate(ENTER, 11L)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> handler.onApproved("C002", 11L)).isInstanceOf(IllegalStateException.class);
        assertThat(r.getStatus()).isEqualTo(OvertimeRequest.Status.IN_PROGRESS);
    }

    @Test
    void 결재_상세에_원본_신청_내용을_제공한다() {
        OvertimeApprovalHandler handler = new OvertimeApprovalHandler(requests, employees,
                mock(OvertimeLimitChecker.class), CLOCK);
        OvertimeRequest r = new OvertimeRequest(ENTER, EMP, OvertimeRequest.Kind.HOLIDAY, EVENING,
                EVENING.plusHours(3), 30, "긴급 점검", EVENING.minusDays(1));
        org.springframework.test.util.ReflectionTestUtils.setField(r, "requestId", 12L);
        when(requests.findByEnterCdAndRequestId(ENTER, 12L)).thenReturn(Optional.of(r));

        var detail = handler.requestDetail(ENTER, 12L);

        assertThat(detail.workKind()).isEqualTo(OvertimeRequest.Kind.HOLIDAY);
        assertThat(detail.plannedMinutes()).isEqualTo(150);
        assertThat(detail.breakMinutes()).isEqualTo(30);
        assertThat(detail.reason()).isEqualTo("긴급 점검");
    }

    @Test
    void enum_값이_V025_CHECK_제약과_같다() throws IOException {
        String s = new ClassPathResource("db/oracle/V025__overtime_request.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(s).contains("work_kind IN (" + quoted(OvertimeRequest.Kind.values()) + ")");
        assertThat(s).contains("status IN (" + quoted(OvertimeRequest.Status.values()) + ")");
        assertThat(s).contains("auth_kind IN (" + quoted(SpecialOvertimeAuth.Kind.values()) + ")");
        assertThat(s).doesNotContain("{");
    }

    static String quoted(Enum<?>[] values) {
        return String.join(", ", Arrays.stream(values).map(v -> "'" + v.name() + "'").toList());
    }
}
