package com.glassehr.legalrate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 항목코드 등록 마이그레이션(V003·V005)과 {@link LegalRateItem}·{@link LegalRateUnit}이 어긋나지 않는지 검사한다. DB 없이 돈다.
 * 법정 수치를 검증하지 않는다(값은 사람이 입력).
 */
class LegalRateItemRegistrationTest {

    private static final Pattern ITEM_INSERT = Pattern.compile(
            "\\('([A-Z0-9_]+)',\\s*'([^']+)',\\s*'([A-Z_]+)',\\s*'([^']*)'\\)");

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private static String withoutComments(String sql) {
        return sql.lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    /** 항목 등록 마이그레이션. 적용 순서대로. */
    private static final List<String> ITEM_MIGRATIONS = List.of(
            "db/oracle/V003__legal_rate_items_hr_attendance.sql",
            "db/oracle/V005__legal_rate_items_hr_attendance_ext.sql");

    /** 단위 CHECK 제약을 정의하는 마이그레이션. 마지막 것이 현재 제약이다. */
    private static final List<String> UNIT_CHECK_MIGRATIONS = List.of(
            "db/oracle/V002__legal_rate_item_unit.sql",
            "db/oracle/V005__legal_rate_items_hr_attendance_ext.sql");

    private static Map<String, String[]> registeredItems() throws IOException {
        Map<String, String[]> items = new LinkedHashMap<>();
        for (String path : ITEM_MIGRATIONS) {
            Matcher m = ITEM_INSERT.matcher(withoutComments(read(path)));
            while (m.find()) {
                String[] prev = items.put(m.group(1), new String[] {m.group(2), m.group(3), m.group(4)});
                assertThat(prev).as("중복 등록: %s", m.group(1)).isNull();
            }
        }
        return items;
    }

    private static Set<String> unitCheck(String path) throws IOException {
        Matcher m = Pattern.compile("unit IN \\(([^)]*)\\)").matcher(withoutComments(read(path)));
        assertThat(m.find()).as("unit CHECK in %s", path).isTrue();
        return Arrays.stream(m.group(1).split(","))
                .map(s -> s.trim().replace("'", "")).collect(Collectors.toSet());
    }

    @Test
    void 등록_코드와_enum_목록이_같다() throws IOException {
        Set<String> enumCodes = Arrays.stream(LegalRateItem.values())
                .map(LegalRateItem::code).collect(Collectors.toSet());
        assertThat(registeredItems().keySet()).containsExactlyInAnyOrderElementsOf(enumCodes);
    }

    @Test
    void 등록_단위와_enum_단위가_같다() throws IOException {
        Map<String, String[]> items = registeredItems();
        for (LegalRateItem item : LegalRateItem.values()) {
            assertThat(items.get(item.code())[1]).as(item.code()).isEqualTo(item.unit());
        }
    }

    @Test
    void 모든_항목에_이름과_근거_조문이_있다() throws IOException {
        registeredItems().forEach((code, v) -> {
            assertThat(v[0]).as("item_name %s", code).isNotBlank();
            assertThat(v[2]).as("legal_basis %s", code).isNotBlank().containsPattern("제\\d+조");
        });
    }

    @Test
    void 항목_등록_마이그레이션에는_값_행이_없다() throws IOException {
        for (String path : ITEM_MIGRATIONS) {
            String sql = withoutComments(read(path));
            assertThat(sql).as(path).doesNotContainPattern("(?i)INSERT\\s+INTO\\s+legal_rate\\s*\\(");
            assertThat(sql).as(path).doesNotContainPattern("(?i)legal_rate_api\\.");
        }
    }

    @Test
    void 사람_답변으로_추가한_항목이_등록돼_있다() throws IOException {
        // 2026-11-11 답변 "①~⑤ 모두 추가": 미숙아, 유산·사산 구간, 임신기 단축, 배우자 출산휴가·육아휴직 분할 횟수
        Map<String, String[]> items = registeredItems();
        assertThat(items).containsKeys(
                "MATERNITY_LEAVE_PREMATURE_DAYS",
                "MISCARRIAGE_LEAVE_TIER1_FROM_WEEKS", "MISCARRIAGE_LEAVE_TIER5_DAYS",
                "PREGNANCY_REDUCED_HOURS_EARLY_WEEKS", "PREGNANCY_REDUCED_HOURS_LATE_FROM_WEEKS",
                "PREGNANCY_REDUCED_HOURS_PER_DAY",
                "SPOUSE_MATERNITY_LEAVE_SPLIT_COUNT", "PARENTAL_LEAVE_SPLIT_COUNT");
        assertThat(items).hasSize(47);
    }

    @Test
    void 유산_사산휴가_구간은_시작주수와_일수가_쌍으로_있다() throws IOException {
        Map<String, String[]> items = registeredItems();
        Set<String> tiers = items.keySet().stream()
                .filter(c -> c.startsWith("MISCARRIAGE_LEAVE_TIER"))
                .map(c -> c.replaceAll("^MISCARRIAGE_LEAVE_TIER(\\d+)_.*$", "$1"))
                .collect(Collectors.toSet());
        for (String n : tiers) {
            assertThat(items.get("MISCARRIAGE_LEAVE_TIER" + n + "_FROM_WEEKS")[1]).isEqualTo(LegalRateUnit.WEEKS);
            assertThat(items.get("MISCARRIAGE_LEAVE_TIER" + n + "_DAYS")[1]).isEqualTo(LegalRateUnit.DAYS);
        }
    }

    @Test
    void 단위_CHECK_확장은_기존_단위를_모두_유지한다() throws IOException {
        Set<String> previous = new java.util.HashSet<>();
        for (String path : UNIT_CHECK_MIGRATIONS) {
            Set<String> current = unitCheck(path);
            assertThat(current).as(path).containsAll(previous);
            previous = current;
        }
    }

    @Test
    void 단위_CHECK_제약과_LegalRateUnit_상수가_같다() throws Exception {
        Set<String> checkUnits = unitCheck(UNIT_CHECK_MIGRATIONS.get(UNIT_CHECK_MIGRATIONS.size() - 1));

        Set<String> constants = new java.util.HashSet<>();
        for (Field f : LegalRateUnit.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                constants.add((String) f.get(null));
            }
        }
        assertThat(checkUnits).containsExactlyInAnyOrderElementsOf(constants);
    }

    @Test
    void 시드_TODO_파일은_실행되는_문장이_없다() throws IOException {
        String sql = withoutComments(read("db/oracle/seed/legal_rate_seed.TODO.sql"));
        assertThat(sql.isBlank()).isTrue();
    }
}
