package com.glassehr.code;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 공통코드 참조 목록(그룹 → 테이블·컬럼). 코드 삭제·사용처 조회는 이 목록 한 곳만 본다(#140).
 * 새 테이블이 공통코드를 참조하게 되면 여기에 추가한다. 마이그레이션의 *_code 컬럼이 이 목록이나
 * 공통코드가 아닌 컬럼 목록 중 어디에도 없으면 CodeReferencesSchemaScriptTest 가 실패한다.
 * 모든 테이블은 enter_cd 를 가진 법인별 테이블이다. 같은 법인 행만 센다.
 */
public final class CodeReferences {

    /**
     * 참조 테이블 하나.
     *
     * @param table       테이블 이름(SQL 에 그대로 들어가므로 이 클래스의 상수만 쓴다)
     * @param label       화면 이름
     * @param columns     코드값을 담는 컬럼(하나라도 같으면 참조)
     * @param dateColumn  사용처 행 목록에 보일 일자 컬럼
     * @param dateLabel   일자 컬럼 화면 이름
     */
    public record Ref(String table, String label, List<String> columns, String dateColumn, String dateLabel) {
        /** 사원 테이블 자신이면 사원 조인 없이 사번·이름을 읽는다. 나머지는 emp_id 로 사원을 조인한다. */
        public boolean isEmployeeTable() {
            return "employee".equals(table);
        }
    }

    private static Ref employee(String... columns) {
        return new Ref("employee", "사원", List.of(columns), "hire_date", "입사일");
    }

    private static Ref appointment(String... columns) {
        return new Ref("hr_appointment", "발령 이력", List.of(columns), "effective_date", "발령일");
    }

    private static final Ref CONTRACT = new Ref("employment_contract", "근로계약", List.of("contract_type_code"),
            "start_date", "계약 시작일");

    private static final Ref RETIREMENT = new Ref("retirement_case", "퇴직 처리", List.of("retire_reason_code"),
            "retire_date", "퇴직일");

    /** 그룹별 참조. 화면에서 만든 사용자 그룹은 참조하는 테이블이 없다. */
    static final Map<String, List<Ref>> BY_GROUP = Map.of(
            CodeGroups.JOB_GRADE, List.of(employee("job_grade_code"),
                    appointment("before_job_grade_code", "after_job_grade_code")),
            CodeGroups.JOB_TITLE, List.of(employee("job_title_code"),
                    appointment("before_job_title_code", "after_job_title_code")),
            CodeGroups.JOB_FAMILY, List.of(employee("job_family_code")),
            CodeGroups.EMPLOYMENT_TYPE, List.of(employee("employment_type_code"), CONTRACT),
            CodeGroups.WORK_LOCATION, List.of(employee("work_location_code")),
            CodeGroups.RETIREMENT_REASON, List.of(employee("retire_reason_code"), RETIREMENT),
            CodeGroups.APPOINTMENT_TYPE, List.of(appointment("appt_type_code")));

    private CodeReferences() {
    }

    public static List<Ref> of(String groupCode) {
        return BY_GROUP.getOrDefault(groupCode, List.of());
    }

    public static Optional<Ref> find(String groupCode, String table) {
        return of(groupCode).stream().filter(r -> r.table().equals(table)).findFirst();
    }
}
