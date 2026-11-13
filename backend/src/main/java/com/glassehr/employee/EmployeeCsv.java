package com.glassehr.employee;

import com.glassehr.employee.EmployeeDtos.EmployeeSummary;
import java.util.List;

/** 사원 목록 CSV(엑셀에서 한글이 깨지지 않게 UTF-8 BOM). 주소·생년월일·주민등록번호는 넣지 않는다. */
final class EmployeeCsv {

    static final String HEADER = "사번,성명,소속부서ID,직급,직책,고용형태,입사일,퇴직일";

    private EmployeeCsv() {
    }

    static String write(List<EmployeeSummary> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(HEADER).append("\r\n");
        for (EmployeeSummary e : rows) {
            sb.append(cell(e.empNo())).append(',')
                    .append(cell(e.name())).append(',')
                    .append(cell(e.currentDeptId() == null ? null : e.currentDeptId().toString())).append(',')
                    .append(cell(e.jobGradeCode())).append(',')
                    .append(cell(e.jobTitleCode())).append(',')
                    .append(cell(e.employmentTypeCode())).append(',')
                    .append(cell(e.hireDate() == null ? null : e.hireDate().toString())).append(',')
                    .append(cell(e.retireDate() == null ? null : e.retireDate().toString()))
                    .append("\r\n");
        }
        return sb.toString();
    }

    /**
     * 한 칸. 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싼다. =,+,-,@ 로 시작하면 스프레드시트 수식으로 실행되지 않게
     * 앞에 작은따옴표를 붙인다(CSV 수식 주입 방지).
     */
    static String cell(String v) {
        if (v == null) {
            return "";
        }
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
