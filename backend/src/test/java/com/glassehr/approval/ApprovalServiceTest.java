package com.glassehr.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.employee.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ApprovalServiceTest {

    static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(2026, 11, 19, 10, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    /** 결과를 기록하는 시험용 핸들러. */
    static class RecordingHandler implements ApprovalCompletionHandler {
        final List<String> events = new ArrayList<>();
        RuntimeException failOnApprove;

        @Override
        public String requestType() {
            return "TEST";
        }

        @Override
        public String label() {
            return "시험 신청";
        }

        @Override
        public void onApproved(String enterCd, long requestRef) {
            if (failOnApprove != null) {
                throw failOnApprove;
            }
            events.add("approved:" + requestRef);
        }

        @Override
        public void onRejected(String enterCd, long requestRef) {
            events.add("rejected:" + requestRef);
        }

        @Override
        public void onWithdrawn(String enterCd, long requestRef) {
            events.add("withdrawn:" + requestRef);
        }
    }

    static final String ENTER = "C001";

    final ApprovalTemplateRepository templates = mock(ApprovalTemplateRepository.class);
    final ApprovalDocRepository docs = mock(ApprovalDocRepository.class);
    final ApprovalActionLogRepository logs = mock(ApprovalActionLogRepository.class);
    final EmployeeRepository employees = mock(EmployeeRepository.class);
    final ApproverResolverTest.FakeDirectory dir = new ApproverResolverTest.FakeDirectory();
    final RecordingHandler handler = new RecordingHandler();
    final List<ApprovalActionLog> saved = new ArrayList<>();
    ApprovalService service;
    ApprovalDoc lastDoc;

    static AuthUser user(Long empId) {
        return new AuthUser("T001", ENTER, ENTER, "u" + empId, empId, Set.of("EMPLOYEE"), Set.of(Permission.EMP_READ_SELF), Set.of(), "ip");
    }

    @BeforeEach
    void setUp() {
        service = new ApprovalService(templates, docs, logs, employees, dir, List.of(handler), CLOCK);
        when(docs.save(any())).thenAnswer(inv -> {
            lastDoc = inv.getArgument(0);
            ReflectionTestUtils.setField(lastDoc, "approvalId", 900L);
            return lastDoc;
        });
        when(docs.findForUpdate(ENTER, 900L)).thenAnswer(inv -> Optional.ofNullable(lastDoc));
        when(logs.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    void template(ApprovalTemplate.Step... steps) {
        ApprovalTemplate t = new ApprovalTemplate(ENTER, "TEST", TODAY.minusDays(10), List.of(steps), "admin");
        ReflectionTestUtils.setField(t, "templateId", 1L);
        when(templates.findEffective(ENTER, "TEST", TODAY)).thenReturn(List.of(t));
    }

    void twoSteps() {
        template(new ApprovalTemplate.Step(1, ApproverKind.DEPT_HEAD, null, true),
                new ApprovalTemplate.Step(2, ApproverKind.ROLE, "HR", false));
    }

    @Test
    void 순차로_승인되고_마지막_승인에_핸들러가_불린다() {
        twoSteps();
        long id = service.submit(ENTER, "TEST", 55, "연장근로 신청", 5);
        assertThat(id).isEqualTo(900);
        assertThat(lastDoc.getCurrentStepNo()).isEqualTo(1);

        // 2단계 결재자가 먼저 처리하려 하면 거부
        assertThatThrownBy(() -> service.approve(900, user(100L), null))
                .isInstanceOf(ApprovalException.class)
                .satisfies(e -> assertThat(((ApprovalException) e).status()).isEqualTo(HttpStatus.FORBIDDEN));

        service.approve(900, user(7L), "확인");
        assertThat(lastDoc.getCurrentStepNo()).isEqualTo(2);
        assertThat(handler.events).isEmpty();

        service.approve(900, user(101L), null);
        assertThat(lastDoc.getStatus()).isEqualTo(ApprovalDoc.Status.APPROVED);
        assertThat(lastDoc.getCompletedAt()).isNotNull();
        assertThat(handler.events).containsExactly("approved:55");
        assertThat(saved).extracting(ApprovalActionLog::getAction).containsExactly(
                ApprovalActionLog.Action.SUBMIT, ApprovalActionLog.Action.APPROVE, ApprovalActionLog.Action.APPROVE);
        assertThat(saved.get(2).getActorEmpId()).isEqualTo(101L);

        // 끝난 문서는 다시 처리할 수 없다
        assertThatThrownBy(() -> service.approve(900, user(100L), null))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("이미 끝난");
    }

    @Test
    void 반려하면_문서_전체가_반려되고_다음_단계는_처리할_수_없다() {
        twoSteps();
        service.submit(ENTER, "TEST", 55, "신청", 5);
        assertThatThrownBy(() -> service.reject(900, user(7L), " "))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("반려 사유");
        service.reject(900, user(7L), "일정 조정 필요");
        assertThat(lastDoc.getStatus()).isEqualTo(ApprovalDoc.Status.REJECTED);
        assertThat(lastDoc.getCurrentStepNo()).isNull();
        assertThat(lastDoc.getSteps().get(1).getStepStatus()).isEqualTo(ApprovalDocStep.Status.WAITING);
        assertThat(handler.events).containsExactly("rejected:55");
        assertThatThrownBy(() -> service.approve(900, user(100L), null)).isInstanceOf(ApprovalException.class);
    }

    @Test
    void 회수는_첫_승인_전_신청자만_할_수_있다() {
        twoSteps();
        service.submit(ENTER, "TEST", 55, "신청", 5);
        assertThatThrownBy(() -> service.withdraw(900, user(7L)))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("본인");
        service.withdraw(900, user(5L));
        assertThat(lastDoc.getStatus()).isEqualTo(ApprovalDoc.Status.WITHDRAWN);
        assertThat(handler.events).containsExactly("withdrawn:55");
    }

    @Test
    void 첫_승인_뒤에는_회수할_수_없다() {
        twoSteps();
        service.submit(ENTER, "TEST", 55, "신청", 5);
        service.approve(900, user(7L), null);
        assertThatThrownBy(() -> service.withdraw(900, user(5L)))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("이미 승인한");
    }

    @Test
    void 신청자가_부서장이면_1단계를_건너뛰고_이력에_남긴다() {
        twoSteps();
        service.submit(ENTER, "TEST", 56, "신청", 7);
        assertThat(lastDoc.getCurrentStepNo()).isEqualTo(2);
        assertThat(lastDoc.getSteps().get(0).getStepStatus()).isEqualTo(ApprovalDocStep.Status.SKIPPED);
        assertThat(saved).extracting(ApprovalActionLog::getAction)
                .containsExactly(ApprovalActionLog.Action.SUBMIT, ApprovalActionLog.Action.SKIP);
    }

    @Test
    void 모든_단계를_건너뛰면_바로_승인된다() {
        template(new ApprovalTemplate.Step(1, ApproverKind.DEPT_HEAD, null, true));
        service.submit(ENTER, "TEST", 57, "신청", 7);
        assertThat(lastDoc.getStatus()).isEqualTo(ApprovalDoc.Status.APPROVED);
        assertThat(handler.events).containsExactly("approved:57");
    }

    @Test
    void 결재선_템플릿이_없으면_신청을_거부한다() {
        when(templates.findEffective(ENTER, "TEST", TODAY)).thenReturn(List.of());
        assertThatThrownBy(() -> service.submit(ENTER, "TEST", 55, "신청", 5))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("결재선이 설정되지 않아")
                .satisfies(e -> assertThat(((ApprovalException) e).status())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(docs, never()).save(any());
    }

    @Test
    void 핸들러가_없는_유형은_올릴_수_없다() {
        assertThatThrownBy(() -> service.submit(ENTER, "NOPE", 1, "신청", 5)).isInstanceOf(ApprovalException.class);
    }

    @Test
    void 결재_후보가_아니면_상세를_볼_수_없다() {
        twoSteps();
        service.submit(ENTER, "TEST", 55, "신청", 5);
        when(docs.findByEnterCdAndApprovalId(ENTER, 900L)).thenReturn(Optional.of(lastDoc));
        assertThat(service.get(900, user(101L)).canAct()).isFalse();
        assertThat(service.get(900, user(7L)).canAct()).isTrue();
        assertThat(service.get(900, user(5L)).canWithdraw()).isTrue();
        assertThatThrownBy(() -> service.get(900, user(999L)))
                .isInstanceOf(ApprovalException.class).hasMessageContaining("권한");
    }

    static AuthUser otherEnterUser(Long empId) {
        return new AuthUser("T001", "C002", "C002", "u" + empId, empId, Set.of("EMPLOYEE"),
                Set.of(Permission.EMP_READ_ALL), Set.of(), "ip");
    }

    @Test
    void 다른_법인_문서는_상세_조회도_승인도_404다() {
        twoSteps();
        service.submit(ENTER, "TEST", 55, "신청", 5);
        when(docs.findByEnterCdAndApprovalId(ENTER, 900L)).thenReturn(Optional.of(lastDoc));
        assertThatThrownBy(() -> service.get(900, otherEnterUser(7L)))
                .isInstanceOf(ApprovalException.class)
                .satisfies(e -> assertThat(((ApprovalException) e).status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.approve(900, otherEnterUser(7L), null))
                .isInstanceOf(ApprovalException.class)
                .satisfies(e -> assertThat(((ApprovalException) e).status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(lastDoc.getCurrentStepNo()).isEqualTo(1);
        assertThat(handler.events).isEmpty();
    }

    @Test
    void 핸들러가_승인을_거부하면_예외가_그대로_올라간다() {
        template(new ApprovalTemplate.Step(1, ApproverKind.DEPT_HEAD, null, false));
        handler.failOnApprove = ApprovalException.rejected("한도 초과");
        service.submit(ENTER, "TEST", 58, "신청", 5);
        assertThatThrownBy(() -> service.approve(900, user(7L), null)).hasMessage("한도 초과");
    }

    @Test
    void 같은_유형_핸들러가_둘이면_시작하지_않는다() {
        assertThatThrownBy(() -> ApprovalService.byType(List.of(handler, new RecordingHandler())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 사원_연결이_없는_계정은_결재함을_쓸_수_없다() {
        assertThatThrownBy(() -> service.inbox(user(null))).isInstanceOf(ApprovalException.class);
        verify(docs, never()).findInbox(anyString(), anyLong());
    }

    @Test
    void 결재함은_지금_내_차례인_문서를_요약한다() {
        twoSteps();
        service.submit(ENTER, "TEST", 55, "신청", 5);
        when(docs.findInbox(ENTER, 7L)).thenReturn(List.of(lastDoc));
        assertThat(service.inbox(user(7L))).singleElement().satisfies(s -> {
            assertThat(s.requestTypeLabel()).isEqualTo("시험 신청");
            assertThat(s.stepCount()).isEqualTo(2);
            assertThat(s.requester().name()).isEqualTo("사원 5");
        });
        verify(templates, never()).findEffective(anyString(), eq("X"), any());
        verify(docs, never()).findByEnterCdAndRequestTypeAndRequestRef(anyString(), anyString(), anyLong());
    }
}
