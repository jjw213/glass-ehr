package com.glassehr.attendance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 근무유형 저장 검사. 법정 수치는 {@link Limits}로 받는다(legal_rate 적용값, {@link WorkPatternLegalLimits}).
 * 이 클래스에는 법정 수치를 쓰지 않는다. 시간은 모두 분 단위 정수.
 *
 * <p>1일 소정근로시간 = (종업 - 시업) - 휴게. 근로기준법 제50조 제3항: 근로시간 산정 시 휴게시간을 제외한다.
 * 1주 소정근로시간 = 1일 소정근로시간 x 근무일(W) 수.
 */
public final class WorkPatternRules {

    /** 하루 분 수(시각 표현 범위). 법정 수치가 아니라 시계 단위다. */
    static final int MINUTES_PER_DAY = 24 * 60;

    /** 요일 수(월~일). */
    static final int DAYS_PER_WEEK = 7;

    public static final char WORK_DAY = 'W';
    public static final char OFF_DAY = 'O';
    public static final char WEEKLY_HOLIDAY = 'H';

    private WorkPatternRules() {
    }

    /** 검사할 근무유형 값. 시각은 자정부터의 분(0~1439), dayTypes 는 월~일 7글자(W/O/H). */
    public record Spec(int workStartMin, int workEndMin, Integer breakStartMin, int breakMinutes, String dayTypes) {
    }

    /** legal_rate 에서 읽은 기준값(모두 분 또는 횟수). */
    public record Limits(
            int dailyMaxMinutes,
            int weeklyMaxMinutes,
            int breakShortThresholdMinutes,
            int breakShortMinMinutes,
            int breakLongThresholdMinutes,
            int breakLongMinMinutes,
            int weeklyHolidayMinCount) {
    }

    public enum Code {
        INVALID_TIME,
        INVALID_DAY_TYPES,
        INVALID_BREAK,
        BREAK_OUTSIDE_WORK,
        NO_WORK_DAY,
        DAILY_LIMIT_EXCEEDED,
        WEEKLY_LIMIT_EXCEEDED,
        BREAK_INSUFFICIENT,
        WEEKLY_HOLIDAY_REQUIRED
    }

    public record Violation(Code code, String message) {
    }

    public record Result(int dailyContractMinutes, int weeklyContractMinutes, List<Violation> violations) {
        public boolean ok() {
            return violations.isEmpty();
        }
    }

    /** 법정 기준과 관계없는 형식 검사(시각 범위, 요일 구분, 휴게 위치). 법정 검사 전에 한다. */
    public static List<Violation> checkStructure(Spec s) {
        Objects.requireNonNull(s, "spec");
        List<Violation> v = new ArrayList<>();
        if (!inDay(s.workStartMin()) || !inDay(s.workEndMin())) {
            v.add(new Violation(Code.INVALID_TIME, "시업·종업 시각은 00:00~23:59 사이여야 해요"));
        }
        String days = s.dayTypes();
        if (days == null || days.length() != DAYS_PER_WEEK || !days.chars().allMatch(
                c -> c == WORK_DAY || c == OFF_DAY || c == WEEKLY_HOLIDAY)) {
            v.add(new Violation(Code.INVALID_DAY_TYPES, "요일 구분은 월~일 7개를 근무일·휴무일·주휴일 중에서 골라 주세요"));
        } else if (count(days, WORK_DAY) == 0) {
            v.add(new Violation(Code.NO_WORK_DAY, "근무일을 하루 이상 지정해 주세요"));
        }
        if (s.breakMinutes() < 0) {
            v.add(new Violation(Code.INVALID_BREAK, "휴게시간은 0분 이상이어야 해요"));
        } else if (s.breakMinutes() == 0 && s.breakStartMin() != null) {
            v.add(new Violation(Code.INVALID_BREAK, "휴게시간이 0분이면 휴게 시작 시각을 비워 주세요"));
        } else if (s.breakMinutes() > 0 && (s.breakStartMin() == null || !inDay(s.breakStartMin()))) {
            v.add(new Violation(Code.INVALID_BREAK, "휴게 시작 시각을 00:00~23:59 사이로 입력해 주세요"));
        }
        if (!v.isEmpty()) {
            return v;
        }
        int span = spanMinutes(s.workStartMin(), s.workEndMin());
        if (s.breakMinutes() > 0) {
            // 근로기준법 제54조 제1항: 휴게시간은 "근로시간 도중에" 주어야 한다. 시업 직후·종업 직전에 붙인 휴게는 받지 않는다.
            int bs = offsetFromStart(s.workStartMin(), s.breakStartMin());
            if (bs <= 0 || bs + s.breakMinutes() >= span) {
                v.add(new Violation(Code.BREAK_OUTSIDE_WORK, "휴게시간은 시업과 종업 사이(근로시간 도중)에 있어야 해요(근로기준법 제54조 제1항)"));
            }
        }
        return v;
    }

