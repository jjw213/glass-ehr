package com.glassehr.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.code.CodeGroups;
import jakarta.persistence.Column;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 인사발령 DDL·발령 유형 시드(V014) 정적 검사. DB 없이 돈다. 실제 적용은 {@link AppointmentOracleTest}. */
class AppointmentSchemaScriptTest {

    private static String ddl() throws IOException {
        return new ClassPathResource("db/oracle/V014__hr_appointment.sql").getContentAsString(StandardCharsets.UTF_8)
                .lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    @Test
    void DDL에_중괄호가_없다() throws IOException {
        // Oracle JDBC 가 {..}를 SQL92 escape 로 해석한다(V006 참고).
        assertThat(ddl()).doesNotContain("{").doesNotContain("}");
    }

    @Test
    void 엔티티_컬럼이_DDL에_모두_있다() throws IOException {
        String table = ddl();
        for (Field f : HrAppointment.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null) {
                assertThat(table).as(c.name()).containsPattern("(?m)^\\s+" + c.name() + "\\s");
            }
        }
    }

    @Test
    void 완료기준_컬럼이_있고_사유는_필수다() throws IOException {
        String table = ddl();
        for (String col : List.of("appt_no", "appt_type_code", "emp_id", "effective_date", "before_dept_id",
                "before_job_grade_code", "before_job_title_code", "before_emp_status", "after_dept_id",
                "after_job_grade_code", "after_job_title_code", "after_emp_status", "reason", "leave_start_date",
                "leave_end_date", "confirmed_yn", "confirmed_at")) {
            assertThat(table).as(col).containsPattern("(?m)^\\s+" + col + "\\s");
        }
        // 근로기준법 제23조 제1항: 사유 필수
        assertThat(table).containsPattern("reason\\s+VARCHAR2\\(1000 CHAR\\)\\s+NOT NULL");
        assertThat(table).contains("CHECK (LENGTH(TRIM(reason)) > 0)");
        assertThat(table).contains("UNIQUE (appt_no)");
    }

    @Test
    void 재직상태_CHECK는_EmpStatus와_같다() throws IOException {
        Matcher m = Pattern.compile("after_emp_status IN \\(([^)]*)\\)").matcher(ddl());
        assertThat(m.find()).isTrue();
        List<String> db = Arrays.stream(m.group(1).split(",")).map(s -> s.strip().replace("'", "")).toList();
        assertThat(db).containsExactlyElementsOf(Arrays.stream(EmpStatus.values()).map(Enum::name).toList());
        assertThat(ddl()).contains("before_emp_status IN (" + m.group(1) + ")");
    }

    @Test
    void 발령번호_CHECK는_채번_형식과_같다() throws IOException {
        assertThat(ddl()).contains("REGEXP_LIKE(appt_no, '" + AppointmentNoGenerator.APPT_NO_PATTERN + "', 'c')");
        assertThat(AppointmentNoGenerator.format(2026, 1)).matches(AppointmentNoGenerator.APPT_NO_PATTERN);
        assertThat(AppointmentNoGenerator.format(2026, AppointmentNoGenerator.MAX_SEQ))
                .isEqualTo("AP-2026-999999").matches(AppointmentNoGenerator.APPT_NO_PATTERN);
        assertThat(AppointmentNoGenerator.format(2026, AppointmentNoGenerator.MAX_SEQ + 1))
                .doesNotMatch(AppointmentNoGenerator.APPT_NO_PATTERN);
    }

    @Test
    void 발령유형_시드는_AppointmentType과_같은_순서다() throws IOException {
        Matcher m = Pattern.compile("INSERT INTO common_code \\([^)]*\\) VALUES\\s*\\('([^']*)',\\s*'([^']*)'")
                .matcher(ddl());
        List<String> codes = new ArrayList<>();
        while (m.find()) {
            assertThat(m.group(1)).isEqualTo(CodeGroups.APPOINTMENT_TYPE);
            codes.add(m.group(2));
        }
        assertThat(codes).containsExactlyElementsOf(
                Arrays.stream(AppointmentType.values()).map(AppointmentType::code).toList());
        assertThat(codes).containsExactly("HIRE", "TRANSFER", "PROMOTION", "TITLE_CHANGE", "LEAVE", "RETURN",
                "RETIREMENT");
    }

    @Test
    void 휴직일_CHECK는_휴직_유형에만_필수다() throws IOException {
        String t = ddl();
        assertThat(t).contains("appt_type_code = 'LEAVE' AND leave_start_date IS NOT NULL AND leave_end_date IS NOT NULL");
        assertThat(t).contains("appt_type_code <> 'LEAVE' AND leave_start_date IS NULL AND leave_end_date IS NULL");
    }
}
