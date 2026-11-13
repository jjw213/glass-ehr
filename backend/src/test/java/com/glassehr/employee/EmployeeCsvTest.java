package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassehr.employee.EmployeeDtos.EmployeeSummary;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmployeeCsvTest {

    @Test
    void 머리글과_행() {
        String csv = EmployeeCsv.write(List.of(new EmployeeSummary(1, "S01-2026-001", "예시사원", 10L, "STAFF", "MEMBER",
                "REGULAR", LocalDate.of(2026, 3, 2), null)));
        assertThat(csv).startsWith("﻿" + EmployeeCsv.HEADER + "\r\n");
        assertThat(csv).endsWith("S01-2026-001,예시사원,10,STAFF,MEMBER,REGULAR,2026-03-02,\r\n");
    }

    @Test
    void 수식_주입과_구분자를_막는다() {
        assertThat(EmployeeCsv.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(EmployeeCsv.cell("+1")).isEqualTo("'+1");
        assertThat(EmployeeCsv.cell("-1")).isEqualTo("'-1");
        assertThat(EmployeeCsv.cell("@a")).isEqualTo("'@a");
        assertThat(EmployeeCsv.cell("가,나")).isEqualTo("\"가,나\"");
        assertThat(EmployeeCsv.cell("줄\n바꿈")).isEqualTo("\"줄\n바꿈\"");
        assertThat(EmployeeCsv.cell(null)).isEmpty();
    }

    @Test
    void 권한_없는_사람용_상세는_인적사항을_뺀다() {
        EmployeeDtos.EmployeeView v = new EmployeeDtos.EmployeeView(1, "S01-2026-001", "S01", "예시사원", "F",
                LocalDate.of(1999, 1, 1), "가상시  예시구 샘플로 1", "이력", "개발", null, LocalDate.of(2026, 3, 2), null, null,
                null, "REGULAR", 2400, "HQ", "메모", 10L, "STAFF", "MEMBER", null, null, "상세", null, "기타",
                "000000-*******").withoutPersonal();
        assertThat(v.address()).isEqualTo("가상시 ***");
        assertThat(v.birthDate()).isNull();
        assertThat(v.careerHistory()).isNull();
        assertThat(v.employmentNote()).isNull();
        assertThat(v.otherNote()).isNull();
        assertThat(v.retireReasonDetail()).isNull();
        assertThat(v.rrnMasked()).isNull();
        assertThat(v.name()).isEqualTo("예시사원");
        assertThat(v.currentDeptId()).isEqualTo(10L);
    }
}
