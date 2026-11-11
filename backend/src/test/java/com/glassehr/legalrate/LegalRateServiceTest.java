package com.glassehr.legalrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 조회 계약(반개구간·경계일·0건/다건) 단위 테스트. DB 없이 돈다.
 * 날짜·값은 경계 동작 설명용 가짜 값이며 실제 법령 시행일·법정 수치가 아니다.
 * SQL 자체의 경계 동작은 {@link LegalRateMapperOracleTest}에서 같은 케이스로 검증한다.
 */
class LegalRateServiceTest {

    private static final String ITEM = "TEST_ITEM";

    /** SQL 필터 없이 같은 항목의 모든 행을 돌려준다. 기간 판정은 서비스가 한다. */
    private static final class AllRowsMapper implements LegalRateMapper {
        final List<LegalRateRow> rows = new ArrayList<>();

        @Override
        public List<LegalRateRow> findEffective(String itemCode, LocalDate asOfDate) {
            return rows.stream().filter(r -> r.itemCode().equals(itemCode)).toList();
        }
    }

    private AllRowsMapper mapper;
    private LegalRateService service;

    private static LegalRateRow row(String from, String to, String value) {
        return new LegalRateRow(
                ITEM,
                LocalDate.parse(from),
                to == null ? null : LocalDate.parse(to),
                new BigDecimal(value),
                "TEST_UNIT",
                "테스트용 근거",
                "https://example.invalid/test");
    }

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @BeforeEach
    void setUp() {
        mapper = new AllRowsMapper();
        service = new LegalRateService(mapper);
        // A: [2026-01-01, 2026-04-01), B: [2026-04-01, 무기한)
        mapper.rows.add(row("2026-01-01", "2026-04-01", "1"));
        mapper.rows.add(row("2026-04-01", null, "2"));
    }

    @Test
    void 시작일_당일은_해당_구간() {
        assertThat(service.getEffective(ITEM, d("2026-01-01")).rateValue()).isEqualByComparingTo("1");
    }

    @Test
    void 기간_중간은_해당_구간() {
        assertThat(service.getEffective(ITEM, d("2026-02-15")).rateValue()).isEqualByComparingTo("1");
    }

    @Test
    void 종료일_전날은_해당_구간() {
        assertThat(service.getEffective(ITEM, d("2026-03-31")).rateValue()).isEqualByComparingTo("1");
    }

    @Test
    void 맞닿은_경계일은_다음_구간만() {
        assertThat(service.getEffective(ITEM, d("2026-04-01")).rateValue()).isEqualByComparingTo("2");
    }

    @Test
    void 종료일_미정이면_먼_미래도_해당() {
        assertThat(service.getEffective(ITEM, d("2099-12-31")).rateValue()).isEqualByComparingTo("2");
    }

    @Test
    void 첫_시작일_전날은_미등록() {
        assertThat(service.findEffective(ITEM, d("2025-12-31"))).isEmpty();
        assertThatThrownBy(() -> service.getEffective(ITEM, d("2025-12-31")))
                .isInstanceOfSatisfying(LegalRateException.class,
                        e -> assertThat(e.reason()).isEqualTo(LegalRateException.Reason.NOT_FOUND));
    }

    @Test
    void 종료일_당일은_구간에서_빠진다() {
        mapper.rows.clear();
        mapper.rows.add(row("2026-01-01", "2026-04-01", "1"));
        assertThat(service.findEffective(ITEM, d("2026-04-01"))).isEmpty();
    }

    @Test
    void 미등록_코드는_NOT_FOUND() {
        assertThatThrownBy(() -> service.getEffective("NO_SUCH_ITEM", d("2026-02-01")))
                .isInstanceOfSatisfying(LegalRateException.class, e -> {
                    assertThat(e.reason()).isEqualTo(LegalRateException.Reason.NOT_FOUND);
                    assertThat(e.itemCode()).isEqualTo("NO_SUCH_ITEM");
                });
    }

