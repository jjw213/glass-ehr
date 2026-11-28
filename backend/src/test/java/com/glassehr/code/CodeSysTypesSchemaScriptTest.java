package com.glassehr.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 서버 고정 처리방식 목록(CodeSysTypes)과 V050 의 DB CHECK·시드 채우기가 같은지 정적으로 본다(#141). DB 없이 돈다. */
class CodeSysTypesSchemaScriptTest {

    private static String v050() throws IOException {
        return new ClassPathResource("db/oracle/V050__common_code_sys_type.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** CHECK 안의 {@code (group_code = 'G' AND process_type IN (...))} 목록. */
    private static List<String> checkList(String sql, String group) {
        Matcher m = Pattern.compile("group_code = '" + group + "' AND process_type IN \\(([^)]*)\\)").matcher(sql);
        assertThat(m.find()).as(group).isTrue();
        return Arrays.stream(m.group(1).split(",")).map(s -> s.strip().replace("'", "")).toList();
    }

    @Test
    void DB_CHECK는_서버_처리방식_목록과_같다() throws IOException {
        String sql = v050();
        for (String g : List.of(CodeGroups.EMPLOYMENT_TYPE, CodeGroups.RETIREMENT_REASON)) {
            assertThat(checkList(sql, g)).as(g)
                    .containsExactlyElementsOf(CodeSysTypes.of(g).stream().map(CodeSysTypes.SysType::code).toList());
        }
    }

    @Test
    void 처리방식이_있는_그룹은_고용형태와_퇴직사유뿐이다() {
        for (String g : CodeGroups.ALL) {
            boolean expected = g.equals(CodeGroups.EMPLOYMENT_TYPE) || g.equals(CodeGroups.RETIREMENT_REASON);
            assertThat(CodeSysTypes.required(g)).as(g).isEqualTo(expected);
        }
        assertThat(CodeSysTypes.of("JOB_GRADE")).isEmpty();
    }

    @Test
    void 기존_시드_코드는_처리방식으로_채운다() throws IOException {
        String sql = v050();
        for (String pair : List.of("'FIXED_TERM' THEN 'FIXED_TERM'", "'PART_TIME' THEN 'PART_TIME'",
                "ELSE 'REGULAR'", "'VOLUNTARY' THEN 'VOLUNTARY'", "'RECOMMENDED_RESIGNATION' THEN 'RECOMMENDED'",
                "'RETIREMENT_AGE' THEN 'RETIREMENT_AGE'", "'CONTRACT_EXPIRED' THEN 'CONTRACT_END'",
                "'DISMISSAL' THEN 'DISMISSAL'", "'TRANSFER_AFFILIATE' THEN 'TRANSFER'", "'DEATH' THEN 'DEATH'",
                "ELSE 'OTHER'")) {
            assertThat(sql).contains(pair);
        }
        // 기존 코드값에 없는 처리방식은 시드 코드를 만들지 않는다
        assertThat(sql).doesNotContain("INSERT INTO common_code");
    }

    @Test
    void 기존_CHECK를_해제한_뒤_새_그룹의_처리방식을_채운다() throws IOException {
        String sql = v050();
        assertThat(sql.indexOf("ALTER TABLE common_code DROP CONSTRAINT ck_common_code_process"))
                .isLessThan(sql.indexOf("UPDATE common_code"));
    }

    @Test
    void 기간제_단시간_처리방식은_두_조항을_함께_적용한다() {
        CodeSysTypes.EmploymentType both = CodeSysTypes.EmploymentType.FIXED_TERM_PART_TIME;
        assertThat(both.fixedTerm()).isTrue();
        assertThat(both.partTime()).isTrue();
        assertThat(CodeSysTypes.EmploymentType.REGULAR.fixedTerm()).isFalse();
        assertThat(CodeSysTypes.EmploymentType.REGULAR.partTime()).isFalse();
    }
}
