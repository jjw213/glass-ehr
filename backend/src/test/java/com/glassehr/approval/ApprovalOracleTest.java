package com.glassehr.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.approval.ApprovalDtos.DocDetail;
import com.glassehr.approval.ApprovalDtos.DocSummary;
import com.glassehr.approval.ApprovalDtos.LogView;
import com.glassehr.approval.ApprovalDtos.TemplateRequest;
import com.glassehr.approval.ApprovalDtos.TemplateStepRequest;
import com.glassehr.auth.AuthUser;
import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeService;
import com.glassehr.employee.RequestActor;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 V024 공통 결재(#113)를 검증한다: 템플릿 유효기간 조회, 결재선 스냅샷, 순차 승인·반려·회수,
 * 처리 이력(추가만), 결재함 쿼리, 결재자·신청자 권한. 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다.
 * 모듈 핸들러 대신 시험용 신청 유형 {@code IT_SAMPLE}을 쓴다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class ApprovalOracleTest {

    private static final String TYPE = "IT_SAMPLE";
    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1");

    /** 핸들러 호출 기록(테스트마다 비운다). */
    static final List<String> CALLS = new ArrayList<>();

    @TestConfiguration
    static class SampleHandlerConfig {
        @Bean
        ApprovalCompletionHandler itSampleHandler() {
            return new ApprovalCompletionHandler() {
                @Override
                public String requestType() {
                    return TYPE;
                }

                @Override
                public String label() {
                    return "시험 신청";
                }

                @Override
                public void onApproved(long ref) {
                    CALLS.add("approved:" + ref);
                }

                @Override
                public void onRejected(long ref) {
                    CALLS.add("rejected:" + ref);
                }

                @Override
                public void onWithdrawn(long ref) {
                    CALLS.add("withdrawn:" + ref);
                }
            };
        }
    }

    @Autowired
    private ApprovalService service;

    @Autowired
    private ApprovalTemplateService templates;

    @Autowired
    private EmployeeService employees;

    @Autowired
    private JdbcTemplate jdbc;

    private long requester;
    private long first;
    private long second;
    private long outsider;

    @BeforeEach
    void setUp() {
        CALLS.clear();
        requester = employee("예시사원결재가");
        first = employee("예시사원결재나");
        second = employee("예시사원결재다");
        outsider = employee("예시사원결재라");
    }

    private long employee(String name) {
        return employees.create(new EmployeeCreateRequest("S01", name, "F", LocalDate.of(1880, 1, 1),
                "가상시 예시구 샘플로 0", null, "시험", null, LocalDate.of(1903, 1, 1), null, null, null, "REGULAR", 2400,
                null, null, null, null, "STAFF", null, null), HR).empId();
    }

    private static AuthUser as(long empId) {
        return new AuthUser("C001", "C001", "it-" + empId, empId, Set.of("EMPLOYEE"), Set.of(), Set.of(), "127.0.0.1");
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    private void template(LocalDate from, long... approvers) {
        List<TemplateStepRequest> steps = new ArrayList<>();
        for (long a : approvers) {
            steps.add(new TemplateStepRequest(ApproverKind.EMPLOYEE, Long.toString(a), false));
        }
        templates.create(new TemplateRequest(TYPE, from, steps), as(outsider));
    }

    private static void assertStatus(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApprovalException.class,
                e -> assertThat(e.status()).isEqualTo(status));
    }

    private static List<Long> ids(List<DocSummary> list) {
        return list.stream().map(DocSummary::approvalId).toList();
    }

    private List<String> logActions(long id) {
        return jdbc.queryForList("SELECT action FROM approval_action_log WHERE approval_id = ? ORDER BY action_id",
                String.class, id);
    }

    @Test
    void 템플릿이_없으면_신청_거부() {
        assertStatus(() -> service.submit(TYPE, 1, "결재선 없음", requester), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void 순차_승인_후_최종_승인에_핸들러가_한_번_불리고_이력이_쌓인다() {
        template(today(), first, second);
        long id = service.submit(TYPE, 101, "시험 문서", requester);

        assertThat(ids(service.inbox(as(first)))).contains(id);
        assertThat(ids(service.inbox(as(second)))).doesNotContain(id);
        assertThat(ids(service.mine(as(requester)))).contains(id);
        // 2단계 결재자가 먼저 처리할 수 없다
        assertStatus(() -> service.approve(id, as(second), null), HttpStatus.FORBIDDEN);

        DocDetail d1 = service.approve(id, as(first), "확인");
        assertThat(d1.summary().status()).isEqualTo(ApprovalDoc.Status.IN_PROGRESS);
        assertThat(d1.summary().currentStepNo()).isEqualTo(2);
        assertThat(CALLS).isEmpty();
        assertThat(ids(service.inbox(as(first)))).doesNotContain(id);
        assertThat(ids(service.inbox(as(second)))).contains(id);
        assertThat(ids(service.processed(as(first)))).contains(id);
        // 첫 승인 뒤에는 회수할 수 없다
        assertStatus(() -> service.withdraw(id, as(requester)), HttpStatus.CONFLICT);

        DocDetail d2 = service.approve(id, as(second), null);
        assertThat(d2.summary().status()).isEqualTo(ApprovalDoc.Status.APPROVED);
        assertThat(d2.summary().completedAt()).isNotNull();
        assertThat(CALLS).containsExactly("approved:101");
        assertThat(d2.history()).extracting(LogView::action).containsExactly(ApprovalActionLog.Action.SUBMIT,
                ApprovalActionLog.Action.APPROVE, ApprovalActionLog.Action.APPROVE);
        assertThat(logActions(id)).containsExactly("SUBMIT", "APPROVE", "APPROVE");
        // 끝난 문서는 다시 처리할 수 없다
        assertStatus(() -> service.approve(id, as(second), null), HttpStatus.CONFLICT);
    }

    @Test
    void 반려는_사유가_필수이고_문서_전체가_반려된다() {
        template(today(), first, second);
        long id = service.submit(TYPE, 102, "반려 문서", requester);

        assertStatus(() -> service.reject(id, as(first), "  "), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.reject(id, as(outsider), "남의 문서"), HttpStatus.FORBIDDEN);
        DocDetail d = service.reject(id, as(first), "  사유 부족  ");
        assertThat(d.summary().status()).isEqualTo(ApprovalDoc.Status.REJECTED);
        assertThat(d.history().get(d.history().size() - 1).comment()).isEqualTo("사유 부족");
        assertThat(CALLS).containsExactly("rejected:102");
        assertStatus(() -> service.approve(id, as(second), null), HttpStatus.CONFLICT);
        assertThat(ids(service.inbox(as(second)))).doesNotContain(id);
        assertThat(jdbc.queryForObject("SELECT step_status FROM approval_doc_step WHERE approval_id = ? AND step_no = 1",
                String.class, id)).isEqualTo("REJECTED");
    }

    @Test
    void 회수는_신청자만_첫_결재_전에() {
        template(today(), first);
        long id = service.submit(TYPE, 103, "회수 문서", requester);

        assertStatus(() -> service.withdraw(id, as(first)), HttpStatus.FORBIDDEN);
        DocDetail d = service.withdraw(id, as(requester));
        assertThat(d.summary().status()).isEqualTo(ApprovalDoc.Status.WITHDRAWN);
        assertThat(CALLS).containsExactly("withdrawn:103");
        assertThat(logActions(id)).containsExactly("SUBMIT", "WITHDRAW");
        assertThat(ids(service.inbox(as(first)))).doesNotContain(id);
    }

    @Test
    void 상세는_관련자만_볼_수_있다() {
        template(today(), first);
        long id = service.submit(TYPE, 104, "상세 문서", requester);

        assertThat(service.get(id, as(requester)).canWithdraw()).isTrue();
        assertThat(service.get(id, as(first)).canAct()).isTrue();
        assertStatus(() -> service.get(id, as(outsider)), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.approve(id, as(outsider), null), HttpStatus.FORBIDDEN);
    }

    @Test
    void 신청일_기준_템플릿을_쓰고_진행_중_문서는_스냅샷을_유지한다() {
        // 어제까지 유효한 옛 템플릿과 오늘부터 유효한 새 템플릿(유효기간 [from, to))
        jdbc.update("INSERT INTO approval_template (request_type, valid_from, valid_to, created_by) "
                + "VALUES (?, ?, ?, 'it')", TYPE, today().minusDays(10), today());
        Long oldId = jdbc.queryForObject("SELECT template_id FROM approval_template WHERE request_type = ?",
                Long.class, TYPE);
        jdbc.update("INSERT INTO approval_template_step (template_id, step_no, approver_kind, approver_ref, "
                + "skip_if_self) VALUES (?, 1, 'EMPLOYEE', ?, 'N')", oldId, Long.toString(outsider));
        template(today(), first);

        long id = service.submit(TYPE, 105, "스냅샷 문서", requester);
        assertThat(ids(service.inbox(as(first)))).contains(id);
        assertThat(ids(service.inbox(as(outsider)))).doesNotContain(id);

        // 내일부터 결재자를 바꿔도 이미 올린 문서의 결재자는 그대로
        template(today().plusDays(1), second);
        assertThat(ids(service.inbox(as(first)))).contains(id);
        assertThat(ids(service.inbox(as(second)))).doesNotContain(id);
        assertThat(service.approve(id, as(first), null).summary().status()).isEqualTo(ApprovalDoc.Status.APPROVED);
    }

    @Test
    void 처리_이력은_추가만_하고_같은_신청에_결재_문서는_하나() {
        template(today(), first);
        long id = service.submit(TYPE, 106, "이력 문서", requester);
        service.approve(id, as(first), "승인");

        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM approval_action_log WHERE approval_id = ?",
                Integer.class, id);
        service.get(id, as(requester)); // 조회는 이력을 바꾸지 않는다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_action_log WHERE approval_id = ?",
                Integer.class, id)).isEqualTo(before);
        assertThat(service.findByRequest(TYPE, 106).getApprovalId()).isEqualTo(id);
        // uq_approval_doc_ref: 같은 원본 신청으로 두 번 올릴 수 없다
        assertThatThrownBy(() -> service.submit(TYPE, 106, "중복", requester)).isNotNull();
    }
}
