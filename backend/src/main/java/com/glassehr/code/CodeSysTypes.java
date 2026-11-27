package com.glassehr.code;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 공통코드 처리방식(sys_type, #141). 로직은 코드값이 아니라 그 코드의 처리방식을 보고 판단한다.
 * 관리자는 같은 처리방식의 코드를 새로 만들 수 있지만 처리방식 목록 자체는 바꾸지 않는다(서버 고정).
 *
 * <p>저장 컬럼은 #143 이 만든 {@code common_code.process_type}이다(같은 개념이라 컬럼을 하나 더 두지 않는다).
 * 목록은 DB CHECK {@code ck_common_code_process}(V047)와 같아야 한다(CodeSysTypesSchemaScriptTest).
 * 근태코드 그룹(LEAVE_TYPE·ATTENDANCE_TYPE)의 처리방식은 {@link AttendanceCodeService#PROCESS_TYPES}가 정하고
 * 근태코드 관리 화면에서 다룬다.
 */
public final class CodeSysTypes {

    /** 화면에 보일 처리방식 한 개. */
    public record SysType(String code, String name) {
    }

    /** 고용형태 처리방식. 기간제 및 단시간근로자 보호 등에 관한 법률(기간제법) 제2조의 구분. */
    public enum EmploymentType {
        REGULAR("일반(기간의 정함 없음)"),
        /** 기간제법 제2조 제1호 기간제근로자 */
        FIXED_TERM("기간제"),
        /** 기간제법 제2조 제2호 단시간근로자 */
        PART_TIME("단시간"),
        /** 기간제법 제2조 제1호·제2호를 함께 적용(기간제이면서 단시간) */
        FIXED_TERM_PART_TIME("기간제·단시간");

        private final String label;

        EmploymentType(String label) {
            this.label = label;
        }

        /** 기간제법 제2조 제1호(기간의 정함이 있는 근로계약)를 적용하는지. */
        public boolean fixedTerm() {
            return this == FIXED_TERM || this == FIXED_TERM_PART_TIME;
        }

        /** 기간제법 제2조 제2호(단시간근로자)를 적용하는지. */
        public boolean partTime() {
            return this == PART_TIME || this == FIXED_TERM_PART_TIME;
        }

        public static EmploymentType of(CommonCode c) {
            return EmploymentType.valueOf(require(c, CodeGroups.EMPLOYMENT_TYPE));
        }
    }

    /** 퇴직사유 처리방식. 퇴직 체크리스트 구성(com.glassehr.retirement.RetirementChecklist)을 정한다. */
    public enum RetirementReason {
        VOLUNTARY("자진 퇴사"),
        RECOMMENDED("권고사직"),
        RETIREMENT_AGE("정년"),
        CONTRACT_END("계약 만료"),
        /** 해고. 근로기준법 제26조(해고의 예고)·제27조(해고사유 등의 서면통지) 검사 대상 */
        DISMISSAL("해고"),
        /** 전적(계열사 이동). 기존 시드 TRANSFER_AFFILIATE 의 체크리스트(전적 동의서)를 유지하려고 둔다 */
        TRANSFER("전적"),
        DEATH("사망"),
        OTHER("기타");

        private final String label;

        RetirementReason(String label) {
            this.label = label;
        }

        public static RetirementReason of(CommonCode c) {
            return RetirementReason.valueOf(require(c, CodeGroups.RETIREMENT_REASON));
        }
    }

    /** 처리방식이 있는 그룹 → 처리방식 목록(화면 순서). 이 그룹의 코드는 처리방식이 꼭 있어야 한다. */
    private static final Map<String, List<SysType>> BY_GROUP = Map.of(
            CodeGroups.EMPLOYMENT_TYPE, Arrays.stream(EmploymentType.values())
                    .map(t -> new SysType(t.name(), t.label)).toList(),
            CodeGroups.RETIREMENT_REASON, Arrays.stream(RetirementReason.values())
                    .map(t -> new SysType(t.name(), t.label)).toList());

    private CodeSysTypes() {
    }

    /** 그룹의 처리방식 목록. 처리방식이 없는 그룹은 빈 목록. */
    public static List<SysType> of(String groupCode) {
        return BY_GROUP.getOrDefault(groupCode, List.of());
    }

    /** 처리방식이 꼭 있어야 하는 그룹인지(공통코드 화면에서 편집하는 그룹 기준). */
    public static boolean required(String groupCode) {
        return BY_GROUP.containsKey(groupCode);
    }

    static boolean allowed(String groupCode, String sysType) {
        return of(groupCode).stream().anyMatch(t -> t.code().equals(sysType));
    }

    private static String require(CommonCode c, String group) {
        if (!group.equals(c.getGroupCode()) && c.getGroupCode() != null) {
            throw new IllegalArgumentException(group + " 코드가 아니에요: " + c.getGroupCode() + "/" + c.getCode());
        }
        String t = c.getProcessType();
        if (t == null) {
            // V047 CHECK 가 막으므로 생기지 않는다. 생기면 조용히 일반 처리하지 않고 드러낸다.
            throw new IllegalStateException("처리방식이 없는 코드예요: " + group + "/" + c.getCode());
        }
        return t;
    }
}
