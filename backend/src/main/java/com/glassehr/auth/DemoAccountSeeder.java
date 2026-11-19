package com.glassehr.auth;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발·시연용 데모 계정(#76). {@code DEMO_ACCOUNT_PASSWORD}(.env)가 있을 때만 앱 시작 시 실행한다. 운영에서는 이 값을 두지 않는다.
 *
 * <ul>
 *   <li>사원은 {@code seed/employee_demo_seed.sql}의 사번으로 연결한다. 사번이 없으면(시드 미적용) 그 계정은 건너뛴다.</li>
 *   <li>계정이 없으면 만들고 역할을 붙인다. 비밀번호는 .env 값을 BCrypt 로 해시해 넣는다(시드 파일에 평문을 두지 않음).</li>
 *   <li>이미 비밀번호가 있는 계정은 바꾸지 않는다. 다시 넣으려면 {@code password_hash} 를 NULL 로 바꾼 뒤 재시작한다.</li>
 * </ul>
 */
@Component
@ConditionalOnExpression("!'${glassehr.auth.demo-password:}'.isBlank()")
public class DemoAccountSeeder implements ApplicationRunner {

    /** 데모 비밀번호 최소 길이(운영 정책 아님, 너무 짧은 값을 막는 개발 편의 기준). */
    static final int MIN_PASSWORD = 8;

    /** @param empNo 연결할 데모 사원 사번(null 이면 사원 연결 없음) @param role 부여 역할(null 이면 EMPLOYEE 자동만) */
    record Account(String loginId, String empNo, String role) {
    }

    static final List<Account> ACCOUNTS = List.of(
            new Account("admin", null, "ADMIN"),
            new Account("hr", "S01-2019-001", "HR"),            // 예시사원03 인사팀장
            new Account("payroll", "S01-2019-002", "PAYROLL"),  // 예시사원04 급여 실무
            new Account("head", "S01-2020-003", null),          // 예시사원08 개발1팀장(부서장 역할은 조직도로 자동)
            new Account("emp", "S01-2020-004", null));          // 예시사원09 일반 사원

    private static final Logger LOG = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private final JdbcTemplate jdbc;
    private final String password;

    public DemoAccountSeeder(JdbcTemplate jdbc, @Value("${glassehr.auth.demo-password:}") String password) {
        this.jdbc = jdbc;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password.length() < MIN_PASSWORD) {
            LOG.warn("DEMO_ACCOUNT_PASSWORD 가 {}자보다 짧아 데모 계정을 만들지 않았어요", MIN_PASSWORD);
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        for (Account a : ACCOUNTS) {
            Long empId = null;
            if (a.empNo() != null) {
                List<Long> ids = jdbc.queryForList("SELECT emp_id FROM employee WHERE emp_no = ?", Long.class, a.empNo());
                if (ids.isEmpty()) {
                    LOG.warn("데모 사원 {} 이 없어 계정 {} 을 건너뛰었어요(employee_demo_seed.sql 적용 필요)", a.empNo(), a.loginId());
                    continue;
                }
                empId = ids.get(0);
            }
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE login_id = ?", Integer.class,
                    a.loginId());
            if (exists == null || exists == 0) {
                jdbc.update("INSERT INTO app_user (login_id, emp_id, active_yn) VALUES (?, ?, 'Y')", a.loginId(), empId);
                if (a.role() != null) {
                    jdbc.update("INSERT INTO app_user_role (login_id, role_code) VALUES (?, ?)", a.loginId(), a.role());
                }
            }
            int set = jdbc.update("UPDATE app_user SET password_hash = ?, password_changed_at = ? "
                    + "WHERE login_id = ? AND password_hash IS NULL",
                    LoginService.hash(password), Timestamp.valueOf(now), a.loginId());
            if (set > 0) {
                LOG.info("데모 계정 {} 비밀번호를 설정했어요", a.loginId());
            }
        }
    }
}
