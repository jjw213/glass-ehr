package com.glassehr.appointment;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 발령 유형. 코드값은 공통코드 APPOINTMENT_TYPE(V014)과 같다(AppointmentSchemaScriptTest).
 * 유형마다 바꿀 수 있는 항목(소속·직급·직책)과 재직상태 변화를 정한다. 설계: docs/hr-appointment-design.md.
 */
public enum AppointmentType {

    /** 입사. 첫 소속·직급(필수)·직책(선택). 이전 값 없음. */
    HIRE("입사", EnumSet.allOf(Field.class)),
    /** 전보: 소속 부서 변경. */
    TRANSFER("전보", EnumSet.of(Field.DEPT)),
    /** 승진: 직급 변경. */
    PROMOTION("승진", EnumSet.of(Field.GRADE)),
    /** 직책 임명·변경·해제(이후 직책 비움 = 해제). */
    TITLE_CHANGE("직책", EnumSet.of(Field.TITLE)),
    /** 휴직(남녀고용평등과 일·가정 양립 지원에 관한 법률 제19조 육아휴직 포함). 휴직 시작·종료 예정일 필수. */
    LEAVE("휴직", EnumSet.noneOf(Field.class)),
    /** 복직: 휴직 중인 사원만. */
    RETURN("복직", EnumSet.noneOf(Field.class)),
    /** 퇴직(해고·사망 포함). */
    RETIREMENT("퇴직", EnumSet.noneOf(Field.class));

    /** 발령이 바꿀 수 있는 소속 항목. */
    public enum Field {
        DEPT("소속"), GRADE("직급"), TITLE("직책");

        private final String label;

        Field(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final String label;
    private final Set<Field> changes;

    AppointmentType(String label, Set<Field> changes) {
        this.label = label;
        this.changes = changes;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public boolean changes(Field f) {
        return changes.contains(f);
    }

    public static Optional<AppointmentType> ofCode(String code) {
        return Arrays.stream(values()).filter(t -> t.code().equals(code)).findFirst();
    }
}
