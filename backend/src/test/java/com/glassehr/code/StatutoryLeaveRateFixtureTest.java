package com.glassehr.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.code.AttendanceCodeDtos.AttendanceCodeView;
import com.glassehr.code.AttendanceCodeDtos.LegalValue;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 법정휴가 기준값 정답(tests/legal-fixtures/statutory-leave-rate-boundaries.csv, #148)과 시드
 * (seed/legal_rate_statutory_leave.sql)를 근태코드 관리 화면 조회 경로({@link AttendanceCodeService#list})로 비교한다.
 * 조회일(Asia/Seoul) 기준값이 화면에 보이는지, 시행일 전날·당일 경계에서 값이 바뀌는지 확인한다.
 * 기대일수가 비어 있으면 기준값이 없다(화면: '기준값 미등록').
 */
class StatutoryLeaveRateFixtureTest {

    private static final String FIXTURE = "tests/legal-fixtures/statutory-leave-rate-boundaries.csv";
    private static final String SEED = "backend/src/main/resources/db/oracle/seed/legal_rate_statutory_leave.sql";
    private static final Pattern SEED_ROW = Pattern.compile(
            "insert_period\\(\\s*'([A-Z0-9_]+)',\\s*DATE '(\\d{4}-\\d{2}-\\d{2})',\\s*(NULL|DATE '\\d{4}-\\d{2}-\\d{2}'),"
                    + "\\s*([0-9.]+),\\s*'([A-Z]+)'");
    private static final String E = "C001";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 25, 9, 0);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 시드(V041)의 법정휴가 기본 코드 */
    private static final List<String> STATUTORY_CODES = List.of(
            "MATERNITY", "SPOUSE_MATERNITY", "MENSTRUAL", "FAMILY_CARE", "INFERTILITY_TREATMENT");

    record Case(String itemCode, LocalDate asOf, BigDecimal expected) {
        @Override
        public String toString() {
            return itemCode + " @ " + asOf + " = " + (expected == null ? "미등록" : expected.toPlainString());
        }
    }

    private static Path repoFile(String relative) {
        for (Path base : List.of(Path.of(".."), Path.of("."))) {
            Path p = base.resolve(relative).normalize();
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        throw new IllegalStateException("파일을 찾을 수 없어요: " + relative);
    }

    static Stream<Case> cases() throws IOException {
        List<String> lines = Files.readAllLines(repoFile(FIXTURE), StandardCharsets.UTF_8);
        assertThat(lines.get(0)).startsWith("item_code,기준일,기대일수,출처,검증일");
        List<Case> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            out.add(new Case(f[0], LocalDate.parse(f[1]), f[2].isBlank() ? null : new BigDecimal(f[2])));
        }
        return out.stream();
    }

    private static List<LegalRateRow> seedRows() throws IOException {
        List<LegalRateRow> rows = new ArrayList<>();
        Matcher m = SEED_ROW.matcher(Files.readString(repoFile(SEED), StandardCharsets.UTF_8));
        while (m.find()) {
            LocalDate to = m.group(3).equals("NULL") ? null : LocalDate.parse(m.group(3).substring(6, 16));
            rows.add(new LegalRateRow(m.group(1), LocalDate.parse(m.group(2)), to, new BigDecimal(m.group(4)),
                    m.group(5), "seed", null));
        }
        return rows;
    }

    private static AttendanceCodeService service(LocalDate today, List<LegalRateRow> rows) {
        CodeGroupRepository groups = mock(CodeGroupRepository.class);
        CommonCodeRepository codes = mock(CommonCodeRepository.class);
        AttendanceLeaveTypeRepository leaves = mock(AttendanceLeaveTypeRepository.class);
        LegalRateService rates = mock(LegalRateService.class);
        Clock clock = Clock.fixed(today.atTime(9, 0).atZone(SEOUL).toInstant(), SEOUL);
        CodeGroup group = new CodeGroup(E, CodeGroups.LEAVE_TYPE, "휴가 종류", null, 0, true, true, T0)
                .withEditPage("attendanceCodes");
        when(groups.findByEnterCdAndGroupCode(E, CodeGroups.LEAVE_TYPE)).thenReturn(Optional.of(group));
        when(groups.existsByEnterCdAndGroupCode(E, CodeGroups.LEAVE_TYPE)).thenReturn(true);
        when(codes.findByEnterCdAndGroupCodeOrderBySortOrderAscCodeAsc(E, CodeGroups.LEAVE_TYPE)).thenReturn(
                STATUTORY_CODES.stream().map(c -> new CommonCode(E, CodeGroups.LEAVE_TYPE, c, c, null, 10, true,
                        "STATUTORY", T0).markSystem()).toList());
        when(leaves.findByEnterCd(E)).thenReturn(STATUTORY_CODES.stream().map(c -> {
            AttendanceLeaveType d = new AttendanceLeaveType(E, c, T0);
            d.change(true, false, false, null, false, null, T0);
            return d;
        }).toList());
        // LegalRateService.findEffective 와 같은 규칙: 시작일 포함, 종료일(다음 시작일) 제외
        when(rates.findEffective(anyString(), any())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            LocalDate asOf = inv.getArgument(1);
            return rows.stream()
                    .filter(r -> r.itemCode().equals(code) && !r.effectiveFrom().isAfter(asOf)
                            && (r.effectiveTo() == null || asOf.isBefore(r.effectiveTo())))
                    .reduce((a, b) -> b.effectiveFrom().isAfter(a.effectiveFrom()) ? b : a);
        });
        CommonCodeService common = new CommonCodeService(groups, codes, mock(CodeUsageRepository.class), clock);
        return new AttendanceCodeService(common, codes, leaves, rates, clock);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void 근태코드_화면은_조회일_기준_법정_기준값을_보인다(Case c) throws IOException {
        List<AttendanceCodeView> list = service(c.asOf(), seedRows()).list(E, CodeGroups.LEAVE_TYPE);

        List<LegalValue> matched = list.stream().flatMap(v -> v.leave().legalValues().stream())
                .filter(v -> v.itemCode().equals(c.itemCode())).toList();
        assertThat(matched).as("화면에 보이는 법정 기준값 %s", c.itemCode()).hasSize(1);
        if (c.expected() == null) {
            assertThat(matched.get(0).value()).isNull();
        } else {
            assertThat(matched.get(0).value()).isEqualByComparingTo(c.expected());
        }
    }

    @Test
    void 시드의_모든_항목이_정답에_있다() throws IOException {
        List<String> fixtureItems = cases().map(Case::itemCode).distinct().toList();
        assertThat(seedRows()).isNotEmpty()
                .allSatisfy(r -> assertThat(fixtureItems).contains(r.itemCode()));
    }
}
