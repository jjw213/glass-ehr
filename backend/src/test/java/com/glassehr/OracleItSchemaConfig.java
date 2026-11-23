package com.glassehr;

import com.glassehr.legalrate.OracleLegalRateSchema;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Oracle 통합 테스트(ORACLE_IT=true)용: 빈 DB에 마이그레이션을 적용한 뒤 JPA를 띄운다.
 * JPA는 {@code ddl-auto: validate} 라서 엔티티 테이블이 없으면 컨텍스트가 뜨지 않는다.
 * 그래서 EntityManagerFactory 가 이 빈에 의존하게 해, 스키마 검증 전에 테이블을 만든다.
 * 이미 있으면 건너뛴다(같은 DB로 여러 번 돌려도 된다).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ORACLE_IT", havingValue = "true")
public class OracleItSchemaConfig {

    static final String BEAN = "oracleItSchema";

    /** 스키마 적용 완료 표시용 빈. */
    public static final class Applied {
    }

    @Bean(name = BEAN)
    Applied oracleItSchema(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        OracleLegalRateSchema.ensure(jdbc);
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'COMMON_CODE_GROUP'", Integer.class);
        if (n == null || n == 0) {
            // V006(DDL)·V007(시드)을 차례로. ResourceDatabasePopulator 는 자동 커밋으로 실행한다.
            new ResourceDatabasePopulator(
                    new ClassPathResource("db/oracle/V006__common_code.sql"),
                    new ClassPathResource("db/oracle/V007__common_code_seed.sql"))
                    .execute(dataSource);
        }
        Integer org = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'ORG_DEPT'", Integer.class);
        if (org == null || org == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V008__org_dept.sql")).execute(dataSource);
        }
        Integer emp = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'EMPLOYEE'", Integer.class);
        if (emp == null || emp == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V009__employee.sql")).execute(dataSource);
        }
        Integer retention = jdbc.queryForObject(
                "SELECT COUNT(*) FROM legal_rate_item WHERE item_code = 'EMPLOYEE_RECORD_RETENTION_YEARS'", Integer.class);
        if (retention == null || retention == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V010__legal_rate_item_record_retention.sql"))
                    .execute(dataSource);
        }
        Integer fixedTerm = jdbc.queryForObject(
                "SELECT COUNT(*) FROM legal_rate_item WHERE item_code = 'FIXED_TERM_MAX_USE_YEARS'", Integer.class);
        if (fixedTerm == null || fixedTerm == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V011__legal_rate_item_fixed_term_limit.sql"))
                    .execute(dataSource);
        }
        Integer contract = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'EMPLOYMENT_CONTRACT'", Integer.class);
        if (contract == null || contract == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V012__employment_contract.sql"))
                    .execute(dataSource);
        }
        Integer auth = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'APP_USER'", Integer.class);
        if (auth == null || auth == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V013__auth_role_privacy_log.sql"))
                    .execute(dataSource);
        }
        Integer appointment = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'HR_APPOINTMENT'", Integer.class);
        if (appointment == null || appointment == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V014__hr_appointment.sql"))
                    .execute(dataSource);
        }
        Integer apptCancel = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'HR_APPOINTMENT_CANCEL'", Integer.class);
        if (apptCancel == null || apptCancel == 0) {
            // #59: employee.emp_status 추가와 취소 발령 연결 테이블
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V019__hr_appointment_apply_cancel.sql"))
                    .execute(dataSource);
        }
        Integer workItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM legal_rate_item WHERE item_code = 'DAILY_STANDARD_WORK_HOURS'", Integer.class);
        if (workItems == null || workItems == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V015__legal_rate_items_work_pattern.sql"))
                    .execute(dataSource);
        }
        Integer holidayItem = jdbc.queryForObject(
                "SELECT COUNT(*) FROM legal_rate_item WHERE item_code = 'PUBLIC_HOLIDAY_PAID_MIN_HEADCOUNT'",
                Integer.class);
        if (holidayItem == null || holidayItem == 0) {
            new ResourceDatabasePopulator(
                    new ClassPathResource("db/oracle/V018__legal_rate_item_public_holiday_paid.sql")).execute(dataSource);
        }
        Integer pattern = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'WORK_PATTERN'", Integer.class);
        if (pattern == null || pattern == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V016__work_pattern.sql")).execute(dataSource);
        }
        Integer holiday = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'HOLIDAY'", Integer.class);
        if (holiday == null || holiday == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V017__holiday_calendar.sql"))
                    .execute(dataSource);
        }
        Integer agreementFile = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'WORKER_REP_AGREEMENT_FILE'", Integer.class);
        if (agreementFile == null || agreementFile == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V020__worker_rep_agreement_ext.sql"))
                    .execute(dataSource);
        }
        Integer punch = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'ATTENDANCE_PUNCH'", Integer.class);
        if (punch == null || punch == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V021__attendance_punch.sql"))
                    .execute(dataSource);
        }
        Integer retirement = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'RETIREMENT_CASE'", Integer.class);
        if (retirement == null || retirement == 0) {
            // #18: 해고 예고 legal_rate 항목 2개 + 퇴직 처리·체크리스트·사용증명서 테이블
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V022__retirement_process.sql"))
                    .execute(dataSource);
        }
        Integer dailySummary = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'ATTENDANCE_DAILY_SUMMARY'", Integer.class);
        if (dailySummary == null || dailySummary == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V023__attendance_daily_summary.sql"))
                    .execute(dataSource);
        }
        Integer approval = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'APPROVAL_DOC'", Integer.class);
        if (approval == null || approval == 0) {
            // #30: 공통 결재(템플릿·문서·스냅샷·처리 이력)
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V024__approval.sql"))
                    .execute(dataSource);
        }
        Integer overtime = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'OVERTIME_REQUEST'", Integer.class);
        if (overtime == null || overtime == 0) {
            // #30: 연장·휴일근로 신청, 특별연장근로 인가 정보
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V025__overtime_request.sql"))
                    .execute(dataSource);
        }
        Integer familyLeave = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'FAMILY_LEAVE_REQUEST'", Integer.class);
        if (familyLeave == null || familyLeave == 0) {
            // #37: 육아휴직·단축·가족돌봄 legal_rate 항목 8개 + 신청 테이블
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V026__family_leave_request.sql"))
                    .execute(dataSource);
        }
        Integer loginLog = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'LOGIN_ACCESS_LOG'", Integer.class);
        if (loginLog == null || loginLog == 0) {
            // #76: app_user 비밀번호·잠금 열, 로그인 접속기록, Spring Session 테이블
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V027__app_user_login_session.sql"))
                    .execute(dataSource);
        }
        Integer flexItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM legal_rate_item WHERE item_code = 'FLEX_SHORT_UNIT_PERIOD_MAX_WEEKS'",
                Integer.class);
        if (flexItems == null || flexItems == 0) {
            // #38: 탄력적·선택적 근로시간제 legal_rate 항목 12개
            new ResourceDatabasePopulator(
                    new ClassPathResource("db/oracle/V028__legal_rate_items_flexible_work.sql")).execute(dataSource);
        }
        Integer flexPlan = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'FLEX_WORK_PLAN'", Integer.class);
        if (flexPlan == null || flexPlan == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V029__flex_work_assign.sql"))
                    .execute(dataSource);
        }
        Integer message = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'MSG_THREAD'", Integer.class);
        if (message == null || message == 0) {
            // #80: 사내 메시지 대화방·참여자·메시지·읽음
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V030__message_thread.sql"))
                    .execute(dataSource);
        }
        Integer enterCompany = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'ENTER_COMPANY'", Integer.class);
        if (enterCompany == null || enterCompany == 0) {
            // #118: 법인 마스터 + 법인별 테이블 enter_cd
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V031__enter_company_enter_cd.sql"))
                    .execute(dataSource);
        }
        Integer accessorEnter = jdbc.queryForObject("SELECT COUNT(*) FROM user_tab_columns "
                + "WHERE table_name = 'PRIVACY_ACCESS_LOG' AND column_name = 'ACCESSOR_ENTER_CD'", Integer.class);
        if (accessorEnter == null || accessorEnter == 0) {
            // #119: app_user·app_user_role 키에 법인, 개인정보 조회 로그 주체 법인
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V032__app_user_enter_cd.sql"))
                    .execute(dataSource);
        }
        Integer siteKeyCols = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_cons_columns WHERE constraint_name = 'PK_BIZ_SITE'", Integer.class);
        if (siteKeyCols == null || siteKeyCols < 2) {
            // #121: 공통코드·사업장·사번 채번 키에 법인
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V034__code_site_enter_cd.sql"))
                    .execute(dataSource);
        }
        return new Applied();
    }

    @Bean
    static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnOracleItSchema() {
        return new EntityManagerFactoryDependsOnPostProcessor(BEAN);
    }
}
