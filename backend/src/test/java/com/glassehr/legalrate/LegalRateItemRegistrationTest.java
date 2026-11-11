package com.glassehr.legalrate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 항목코드 등록 마이그레이션(V003)과 {@link LegalRateItem}·{@link LegalRateUnit}이 어긋나지 않는지 검사한다. DB 없이 돈다.
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

    private static Map<String, String[]> registeredItems() throws IOException {
        String sql = withoutComments(read("db/oracle/V003__legal_rate_items_hr_attendance.sql"));
        Map<String, String[]> items = new LinkedHashMap<>();
        Matcher m = ITEM_INSERT.matcher(sql);
        while (m.find()) {
            String[] prev = items.put(m.group(1), new String[] {m.group(2), m.group(3), m.group(4)});
            assertThat(prev).as("중복 등록: %s", m.group(1)).isNull();
        }
        return items;
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
        String sql = withoutComments(read("db/oracle/V003__legal_rate_items_hr_attendance.sql"));
        assertThat(sql).doesNotContainPattern("(?i)INSERT\\s+INTO\\s+legal_rate\\s*\\(");
    }

    @Test
    void 단위_CHECK_제약과_LegalRateUnit_상수가_같다() throws Exception {
        String sql = read("db/oracle/V002__legal_rate_item_unit.sql");
        Matcher m = Pattern.compile("unit IN \\(([^)]*)\\)").matcher(sql);
        assertThat(m.find()).isTrue();
        Set<String> checkUnits = Arrays.stream(m.group(1).split(","))
                .map(s -> s.trim().replace("'", "")).collect(Collectors.toSet());

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
