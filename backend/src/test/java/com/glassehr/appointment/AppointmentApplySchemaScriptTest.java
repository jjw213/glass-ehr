package com.glassehr.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.employee.Employee;
import jakarta.persistence.Column;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** #59 V019(사원 재직상태·취소 발령 연결) 정적 검사. DB 없이 돈다. */
class AppointmentApplySchemaScriptTest {

    private static String ddl() throws IOException {
        return new ClassPathResource("db/oracle/V019__hr_appointment_apply_cancel.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
    }

    @Test
    void DDL에_중괄호가_없다() throws IOException {
        assertThat(ddl()).doesNotContain("{").doesNotContain("}");
    }

    @Test
    void 사원_재직상태_CHECK는_EmpStatus와_같고_엔티티와_맞는다() throws IOException {
        Matcher m = Pattern.compile("emp_status IN \\(([^)]*)\\)").matcher(ddl());
        assertThat(m.find()).isTrue();
        List<String> db = Arrays.stream(m.group(1).split(",")).map(s -> s.strip().replace("'", "")).toList();
        assertThat(db).containsExactlyElementsOf(Arrays.stream(EmpStatus.values()).map(Enum::name).toList());
        assertThat(ddl()).containsPattern("emp_status\\s+VARCHAR2\\(10 CHAR\\)\\s+DEFAULT 'ACTIVE' NOT NULL");
        assertThat(Arrays.stream(Employee.class.getDeclaredFields())
                .map(f -> f.getAnnotation(Column.class)).filter(c -> c != null).map(Column::name))
                .contains("emp_status");
    }

    @Test
    void 취소_연결_엔티티_컬럼이_DDL에_있고_원_발령당_하나다() throws IOException {
        String t = ddl();
        for (Field f : HrAppointmentCancel.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null) {
                assertThat(t).as(c.name()).containsPattern("(?m)^\\s+" + c.name() + "\\s");
            }
        }
        assertThat(t).contains("UNIQUE (original_appt_id)");
        assertThat(t).contains("FOREIGN KEY (cancel_appt_id) REFERENCES hr_appointment (appt_id)");
        assertThat(t).contains("FOREIGN KEY (original_appt_id) REFERENCES hr_appointment (appt_id)");
    }
}
