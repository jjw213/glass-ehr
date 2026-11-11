package com.glassehr.legalrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 겹침 방지 쓰기 패키지 legal_rate_api(V004)를 Oracle 실DB에서 검증한다(docs/legal-rate-design.md 7절).
 *
 * <p>실행 조건: 환경변수 {@code ORACLE_IT=true}와 DB_URL/DB_USER/DB_PASSWORD(스키마 소유 계정).
 * 동시성 검증을 위해 트랜잭션을 실제로 커밋하므로, 테스트 전용 항목코드의 행을 테스트 전후에 지운다.
 * 날짜·값은 경계 동작 설명용 가짜 값이다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class LegalRateApiOracleTest {

    private static final String ITEM = "ZZ_IT_API_ITEM";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private LegalRateMapper mapper;

    @BeforeEach
    void setUp() {
        OracleLegalRateSchema.ensure(jdbc);
        cleanUp();
        jdbc.update("INSERT INTO legal_rate_item (item_code, item_name, unit, legal_basis) VALUES (?, ?, ?, ?)",
                ITEM, "IT 쓰기 테스트 항목", LegalRateUnit.DAYS, "테스트용 근거 제1조");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM legal_rate WHERE item_code = ?", ITEM);
        jdbc.update("DELETE FROM legal_rate_item WHERE item_code = ?", ITEM);
    }

    private void insertPeriod(String item, String from, String to, String unit) {
        jdbc.update("BEGIN legal_rate_api.insert_period(?, ?, ?, ?, ?, ?, ?, ?); END;",
                item, Date.valueOf(from), to == null ? null : Date.valueOf(to), 1, unit,
                "테스트용 근거", "https://example.invalid/test", "it");
    }

    private void insertPeriod(String from, String to) {
        insertPeriod(ITEM, from, to, LegalRateUnit.DAYS);
    }

    private void setEnd(String from, String to) {
        jdbc.update("BEGIN legal_rate_api.set_end(?, ?, ?); END;",
                ITEM, Date.valueOf(from), to == null ? null : Date.valueOf(to));
    }

    private static int oraCode(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException se) {
                return se.getErrorCode();
            }
        }
        return -1;
    }

    private int rows() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM legal_rate WHERE item_code = ?", Integer.class, ITEM);
        return n == null ? 0 : n;
    }

    @Test
    void 맞닿는_기간은_쓰기_성공() {
        insertPeriod("2026-01-01", "2026-04-01");
        insertPeriod("2026-04-01", null);
        assertThat(rows()).isEqualTo(2);
        assertThat(mapper.findEffective(ITEM, LocalDate.parse("2026-04-01"))).singleElement()
                .satisfies(r -> assertThat(r.effectiveFrom()).isEqualTo(LocalDate.parse("2026-04-01")));
    }

    @Test
    void 겹치는_기간은_거부() {
        insertPeriod("2026-01-01", "2026-04-01");
        assertThatThrownBy(() -> insertPeriod("2026-03-31", "2026-05-01"))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(oraCode(e)).isEqualTo(20103));
        assertThatThrownBy(() -> insertPeriod("2025-12-01", null))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(oraCode(e)).isEqualTo(20103));
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void 열린_기간_뒤에_넣으려면_먼저_닫아야_한다() {
        insertPeriod("2026-01-01", null);
        assertThatThrownBy(() -> insertPeriod("2027-01-01", null))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(oraCode(e)).isEqualTo(20103));
        setEnd("2026-01-01", "2027-01-01");
        insertPeriod("2027-01-01", null);
        assertThat(rows()).isEqualTo(2);
    }

    @Test
    void 종료일을_늘려_다음_기간과_겹치면_거부() {
        insertPeriod("2026-01-01", "2026-04-01");
        insertPeriod("2026-04-01", null);
        assertThatThrownBy(() -> setEnd("2026-01-01", "2026-04-02"))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(oraCode(e)).isEqualTo(20103));
    }

    @Test
    void 없는_기간의_종료일_변경은_거부() {
        assertThatThrownBy(() -> setEnd("2026-01-01", "2026-02-01"))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(oraCode(e)).isEqualTo(20104));
    }

    @Test
    void 미등록_항목은_거부() {
        assertThatThrownBy(() -> insertPeriod("ZZ_IT_NO_SUCH", "2026-01-01", null, LegalRateUnit.DAYS))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(oraCode(e)).isEqualTo(20101));
    }

    @Test
    void 항목_단위와_다르면_거부() {
        assertThatThrownBy(() -> insertPeriod(ITEM, "2026-01-01", null, LegalRateUnit.PERCENT))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(oraCode(e)).isEqualTo(20102));
    }

    @Test
    void 직접_INSERT도_단위가_다르면_FK_위반() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO legal_rate (item_code, effective_from, rate_value, unit,"
                + " legal_basis, source_url, created_by) VALUES (?, DATE '2026-01-01', 1, 'PERCENT',"
                + " '테스트용 근거', 'https://example.invalid/test', 'it')", ITEM))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void 역전_기간과_시각_성분은_거부() {
        assertThatThrownBy(() -> insertPeriod("2026-04-01", "2026-04-01"))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(oraCode(e)).isEqualTo(20105));
        assertThatThrownBy(() -> jdbc.update(
                "BEGIN legal_rate_api.insert_period(?, TO_DATE('2026-01-01 09:00', 'YYYY-MM-DD HH24:MI'),"
                        + " NULL, 1, 'DAYS', '테스트용 근거', 'https://example.invalid/test', 'it'); END;", ITEM))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(oraCode(e)).isEqualTo(20105));
    }

    @Test
    void 동시에_겹치는_기간을_넣으면_한_건만_성공() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (String from : List.of("2026-01-01", "2026-02-01")) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        tx.executeWithoutResult(s -> insertPeriod(from, null));
                        return true;
                    } catch (DataAccessException e) {
                        assertThat(oraCode(e)).isEqualTo(20103);
                        return false;
                    }
                }));
            }
            start.countDown();
            int ok = 0;
            for (Future<Boolean> f : results) {
                if (f.get(30, TimeUnit.SECONDS)) {
                    ok++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(rows()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
