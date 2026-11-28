package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.auth.AuthUser;
import com.glassehr.message.MessageDtos.ThreadSummary;
import com.glassehr.message.MessageException;
import com.glassehr.message.MessageService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V040(#128): 사내 메시지의 법인 분리를 Oracle 실DB에서 검증한다.
 * "법인 A(C001) 세션으로 법인 B(ZZIT1) 대화방 조회·쓰기·읽음 → 404", 복합 PK·FK 로 다른 법인 사원과 대화방을 만들 수 없음을 본다
 * (docs/tenant-design.md 3.2). 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다. 1900년대 날짜, ZZIT 코드만 쓴다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class MessageTenantOracleTest {

    private static final String A = "C001";
    private static final String B = "ZZIT1";

    @Autowired
    private MessageService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long aEmp1;
    private long aEmp2;
    private long bEmp1;
    private long bEmp2;
    private long bThread;

    @BeforeEach
    void 법인_B와_B의_대화방을_넣는다() {
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order, tenant_id)"
                + " VALUES (?, 'IT 법인 가(예시)', '0000000093', 'Y', 0, 'T001')", B);
        jdbc.update("INSERT INTO biz_site (enter_cd, site_code, site_name) VALUES (?, 'S01', 'B 본사(예시)')", B);
        aEmp1 = insertEmployee(A, "S01-1903-911");
        aEmp2 = insertEmployee(A, "S01-1903-912");
        bEmp1 = insertEmployee(B, "S01-1903-913");
        bEmp2 = insertEmployee(B, "S01-1903-914");
        bThread = thread(B, bEmp1, bEmp2);
        jdbc.update("INSERT INTO msg_message (enter_cd, thread_id, sender_emp_id, body, sent_at)"
                + " VALUES (?, ?, ?, '법인 B 메시지', SYSTIMESTAMP)", B, bThread, bEmp1);
    }

    @Test
    void 법인_A_세션은_법인_B_대화방을_보지_못하고_읽기_쓰기_읽음은_404() {
        AuthUser a = as(A, aEmp1);
        assertThat(service.threads(a)).extracting(ThreadSummary::id).doesNotContain(bThread);
        for (Runnable r : List.<Runnable>of(
                () -> service.messages(a, bThread, null, null),
                () -> service.send(a, bThread, "끼어들기"),
                () -> service.markRead(a, bThread))) {
            assertThatThrownBy(r::run).isInstanceOfSatisfying(MessageException.class,
                    e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM msg_message WHERE thread_id = ?", Integer.class, bThread))
                .isEqualTo(1);

        // 세션 법인이 맞지 않으면 B 사원 번호여도 B 대화방을 찾지 않는다.
        assertThatThrownBy(() -> service.messages(as(A, bEmp2), bThread, null, null))
                .isInstanceOfSatisfying(MessageException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.threads(as(A, bEmp2))).isEmpty();

        // 법인 B 세션의 참여자는 그대로 본다.
        AuthUser b = as(B, bEmp2);
        assertThat(service.threads(b)).singleElement().satisfies(t -> {
            assertThat(t.id()).isEqualTo(bThread);
            assertThat(t.unread()).isEqualTo(1);
        });
        service.markRead(b, bThread);
        assertThat(service.threads(b).get(0).unread()).isZero();
        assertThat(jdbc.queryForObject("SELECT enter_cd FROM msg_read WHERE thread_id = ? AND emp_id = ?",
                String.class, bThread, bEmp2)).isEqualTo(B);
        assertThat(service.send(b, bThread, "답장").mine()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM msg_message WHERE enter_cd = ? AND thread_id = ?",
                Integer.class, B, bThread)).isEqualTo(2);
    }

    @Test
    void DB_복합_PK_FK는_다른_법인_사원과_대화방을_만들지_못하게_한다() {
        long aThread = thread(A, aEmp1, aEmp2);
        jdbc.update("INSERT INTO msg_message (enter_cd, thread_id, sender_emp_id, body, sent_at)"
                + " VALUES (?, ?, ?, '법인 A 메시지', SYSTIMESTAMP)", A, aThread, aEmp1);
        long aMessage = jdbc.queryForObject("SELECT MAX(message_id) FROM msg_message WHERE thread_id = ?", Long.class,
                aThread);

        // 법인 A 대화방에 법인 B 사원을 넣을 수 없다(어느 법인으로 적어도).
        assertCrossRejected("INSERT INTO msg_thread_member (enter_cd, thread_id, emp_id) VALUES (?, ?, ?)",
                A, aThread, bEmp1);
        assertCrossRejected("INSERT INTO msg_thread_member (enter_cd, thread_id, emp_id) VALUES (?, ?, ?)",
                B, aThread, bEmp1);
        // 법인 B 대화방에 법인 A 사원을 넣을 수 없다.
        assertCrossRejected("INSERT INTO msg_thread_member (enter_cd, thread_id, emp_id) VALUES (?, ?, ?)",
                A, bThread, aEmp1);
        // 다른 법인 대화방에 메시지를 쓸 수 없다.
        assertCrossRejected("INSERT INTO msg_message (enter_cd, thread_id, sender_emp_id, body, sent_at)"
                + " VALUES (?, ?, ?, '교차', SYSTIMESTAMP)", A, bThread, bEmp1);
        // 다른 법인 메시지를 읽음 처리할 수 없다.
        assertCrossRejected("INSERT INTO msg_read (enter_cd, message_id, thread_id, emp_id, read_at)"
                + " VALUES (?, ?, ?, ?, SYSTIMESTAMP)", B, aMessage, bThread, bEmp2);
        // 법인을 빠뜨린 대화방은 DEFAULT 가 없어 거부한다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO msg_thread (created_at) VALUES (SYSTIMESTAMP)"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM msg_thread_member WHERE thread_id = ?",
                Integer.class, aThread)).isEqualTo(2);
    }

    private void assertCrossRejected(String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private long thread(String enterCd, long... members) {
        jdbc.update("INSERT INTO msg_thread (enter_cd, created_at) VALUES (?, SYSTIMESTAMP)", enterCd);
        Long id = jdbc.queryForObject("SELECT MAX(thread_id) FROM msg_thread WHERE enter_cd = ?", Long.class, enterCd);
        for (long m : members) {
            jdbc.update("INSERT INTO msg_thread_member (enter_cd, thread_id, emp_id) VALUES (?, ?, ?)",
                    enterCd, id, m);
        }
        return id;
    }

    private long insertEmployee(String enterCd, String empNo) {
        jdbc.update("INSERT INTO employee (enter_cd, emp_no, site_code, emp_name, gender, birth_date, address,"
                + " job_description, hire_date, employment_type_code) VALUES (?, ?, 'S01', '예시사원메시지', 'M',"
                + " DATE '1880-01-01', '가상시 예시구 샘플로 0', '시험', DATE '1903-01-01', 'REGULAR')", enterCd, empNo);
        return jdbc.queryForObject("SELECT emp_id FROM employee WHERE enter_cd = ? AND emp_no = ?", Long.class,
                enterCd, empNo);
    }

    private static AuthUser as(String enterCd, long empId) {
        return new AuthUser("T001", enterCd, enterCd, "it-" + empId, empId, Set.of("EMPLOYEE"), Set.of(), Set.of(),
                "127.0.0.1");
    }
}
