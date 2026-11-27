package com.glassehr.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.approval.ApprovalDtos.TemplateRequest;
import com.glassehr.approval.ApprovalDtos.TemplateStepRequest;
import com.glassehr.approval.ApprovalDtos.TemplateView;
import com.glassehr.auth.AuthUser;
import com.glassehr.employee.EmployeeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApprovalTemplateServiceTest {

    final ApprovalTemplateRepository templates = mock(ApprovalTemplateRepository.class);
    final EmployeeRepository employees = mock(EmployeeRepository.class);
    final ApprovalService approvals = mock(ApprovalService.class);
    final ApprovalTemplateService service =
            new ApprovalTemplateService(templates, employees, approvals, ApprovalServiceTest.CLOCK);
    final AuthUser admin = new AuthUser("C001", "C001", "hr1", 100L, Set.of("HR"), Set.of(), Set.of(), "ip");
    static final LocalDate TODAY = ApprovalServiceTest.TODAY;

    @BeforeEach
    void setUp() {
        when(approvals.isKnownType("TEST")).thenReturn(true);
        when(templates.saveAndFlush(any())).thenAnswer(inv -> {
            ApprovalTemplate t = inv.getArgument(0);
            if (t.getTemplateId() == null) {
                ReflectionTestUtils.setField(t, "templateId", 2L);
            }
            return t;
        });
    }

    static TemplateRequest req(LocalDate from, TemplateStepRequest... steps) {
        return new TemplateRequest("TEST", from, List.of(steps));
    }

    static TemplateStepRequest s(ApproverKind k, String ref) {
        return new TemplateStepRequest(k, ref, false);
    }

    @Test
    void 새_템플릿은_앞_템플릿을_전날까지로_닫고_단계번호를_1부터_매긴다() {
        ApprovalTemplate prev = new ApprovalTemplate("C001", "TEST", TODAY.minusDays(30), List.of(), "hr1");
        when(templates.lockByRequestType("C001", "TEST")).thenReturn(List.of(prev));
        TemplateView v = service.create(req(TODAY.plusDays(1), s(ApproverKind.DEPT_HEAD, null),
                s(ApproverKind.ROLE, "HR")), admin);
        assertThat(prev.getValidTo()).isEqualTo(TODAY.plusDays(1));
        assertThat(v.validFrom()).isEqualTo(TODAY.plusDays(1));
        assertThat(v.validTo()).isNull();
        assertThat(v.steps()).extracting(ApprovalDtos.TemplateStepView::stepNo).containsExactly(1, 2);
        assertThat(v.createdBy()).isEqualTo("hr1");
    }

    @Test
    void 지난_날짜나_앞_템플릿보다_이른_시작일은_거부한다() {
        assertThatThrownBy(() -> service.create(req(TODAY.minusDays(1), s(ApproverKind.DEPT_HEAD, null)), admin))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("오늘");
        ApprovalTemplate prev = new ApprovalTemplate("C001", "TEST", TODAY.plusDays(5), List.of(), "hr1");
        when(templates.lockByRequestType("C001", "TEST")).thenReturn(List.of(prev));
        assertThatThrownBy(() -> service.create(req(TODAY.plusDays(5), s(ApproverKind.DEPT_HEAD, null)), admin))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("늦어야");
        assertThat(prev.getValidTo()).isNull();
    }

    @Test
    void 지정_방식에_맞지_않는_대상값은_거부한다() {
        when(templates.lockByRequestType("C001", "TEST")).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(req(TODAY, s(ApproverKind.DEPT_HEAD, "X")), admin))
                .hasMessageContaining("넣지 않아요");
        assertThatThrownBy(() -> service.create(req(TODAY, s(ApproverKind.POSITION, " ")), admin))
                .hasMessageContaining("넣어 주세요");
        assertThatThrownBy(() -> service.create(req(TODAY, s(ApproverKind.ROLE, "DEPT_HEAD")), admin))
                .hasMessageContaining("역할은");
        assertThatThrownBy(() -> service.create(req(TODAY, s(ApproverKind.EMPLOYEE, "abc")), admin))
                .hasMessageContaining("숫자");
        when(employees.findByEnterCdAndEmpId("C001", 42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(req(TODAY, s(ApproverKind.EMPLOYEE, "42")), admin))
                .hasMessageContaining("찾을 수 없어요");
        verify(templates, never()).saveAndFlush(any());
    }

    @Test
    void 알_수_없는_신청_유형은_거부한다() {
        assertThatThrownBy(() -> service.list("C001", "NOPE")).isInstanceOf(ApprovalException.class);
        assertThatThrownBy(() -> service.create(new TemplateRequest("NOPE", TODAY,
                List.of(s(ApproverKind.DEPT_HEAD, null))), admin)).isInstanceOf(ApprovalException.class);
    }
}
