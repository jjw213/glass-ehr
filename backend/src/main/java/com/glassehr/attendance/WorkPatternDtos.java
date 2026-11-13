package com.glassehr.attendance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 근무유형 API 요청·응답. 시각은 자정부터의 분(0~1439), 시간 길이는 분. */
public final class WorkPatternDtos {

    /** DB 제약 ck_work_pattern_code 와 같은 규칙. */
    static final String CODE_PATTERN = "^[A-Z0-9][A-Z0-9_]*$";

    private WorkPatternDtos() {
    }

    public record WorkPatternView(long patternId, String patternCode, String patternName, int workStartMin,
            int workEndMin, Integer breakStartMin, int breakMinutes, String dayTypes, int dailyContractMinutes,
            int weeklyContractMinutes, boolean active, String note, boolean assigned) {

        static WorkPatternView of(WorkPattern p, boolean assigned) {
            WorkPatternRules.Spec s = p.spec();
            return new WorkPatternView(p.getPatternId(), p.getPatternCode(), p.getPatternName(), s.workStartMin(),
                    s.workEndMin(), s.breakStartMin(), s.breakMinutes(), s.dayTypes(), p.getDailyContractMinutes(),
                    p.getWeeklyContractMinutes(), p.isActive(), p.getNote(), assigned);
        }
    }

    public record WorkPatternRequest(
            @NotNull(message = "근무유형 코드를 입력해 주세요")
            @Size(max = 20, message = "근무유형 코드는 20자 이내로 써 주세요")
            @Pattern(regexp = CODE_PATTERN, message = "근무유형 코드는 영문 대문자·숫자·밑줄만 써 주세요")
            String patternCode,
            @NotBlank(message = "근무유형 이름을 입력해 주세요")
            @Size(max = 100, message = "근무유형 이름은 100자 이내로 써 주세요")
            String patternName,
            @NotNull(message = "시업 시각을 입력해 주세요")
            @Min(value = 0, message = "시업 시각이 올바르지 않아요")
            @Max(value = 1439, message = "시업 시각이 올바르지 않아요")
            Integer workStartMin,
            @NotNull(message = "종업 시각을 입력해 주세요")
            @Min(value = 0, message = "종업 시각이 올바르지 않아요")
            @Max(value = 1439, message = "종업 시각이 올바르지 않아요")
            Integer workEndMin,
            @Min(value = 0, message = "휴게 시작 시각이 올바르지 않아요")
            @Max(value = 1439, message = "휴게 시작 시각이 올바르지 않아요")
            Integer breakStartMin,
            @NotNull(message = "휴게시간을 입력해 주세요")
            @Min(value = 0, message = "휴게시간은 0분 이상이어야 해요")
            @Max(value = 1439, message = "휴게시간이 너무 길어요")
            Integer breakMinutes,
            @NotNull(message = "요일 구분을 입력해 주세요")
            @Pattern(regexp = "^[WOH]+$", message = "요일 구분은 W(근무일)·O(휴무일)·H(주휴일)로 7자리를 써 주세요")
            @Size(min = 7, max = 7, message = "요일 구분은 월~일 7자리로 써 주세요")
            String dayTypes,
            Boolean active,
            @Size(max = 500, message = "비고는 500자 이내로 써 주세요")
            String note) {

        WorkPatternRules.Spec spec() {
            return new WorkPatternRules.Spec(workStartMin, workEndMin, breakStartMin, breakMinutes, dayTypes);
        }
    }

    public record AssignRequest(
            @NotNull(message = "근무유형을 골라 주세요")
            @Positive(message = "근무유형이 올바르지 않아요")
            Long patternId,
            @NotNull(message = "적용 시작일을 입력해 주세요")
            LocalDate validFrom,
            @Size(max = 500, message = "사유는 500자 이내로 써 주세요")
            String reason) {
    }

    public record AssignmentView(long assignId, long empId, long patternId, String patternCode, String patternName,
            LocalDate validFrom, LocalDate validTo, String reason) {
    }

    public record ViolationView(String code, String message) {
        static List<ViolationView> of(List<WorkPatternRules.Violation> v) {
            return v.stream().map(x -> new ViolationView(x.code().name(), x.message())).toList();
        }
    }
}
