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
        Integer auth = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'APP_USER'", Integer.class);
        if (auth == null || auth == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V011__auth_role_privacy_log.sql"))
                    .execute(dataSource);
        }
        return new Applied();
    }

    @Bean
    static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnOracleItSchema() {
        return new EntityManagerFactoryDependsOnPostProcessor(BEAN);
    }
}
