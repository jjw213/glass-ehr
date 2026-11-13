package com.glassehr.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 기간제법 제4조 제1항 사용기간 상한의 마지막 날을 tests/legal-fixtures/fixed-term-use-period.csv 정답으로 검증한다.
 * 기간 계산은 민법 제157조(초일 산입: 오전 0시 시작)·제160조(해당일 전일 만료, 해당일 없으면 그 월 말일).
 *
 * <p>연 수는 legal_rate 시드(seed/legal_rate_fixed_term.sql)의 값을 {@link LegalRateService} 조회 결과로 넣어
 * 실제 조회 경로({@link FixedTermUsePeriodCalculator#lastDayWithinLimit})를 거친다. CSV의 연 수와 시드 값이 다르면 실패한다.
 */
class FixedTermUsePeriodFixtureTest {

    private static final String FIXTURE = "tests/legal-fixtures/fixed-term-use-period.csv";
    private static final String SEED = "backend/src/main/resources/db/oracle/seed/legal_rate_fixed_term.sql";
    private static final Pattern SEED_ROW = Pattern.compile(
            "insert_period\\(\\s*'FIXED_TERM_MAX_USE_YEARS',\\s*DATE '(\\d{4}-\\d{2}-\\d{2})',\\s*NULL,\\s*(\\d+),\\s*'YEARS'");

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

    private record SeedValue(LocalDate effectiveFrom, int years) {}

    private static SeedValue seedValue() throws IOException {
        String sql = Files.readString(repoFile(SEED), StandardCharsets.UTF_8);
        Matcher m = SEED_ROW.matcher(sql);
        assertThat(m.find()).as("시드에 FIXED_TERM_MAX_USE_YEARS 행이 있어야 해요").isTrue();
        return new SeedValue(LocalDate.parse(m.group(1)), Integer.parseInt(m.group(2)));
    }

    static Stream<Arguments> fixtureRows() throws IOException {
        List<String> lines = Files.readAllLines(repoFile(FIXTURE), StandardCharsets.UTF_8);
        String[] header = lines.get(0).split(",");
        assertThat(header).containsExactly("continuous_start", "years", "last_day", "source", "verified_on");
        return lines.stream().skip(1).filter(l -> !l.isBlank()).map(l -> {
            String[] c = l.split(",", -1);
            return Arguments.of(LocalDate.parse(c[0]), Integer.parseInt(c[1]), LocalDate.parse(c[2]));
        });
    }

    @ParameterizedTest(name = "{0} 시작, {1}년 → 마지막 날 {2}")
    @MethodSource("fixtureRows")
    void 사용기간_마지막_날이_fixture_정답과_같다(LocalDate start, int years, LocalDate expectedLastDay) throws IOException {
        SeedValue seed = seedValue();
        assertThat(start).as("fixture 시작일은 시드 적용 시작일 이후여야 해요").isAfterOrEqualTo(seed.effectiveFrom());
        assertThat(years).as("fixture 연 수와 legal_rate 시드 값이 같아야 해요").isEqualTo(seed.years());

        LegalRateService rates = mock(LegalRateService.class);
        LegalRateRow row = mock(LegalRateRow.class);
        when(row.itemCode()).thenReturn("FIXED_TERM_MAX_USE_YEARS");
        when(row.rateValue()).thenReturn(BigDecimal.valueOf(seed.years()));
        when(row.unit()).thenReturn("YEARS");
        when(row.effectiveFrom()).thenReturn(seed.effectiveFrom());
        when(rates.findEffective("FIXED_TERM_MAX_USE_YEARS", start)).thenReturn(Optional.of(row));

        Optional<LocalDate> actual = new FixedTermUsePeriodCalculator(rates).lastDayWithinLimit(start);

        assertThat(actual).contains(expectedLastDay);
        verify(rates).findEffective("FIXED_TERM_MAX_USE_YEARS", start);
    }
}