    /**
     * 형식 검사 + 법정 기준 검사. 위반이 하나라도 있으면 저장을 거부한다.
     * 형식 위반이 있으면 법정 검사는 하지 않는다(계산할 수 없으므로).
     */
    public static Result check(Spec s, Limits limits) {
        Objects.requireNonNull(limits, "limits");
        List<Violation> v = checkStructure(s);
        if (!v.isEmpty()) {
            return new Result(0, 0, List.copyOf(v));
        }
        int span = spanMinutes(s.workStartMin(), s.workEndMin());
        int daily = span - s.breakMinutes();
        int workDays = count(s.dayTypes(), WORK_DAY);
        int weekly = daily * workDays;

        // 근로기준법 제50조 제2항: 1일의 근로시간은 휴게시간을 제외하고 legal_rate DAILY_STANDARD_WORK_HOURS 를 초과할 수 없다.
        if (daily > limits.dailyMaxMinutes()) {
            v.add(new Violation(Code.DAILY_LIMIT_EXCEEDED, "1일 소정근로시간 " + hm(daily) + "이 법정 한도 "
                    + hm(limits.dailyMaxMinutes()) + "을 넘어요(근로기준법 제50조 제2항)"));
        }
        // 근로기준법 제50조 제1항: 1주의 근로시간은 휴게시간을 제외하고 legal_rate WEEKLY_STANDARD_WORK_HOURS 를 초과할 수 없다.
        if (weekly > limits.weeklyMaxMinutes()) {
            v.add(new Violation(Code.WEEKLY_LIMIT_EXCEEDED, "1주 소정근로시간 " + hm(weekly) + "이 법정 한도 "
                    + hm(limits.weeklyMaxMinutes()) + "을 넘어요(근로기준법 제50조 제1항)"));
        }
        // 근로기준법 제54조 제1항: 근로시간이 짧은 구간 기준 이상이면 짧은 구간 최소 휴게, 긴 구간 기준 이상이면 긴 구간 최소 휴게.
        // 기준 시간 "인 경우"를 "이상"으로 해석한다(경계값은 tests/legal-fixtures 정답으로 확인).
        int required = requiredBreakMinutes(daily, limits);
        if (s.breakMinutes() < required) {
            v.add(new Violation(Code.BREAK_INSUFFICIENT, "근로시간 " + hm(daily) + "에는 휴게시간이 " + hm(required)
                    + " 이상 필요해요(지금 " + hm(s.breakMinutes()) + ", 근로기준법 제54조 제1항)"));
        }
        // 근로기준법 제55조 제1항: 1주에 평균 1회 이상 유급휴일. 횟수는 legal_rate WEEKLY_PAID_HOLIDAY_MIN_COUNT.
        int holidays = count(s.dayTypes(), WEEKLY_HOLIDAY);
        if (holidays < limits.weeklyHolidayMinCount()) {
            v.add(new Violation(Code.WEEKLY_HOLIDAY_REQUIRED, "주휴일을 1주에 " + limits.weeklyHolidayMinCount()
                    + "일 이상 지정해 주세요(지금 " + holidays + "일, 근로기준법 제55조 제1항)"));
        }
        return new Result(daily, weekly, List.copyOf(v));
    }

    /** 근로시간(휴게 제외, 분)에 필요한 최소 휴게(분). 근로기준법 제54조 제1항. */
    static int requiredBreakMinutes(int workMinutes, Limits limits) {
        if (workMinutes >= limits.breakLongThresholdMinutes()) {
            return limits.breakLongMinMinutes();
        }
        if (workMinutes >= limits.breakShortThresholdMinutes()) {
            return limits.breakShortMinMinutes();
        }
        return 0;
    }

    /** 시업부터 종업까지 분. 종업이 시업 이하이면 다음 날 종업으로 본다(같으면 24시간). */
    static int spanMinutes(int start, int end) {
        return end > start ? end - start : end + MINUTES_PER_DAY - start;
    }

    /** 시업부터 그 시각까지 분(시업보다 이른 시각은 다음 날로 본다). */
    static int offsetFromStart(int start, int time) {
        return time >= start ? time - start : time + MINUTES_PER_DAY - start;
    }

    static int count(String s, char c) {
        return (int) s.chars().filter(x -> x == c).count();
    }

    private static boolean inDay(int m) {
        return m >= 0 && m < MINUTES_PER_DAY;
    }

    /** 분 → "8시간 30분" 표시. */
    static String hm(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) {
            return m + "분";
        }
        return m == 0 ? h + "시간" : h + "시간 " + m + "분";
    }
}
