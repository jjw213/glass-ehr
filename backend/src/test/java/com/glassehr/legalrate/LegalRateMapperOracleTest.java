package com.glassehr.legalrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 조회 SQL의 경계 동작과 DDL 제약을 검증한다.
 * Oracle 고유 동작(DATE 비교, CHECK)은 H2로 대신하지 않는다(docs/legal-rate-design.md 7절).
 *
 * <p>실행 조건: 환경변수 {@code ORACLE_IT=true}와 DB_URL/DB_USER/DB_PASSWORD.
 * 테이블이 없으면 V001 DDL을 적용한다. 각 테스트는 트랜잭션 롤백으로 데이터를 남기지 않는다.
 * 날짜·값은 경계 동작 설명용 가짜 값이다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class LegalRateMapperOracleTest {

    private static final String ITEM = "ZZ_IT_TEST_ITEM";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LegalRateMapper mapper;

    @BeforeEach
    void setUp() {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'LEGAL_RATE'", Integer.class);
        if (tables == null || tables == 0) {
            new ResourceDatabasePopulator(new ClassPathResource("db/oracle/V001__legal_rate.sql"))
                    .execute(jdbc.getDataSource());
        }
        jdbc.update("INSERT INTO legal_rate_item (item_code, item_name) VALUES (?, ?)", ITEM, "IT 테스트 항목");
        insert("DATE '2026-01-01'", "DATE '2026-04-01'", "1");
        insert("DATE '2026-04-01'", "NULL", "2");
    }

    private void insert(String fromSql, String toSql, String value) {
        jdbc.update("INSERT INTO legal_rate (item_code, effective_from, effective_to, rate_value, unit,"
                + " legal_basis, source_url, created_by) VALUES (?, " + fromSql + ", " + toSql + ", "
                + value + ", 'TEST_UNIT', '테스트용 근거', 'https://example.invalid/test', 'it')", ITEM);
    }

    private List<LegalRateRow> at(String date) {
        return mapper.findEffective(ITEM, LocalDate.parse(date));
    }

    @Test
    void 시작일_당일() {
        assertThat(at("2026-01-01")).singleElement()
                .satisfies(r -> assertThat(r.rateValue()).isEqualByComparingTo("1"));
    }

    @Test
    void 종료일_전날() {
        assertThat(at("2026-03-31")).singleElement()
                .satisfies(r -> assertThat(r.effectiveTo()).isEqualTo(LocalDate.parse("2026-04-01")));
    }

    @Test
    void 맞닿은_경계일은_다음_구간만() {
        assertThat(at("2026-04-01")).singleElement()
                .satisfies(r -> {
                    assertThat(r.rateValue()).isEqualByComparingTo("2");
                    assertThat(r.effectiveTo()).isNull();
                });
    }

    @Test
    void 첫_시작일_전날은_0건() {
        assertThat(at("2025-12-31")).isEmpty();
    }

    @Test
    void 미등록_코드는_0건() {
        assertThat(mapper.findEffective("ZZ_IT_NO_SUCH", LocalDate.parse("2026-02-01"))).isEmpty();
    }

    @Test
    void 겹침_데이터는_다건으로_드러난다() {
        insert("DATE '2026-03-01'", "DATE '2026-03-20'", "9");
        assertThat(at("2026-03-15")).hasSize(2);
    }

    @Test
    void 같은_시작일_중복은_UNIQUE_위반() {
        assertThatThrownBy(() -> insert("DATE '2026-01-01'", "DATE '2026-02-01'", "3"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 종료일이_시작일_이하면_CHECK_위반() {
        assertThatThrownBy(() -> insert("DATE '2027-01-01'", "DATE '2027-01-01'", "3"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 시각_성분이_있으면_CHECK_위반() {
        assertThatThrownBy(() -> insert("TO_DATE('2027-01-01 09:00', 'YYYY-MM-DD HH24:MI')", "NULL", "3"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
