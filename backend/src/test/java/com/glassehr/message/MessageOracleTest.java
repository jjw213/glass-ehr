package com.glassehr.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.auth.AuthUser;
import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeService;
import com.glassehr.employee.RequestActor;
import com.glassehr.message.MessageDtos.MessageView;
import com.glassehr.message.MessageDtos.ThreadSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 V030 과 메시지 쿼리(안 읽은 수·페이지·읽음)·참여자 권한을 검증한다. 실행 조건: {@code ORACLE_IT=true}.
 * 각 테스트는 롤백한다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class MessageOracleTest {

    private static final RequestActor HR = new RequestActor("it-hr", RequestActor.Role.HR, "127.0.0.1", "C001");

    @Autowired
    private MessageService service;

    @Autowired
    private EmployeeService employees;

    @Autowired
    private JdbcTemplate jdbc;

    private long employee(String name) {
        return employees.create("C001", new EmployeeCreateRequest("S01", name, "F", LocalDate.of(1880, 1, 1),
                "가상시 예시구 샘플로 0", null, "시험", null, LocalDate.of(1903, 1, 1), null, null, null, "REGULAR", 2400,
                null, null, null, null, "STAFF", null, null), HR).empId();
    }

    private static AuthUser as(long empId) {
        return new AuthUser("T001", "C001", "C001", "it-" + empId, empId, Set.of("EMPLOYEE"), Set.of(), Set.of(), "127.0.0.1");
    }

    private long thread(long... members) {
        jdbc.update("INSERT INTO msg_thread (enter_cd, created_at) VALUES ('C001', SYSTIMESTAMP)");
        Long id = jdbc.queryForObject("SELECT MAX(thread_id) FROM msg_thread", Long.class);
        for (long m : members) {
            jdbc.update("INSERT INTO msg_thread_member (enter_cd, thread_id, emp_id) VALUES ('C001', ?, ?)", id, m);
        }
        return id;
    }

    private ThreadSummary summary(long empId, long threadId) {
        return service.threads(as(empId)).stream().filter(t -> t.id() == threadId).findFirst().orElseThrow();
    }

    @Test
    void 안_읽은_수는_남이_보낸_메시지_중_내가_읽지_않은_것() {
        long a = employee("예시사원IT가");
        long b = employee("예시사원IT나");
        long c = employee("예시사원IT다");
        long t = thread(a, b, c);

        service.send(as(a), t, "첫 메시지");
        service.send(as(a), t, "둘째");
        service.send(as(b), t, "답장");

        assertThat(summary(a, t).unread()).isEqualTo(1);
        assertThat(summary(b, t).unread()).isEqualTo(2);
        assertThat(summary(c, t).unread()).isEqualTo(3);
        assertThat(summary(c, t).preview()).isEqualTo("답장");
        assertThat(summary(c, t).members()).extracting(MessageDtos.Member::empId).containsExactly(a, b);

        service.markRead(as(b), t);
        assertThat(summary(b, t).unread()).isZero();
        assertThat(summary(c, t).unread()).isEqualTo(3); // 남의 읽음은 내 수에 영향 없음
        service.markRead(as(b), t); // 두 번 눌러도 오류 없음
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM msg_read WHERE thread_id = ? AND emp_id = ?",
                Integer.class, t, b)).isEqualTo(2);

        service.send(as(a), t, "읽은 뒤 새 메시지");
        assertThat(summary(b, t).unread()).isEqualTo(1);
    }

    @Test
    void 참여자가_아니면_403_없는_대화방은_404() {
        long a = employee("예시사원IT라");
        long b = employee("예시사원IT마");
        long outsider = employee("예시사원IT바");
        long t = thread(a, b);
        service.send(as(a), t, "비밀");

        assertThat(service.threads(as(outsider))).noneMatch(s -> s.id() == t);
        for (Runnable r : List.<Runnable>of(
                () -> service.messages(as(outsider), t, null, null),
                () -> service.send(as(outsider), t, "끼어들기"),
                () -> service.markRead(as(outsider), t))) {
            assertThatThrownBy(r::run).isInstanceOfSatisfying(MessageException.class,
                    e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        assertThat(summary(b, t).unread()).isEqualTo(1);
        Long missing = jdbc.queryForObject("SELECT NVL(MAX(thread_id), 0) + 1000 FROM msg_thread", Long.class);
        assertThatThrownBy(() -> service.messages(as(a), missing, null, null)).isInstanceOfSatisfying(
                MessageException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 메시지는_페이지로_오래된_것부터_1000자_본문도_저장된다() {
        long a = employee("예시사원IT사");
        long b = employee("예시사원IT아");
        long t = thread(a, b);
        for (int i = 1; i <= 5; i++) {
            service.send(as(i % 2 == 0 ? b : a), t, "메시지" + i);
        }
        var last = service.messages(as(a), t, null, 2);
        assertThat(last.messages()).extracting(MessageView::text).containsExactly("메시지4", "메시지5");
        assertThat(last.messages()).extracting(MessageView::mine).containsExactly(false, true);
        assertThat(last.hasMore()).isTrue();
        var prev = service.messages(as(a), t, last.messages().get(0).id(), 10);
        assertThat(prev.messages()).extracting(MessageView::text).containsExactly("메시지1", "메시지2", "메시지3");
        assertThat(prev.hasMore()).isFalse();

        String max = "가".repeat(1000);
        assertThat(service.send(as(a), t, max).text()).hasSize(1000);
        assertThat(summary(b, t).preview()).isEqualTo(max);
    }
}