    @Test
    void 겹친_데이터는_첫_행을_고르지_않고_OVERLAPPING() {
        mapper.rows.add(row("2026-03-01", "2026-05-01", "9"));
        assertThatThrownBy(() -> service.getEffective(ITEM, d("2026-03-15")))
                .isInstanceOfSatisfying(LegalRateException.class,
                        e -> assertThat(e.reason()).isEqualTo(LegalRateException.Reason.OVERLAPPING));
        assertThatThrownBy(() -> service.findEffective(ITEM, d("2026-04-15")))
                .isInstanceOfSatisfying(LegalRateException.class,
                        e -> assertThat(e.reason()).isEqualTo(LegalRateException.Reason.OVERLAPPING));
    }

    // ---- 등록 항목코드(enum)로 조회: 경계일 + 단위 검사 (#7) ----
    // 항목코드만 실제 것을 쓰고 날짜·값은 경계 동작 설명용 가짜다(법정 수치 아님).

    private static final LegalRateItem REG = LegalRateItem.OVERTIME_PREMIUM_RATE;

    private static LegalRateRow regRow(String from, String to, String value, String unit) {
        return new LegalRateRow(REG.code(), d(from), to == null ? null : d(to), new BigDecimal(value),
                unit, "테스트용 근거", "https://example.invalid/test");
    }

    private void addRegRows() {
        // A: [2030-07-01, 2031-01-01), B: [2031-01-01, 무기한)
        mapper.rows.add(regRow("2030-07-01", "2031-01-01", "1", REG.unit()));
        mapper.rows.add(regRow("2031-01-01", null, "2", REG.unit()));
    }

    @Test
    void 등록항목_적용시작일_당일은_그_구간() {
        addRegRows();
        assertThat(service.getEffective(REG, d("2030-07-01")).rateValue()).isEqualByComparingTo("1");
    }

    @Test
    void 등록항목_적용시작일_전날은_기본값_없이_NOT_FOUND() {
        addRegRows();
        assertThatThrownBy(() -> service.getEffective(REG, d("2030-06-30")))
                .isInstanceOfSatisfying(LegalRateException.class, e -> {
                    assertThat(e.reason()).isEqualTo(LegalRateException.Reason.NOT_FOUND);
                    assertThat(e.itemCode()).isEqualTo(REG.code());
                });
    }

    @Test
    void 등록항목_종료일_전날은_앞_구간_종료일_당일은_다음_구간() {
        addRegRows();
        assertThat(service.getEffective(REG, d("2030-12-31")).rateValue()).isEqualByComparingTo("1");
        assertThat(service.getEffective(REG, d("2031-01-01")).rateValue()).isEqualByComparingTo("2");
    }

    @Test
    void 등록항목_종료일_당일_다음_구간이_없으면_NOT_FOUND() {
        mapper.rows.add(regRow("2030-07-01", "2031-01-01", "1", REG.unit()));
        assertThatThrownBy(() -> service.getEffective(REG, d("2031-01-01")))
                .isInstanceOfSatisfying(LegalRateException.class,
                        e -> assertThat(e.reason()).isEqualTo(LegalRateException.Reason.NOT_FOUND));
    }

    @Test
    void 등록항목_겹침은_OVERLAPPING() {
        addRegRows();
        mapper.rows.add(regRow("2030-12-01", "2031-02-01", "9", REG.unit()));
        assertThatThrownBy(() -> service.getEffective(REG, d("2030-12-15")))
                .isInstanceOfSatisfying(LegalRateException.class,
                        e -> assertThat(e.reason()).isEqualTo(LegalRateException.Reason.OVERLAPPING));
    }

    @Test
    void 등록항목_단위가_다르면_UNIT_MISMATCH() {
        mapper.rows.add(regRow("2030-07-01", null, "1", LegalRateUnit.DAYS));
        assertThatThrownBy(() -> service.getEffective(REG, d("2030-07-01")))
                .isInstanceOfSatisfying(LegalRateException.class,
                        e -> assertThat(e.reason()).isEqualTo(LegalRateException.Reason.UNIT_MISMATCH));
    }

    @Test
    void 과거_날짜로_재조회하면_당시_값() {
        LocalDate past = d("2026-02-01");
        LocalDate now = d("2026-11-11");
        assertThat(service.getEffective(ITEM, past).rateValue()).isEqualByComparingTo("1");
        assertThat(service.getEffective(ITEM, now).rateValue()).isEqualByComparingTo("2");
    }
}
