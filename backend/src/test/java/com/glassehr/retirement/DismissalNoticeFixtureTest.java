package com.glassehr.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import com.glassehr.retirement.DismissalNoticeChecker.Exemption;
import com.glassehr.retirement.DismissalNoticeChecker.NoticeCheck;
import com.glassehr.retirement.DismissalNoticeChecker.Result;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 근로기준법 제26조 해고 예고 확인을 tests/legal-fixtures/dismissal-notice.csv 정답으로 검증한다.
 * 날짜 셈은 민법 제157조·제160조(해당일이 없으면 그 월 말일 만료), 단서 제1호 적용은 법률 제16270호 부칙 제2조.
 *
 * <p>기준값은 legal_rate 시드(seed/legal_rate_dismissal_notice.sql)를 읽어 {@link LegalRateService} 조회 결과로 넣는다.
 * 시드 값이 바뀌면 이 테스트도 그 값으로 돈다.
 */
class DismissalNoticeFixtureTest {

    private static final String FIXTURE = "tests/legal-fixtures/dismissal-notice.csv";
    private static final String SEED = "backend/src/main/resources/db/oracle/seed/legal_rate_dismissal_notice.sql";
    private static final Pattern SEED_ROW = Pattern.compile(
            "insert_period\\(\\s*'([A-Z_]+)',\\s*DATE '(\\d{4}-\\d{2}-\\d{2})',\\s*(NULL|DATE '\\d{4}-\\d{2}-\\d{2}'),"
                    + "\\s*(\\d+),\\s*'([A-Z]+)'");

    /** Maven은 backend/에서, IDE는 저장소 루트에서 돌 수 있어 둘 다 찾는다. */
    private static Path repoFile(String relative) {
        for (Path base : List.of(Path.of(".."), Path.of("."))) {
            Path p = base.resolve(relative).normalize();
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        throw new IllegalStateException("파일을 찾을 수 없어요: " + relative);
    }

    private static List<LegalRateRow> seedRows() throws IOException {
        String sql = Files.readString(repoFile(SEED), StandardCharsets.UTF_8);
        Matcher m = SEED_ROW.matcher(sql);
        List<LegalRateRow> rows = new ArrayList<>();
        while (m.find()) {
            LocalDate to = m.group(3).equals("NULL") ? null : LocalDate.parse(m.group(3).substring(6, 16));
            rows.add(new LegalRateRow(m.group(1), LocalDate.parse(m.group(2)), to, new BigDecimal(m.group(4)),
                    m.group(5), "seed", null));
        }
        return rows;
    }

    /** 시드 행으로 effective_from ≤ 기준일 < effective_to 인 값을 돌려주는 가짜 조회. */
    private static LegalRateService seededRates() throws IOException {
        List<LegalRateRow> rows = seedRows();
        LegalRateService rates = mock(LegalRateService.class);
        when(rates.findEffective(anyString(), any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            LocalDate asOf = inv.getArgument(1);
            return rows.stream()
                    .filter(r -> r.itemCode().equals(code) && !r.effectiveFrom().isAfter(asOf)
                            && (r.effectiveTo() == null || asOf.isBefore(r.effectiveTo())))
                    .reduce((a, b) -> b.effectiveFrom().isAfter(a.effectiveFrom()) ? b : a);
        });
        return rates;
    }

    @Test
    void 시드에_예고일수와_적용제외_개월수가_있다() throws IOException {
        assertThat(seedRows()).extracting(LegalRateRow::itemCode)
                .contains("DISMISSAL_NOTICE_DAYS", "DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS");
    }

    static Stream<Arguments> fixtureRows() throws IOException {
        List<String> lines = Files.readAllLines(repoFile(FIXTURE), StandardCharsets.UTF_8);
        assertThat(lines.get(0).split(",")).startsWith("사례", "입사일", "예고일", "해고일", "적용제외사유", "기대결과",
                "기대예고일수", "기대미달일수", "출처", "검증일");
        return lines.stream().skip(1).filter(l -> !l.isBlank()).map(l -> {
            String[] c = l.split(",", -1);
            return Arguments.of(c[0], LocalDate.parse(c[1]), LocalDate.parse(c[2]), LocalDate.parse(c[3]),
                    c[4].isBlank() ? null : Exemption.valueOf(c[4]), NoticeCheck.valueOf(c[5]),
                    Integer.valueOf(c[6]), c[7].isBlank() ? null : Integer.valueOf(c[7]));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureRows")
    void 해고예고_확인이_fixture_정답과_같다(String name, LocalDate hire, LocalDate notice, LocalDate dismissal,
            Exemption exemption, NoticeCheck expected, Integer expectedNoticeDays, Integer expectedShortfall)
            throws IOException {
        Result r = new DismissalNoticeChecker(seededRates()).check(hire, notice, dismissal, exemption);

        assertThat(r.check()).as(name + " 결과").isEqualTo(expected);
        assertThat(r.noticeDays()).as(name + " 예고 일수").isEqualTo(expectedNoticeDays);
        assertThat(r.shortfallDays()).as(name + " 미달 일수").isEqualTo(expectedShortfall);
    }

    @Test
    void 개정_시행_전_입사자는_근속_적용제외를_쓰지_않는다() throws IOException {
        // 법률 제16270호 부칙 제2조: 시행일(시드 effective_from) 전에 계약한 근로자에게는 개정 단서 제1호를 적용하지 않는다.
        LocalDate effectiveFrom = seedRows().stream()
                .filter(r -> r.itemCode().equals("DISMISSAL_NOTICE_EXEMPT_SERVICE_MONTHS"))
                .map(LegalRateRow::effectiveFrom).min(LocalDate::compareTo).orElseThrow();
        LocalDate hire = effectiveFrom.minusDays(1);
        LocalDate dismissal = effectiveFrom.plusDays(10);
        Result r = new DismissalNoticeChecker(seededRates()).check(hire, dismissal, dismissal, Exemption.SHORT_SERVICE);
        assertThat(r.check()).isEqualTo(NoticeCheck.SHORT);
        assertThat(r.warnings().get(0)).contains("부칙 제2조");

        Result after = new DismissalNoticeChecker(seededRates())
                .check(effectiveFrom, dismissal, dismissal, Exemption.SHORT_SERVICE);
        assertThat(after.check()).isEqualTo(NoticeCheck.EXEMPT);
    }

    @Test
    void 해당일이_없으면_그_월_말일이_만료일() {
        // 민법 제160조 제3항
        assertThat(DismissalNoticeChecker.lastDayOfMonths(LocalDate.of(2026, 1, 31), 3)).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(DismissalNoticeChecker.lastDayOfMonths(LocalDate.of(2026, 1, 1), 3)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(DismissalNoticeChecker.lastDayOfMonths(LocalDate.of(2024, 2, 29), 3)).isEqualTo(LocalDate.of(2024, 5, 28));
        assertThat(Optional.of(DismissalNoticeChecker.lastDayOfMonths(LocalDate.of(2026, 1, 30), 1)))
                .contains(LocalDate.of(2026, 2, 28));
    }
}
