package com.glassehr.attendance;

import com.glassehr.attendance.WorkHoursCalculator.DayResult;
import com.glassehr.attendance.WorkPatternRules.Code;
import com.glassehr.attendance.WorkPatternRules.Violation;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 근무 정책(기록·인정 규칙, #159) 검사와 적용. 순수 함수만 둔다. 시간은 모두 분 단위 정수.
 *
 * <p>정책 값(인정 단위, 정시 인정 범위, 한도 등)은 회사가 정하는 값이라 법정 수치가 아니다. 법정 수치는
 * {@link WorkPatternRules.Limits}와 legal_rate {@code WEEKLY_OVERTIME_LIMIT_HOURS}로 받아 넘는 정책을 거부한다.
 * 정책이 없으면 {@link #SYSTEM_DEFAULT}(정책 도입 전 집계와 같은 결과)를 쓴다.
 */
public final class WorkPolicyRules {

    private WorkPolicyRules() {
    }

    /** 연장·야간·휴일 근로 허용 방식. */
    public enum AllowMode { UNLIMITED, DENY, LIMIT }

    /** 인정 단위 미달 처리. */
    public enum Rounding { UP, DOWN }

    /** 퇴근 기록 누락 처리. */
    public enum MissingOut {
        /** 근무유형 종업 시각에 퇴근한 것으로 본다. */
        SCHEDULED_END,
        /** 출근만 남긴다(실근로 0분, 정책 도입 전과 같음). */
        IN_ONLY,
        /** 출근만 남기고 다음 날 확인을 요청한다(관리자·본인 확인 대상 표시). */
        CONFIRM_NEXT_DAY
    }

    /** 허용 방식과 한도(분). LIMIT 이 아니면 한도는 모두 null. */
    public record Allowance(AllowMode mode, Integer dayLimit, Integer weekLimit, Integer monthLimit) {
        public static final Allowance UNLIMITED = new Allowance(AllowMode.UNLIMITED, null, null, null);
        public static final Allowance DENY = new Allowance(AllowMode.DENY, null, null, null);

        public Allowance {
            Objects.requireNonNull(mode, "mode");
        }
    }

    public record Policy(Allowance overtime, Allowance night, Allowance holiday, boolean separateStatutoryExtra,
            int unitMinutes, int minRecognizeMinutes, Rounding rounding, int onTimeInGraceMinutes,
            int onTimeOutGraceMinutes, boolean earlyInAllowed, MissingOut missingOut, boolean realtimeReflect) {
        public Policy {
            Objects.requireNonNull(overtime, "overtime");
            Objects.requireNonNull(night, "night");
            Objects.requireNonNull(holiday, "holiday");
            Objects.requireNonNull(rounding, "rounding");
            Objects.requireNonNull(missingOut, "missingOut");
        }
    }

    /** 정책이 없을 때. 1분 단위·정시 인정 없음·실시간 기록·퇴근 누락은 출근만 — 정책 도입 전 집계와 같다. */
    public static final Policy SYSTEM_DEFAULT = new Policy(Allowance.UNLIMITED, Allowance.UNLIMITED,
            Allowance.UNLIMITED, true, 1, 0, Rounding.UP, 0, 0, true, MissingOut.IN_ONLY, true);

    /** 화면 입력 범위(운영 보호용, 법정 수치 아님). DDL CHECK 와 같다. */
    static final int MAX_UNIT_MINUTES = 60;
    static final int MAX_MIN_RECOGNIZE_MINUTES = 240;
    static final int MAX_GRACE_MINUTES = 60;

    /**
     * 형식 검사 + 법정 한도 검사. 위반이 하나라도 있으면 저장을 거부한다.
     *
     * @param pattern 재정의 대상 근무유형 값(기본 정책이면 null — 휴게 검사를 하지 않는다)
     * @param limits 근무유형 검사와 같은 legal_rate 적용값
     * @param weeklyOvertimeLimitMinutes legal_rate WEEKLY_OVERTIME_LIMIT_HOURS(분)
     */
    public static List<Violation> check(Policy p, WorkPatternRules.Spec pattern, WorkPatternRules.Limits limits,
            int weeklyOvertimeLimitMinutes) {
        Objects.requireNonNull(p, "policy");
        List<Violation> v = new ArrayList<>();
        checkAllowance(v, "연장근로", p.overtime());
        checkAllowance(v, "야간근로", p.night());
        checkAllowance(v, "휴일근로", p.holiday());
        if (p.unitMinutes() < 1 || p.unitMinutes() > MAX_UNIT_MINUTES) {
            v.add(invalid("인정 단위는 1~" + MAX_UNIT_MINUTES + "분이어야 해요"));
        }
        if (p.minRecognizeMinutes() < 0 || p.minRecognizeMinutes() > MAX_MIN_RECOGNIZE_MINUTES) {
            v.add(invalid("최소 인정 시간은 0~" + MAX_MIN_RECOGNIZE_MINUTES + "분이어야 해요"));
        }
        if (p.onTimeInGraceMinutes() < 0 || p.onTimeInGraceMinutes() > MAX_GRACE_MINUTES
                || p.onTimeOutGraceMinutes() < 0 || p.onTimeOutGraceMinutes() > MAX_GRACE_MINUTES) {
            v.add(invalid("정시 출퇴근 인정 범위는 0~" + MAX_GRACE_MINUTES + "분이어야 해요"));
        }
        if (!v.isEmpty()) {
            return List.copyOf(v);
        }
        // 근로기준법 제53조 제1항: 1주 연장 한도(legal_rate WEEKLY_OVERTIME_LIMIT_HOURS). 휴일근로도 1주 실근로에 포함하므로
        // (근로기준법 제2조 제1항 제7호, OvertimeLimitChecker 와 같은 해석) 연장·휴일 1주 한도의 합도 법정 한도를 넘을 수 없다.
        int otWeek = capOf(p.overtime());
        int holWeek = capOf(p.holiday());
        if (otWeek > weeklyOvertimeLimitMinutes || holWeek > weeklyOvertimeLimitMinutes
                || (otWeek >= 0 && holWeek >= 0 && otWeek + holWeek > weeklyOvertimeLimitMinutes)) {
            v.add(new Violation(Code.POLICY_OVERTIME_LIMIT_EXCEEDED, "연장·휴일근로 1주 한도(합계)가 법정 한도 "
                    + WorkPatternRules.hm(weeklyOvertimeLimitMinutes) + "을 넘어요(근로기준법 제53조 제1항, 휴일근로 포함)"));
        }
        // 근로기준법 제54조 제1항: 1일 연장 한도까지 일한 근로시간에 필요한 휴게를 근무유형이 주지 못하면 거부한다.
        Integer otDay = p.overtime().mode() == AllowMode.LIMIT ? p.overtime().dayLimit() : null;
        if (pattern != null && limits != null && otDay != null) {
            int daily = WorkPatternRules.spanMinutes(pattern.workStartMin(), pattern.workEndMin()) - pattern.breakMinutes();
            int required = WorkPatternRules.requiredBreakMinutes(daily + otDay, limits);
            if (pattern.breakMinutes() < required) {
                v.add(new Violation(Code.POLICY_BREAK_INSUFFICIENT, "1일 연장 한도까지 일하면 근로시간 "
                        + WorkPatternRules.hm(daily + otDay) + "에 휴게 " + WorkPatternRules.hm(required)
                        + " 이상이 필요한데 근무유형 휴게는 " + WorkPatternRules.hm(pattern.breakMinutes())
                        + "이에요(근로기준법 제54조 제1항)"));
            }
        }
        return List.copyOf(v);
    }

    /** 1주 한도로 쓸 값(분). 주 한도가 없으면 일 한도, 둘 다 없으면 -1(한도 없음). */
    private static int capOf(Allowance a) {
        if (a.mode() == AllowMode.DENY) {
            return 0;
        }
        if (a.mode() != AllowMode.LIMIT) {
            return -1;
        }
        return a.weekLimit() != null ? a.weekLimit() : -1;
    }

    private static void checkAllowance(List<Violation> v, String label, Allowance a) {
        if (a.mode() != AllowMode.LIMIT) {
            if (a.dayLimit() != null || a.weekLimit() != null || a.monthLimit() != null) {
                v.add(invalid(label + " 한도는 '한도' 방식일 때만 넣어 주세요"));
            }
            return;
        }
        if (a.dayLimit() == null && a.weekLimit() == null && a.monthLimit() == null) {
            v.add(invalid(label + " 한도를 일·주·월 중 하나 이상 넣어 주세요"));
            return;
        }
        if (negative(a.dayLimit()) || negative(a.weekLimit()) || negative(a.monthLimit())) {
            v.add(invalid(label + " 한도는 0분 이상이어야 해요"));
            return;
        }
        if (greater(a.dayLimit(), a.weekLimit()) || greater(a.weekLimit(), a.monthLimit())
                || greater(a.dayLimit(), a.monthLimit())) {
            v.add(invalid(label + " 한도는 일 ≤ 주 ≤ 월 순이어야 해요"));
        }
    }

    private static boolean negative(Integer x) {
        return x != null && x < 0;
    }

    private static boolean greater(Integer a, Integer b) {
        return a != null && b != null && a > b;
    }

    private static Violation invalid(String msg) {
        return new Violation(Code.INVALID_POLICY, msg);
    }

    /**
     * 인정 시간: 최소 인정 시간 미만은 0, 그 밖에는 인정 단위로 올림/내림. 회사 정책 값이며 법정 수치가 아니다.
     * 예) 단위 15분·올림: 7분 → 15분.
     */
    public static int recognize(int minutes, Policy p) {
        if (minutes <= 0 || minutes < p.minRecognizeMinutes()) {
            return 0;
        }
        int rest = minutes % p.unitMinutes();
        if (rest == 0) {
            return minutes;
        }
        return p.rounding() == Rounding.UP ? minutes - rest + p.unitMinutes() : minutes - rest;
    }

    /** 집계에 쓸 근무 구간과 표시할 사유. start·end 가 null 이면 실근로 0분. */
    public record Recorded(LocalDateTime start, LocalDateTime end, List<String> issues) {
    }

    static final String ISSUE_MISSING_OUT = "MISSING_OUT";
    /** 퇴근 누락을 근무유형 종업 시각으로 채움. */
    static final String ISSUE_OUT_SET_TO_SCHEDULE = "OUT_SET_TO_SCHEDULE";
    /** 퇴근 누락 — 다음 날 확인 요청. */
    static final String ISSUE_OUT_CONFIRM_REQUESTED = "OUT_CONFIRM_REQUESTED";

    /**
     * 출퇴근 기록(첫 출근·마지막 퇴근)을 정책으로 집계 구간으로 바꾼다.
     *
     * @param schedStart·schedEnd 그날 근무유형 시업·종업(소정근로일이 아니거나 근무유형이 없으면 null)
     */
    public static Recorded record(Policy p, LocalDateTime in, LocalDateTime out, LocalDateTime schedStart,
            LocalDateTime schedEnd) {
        if (in == null) {
            return new Recorded(null, null, List.of());
        }
        boolean scheduled = schedStart != null && schedEnd != null;
        if (scheduled && !p.realtimeReflect()) {
            // 실시간 기록을 반영하지 않으면 출근 기록이 있는 소정근로일은 계획(근무유형 시업~종업) 기준.
            return new Recorded(schedStart, schedEnd, List.of());
        }
        LocalDateTime start = in;
        if (scheduled) {
            if (!p.earlyInAllowed() && start.isBefore(schedStart)) {
                start = schedStart;
            }
            if (within(start, schedStart, p.onTimeInGraceMinutes())) {
                start = schedStart;
            }
        }
        if (out == null) {
            return switch (p.missingOut()) {
                case SCHEDULED_END -> scheduled && schedEnd.isAfter(start)
                        ? new Recorded(start, schedEnd, List.of(ISSUE_MISSING_OUT, ISSUE_OUT_SET_TO_SCHEDULE))
                        : new Recorded(start, null, List.of(ISSUE_MISSING_OUT));
                case IN_ONLY -> new Recorded(start, null, List.of(ISSUE_MISSING_OUT));
                case CONFIRM_NEXT_DAY -> new Recorded(start, null,
                        List.of(ISSUE_MISSING_OUT, ISSUE_OUT_CONFIRM_REQUESTED));
            };
        }
        LocalDateTime end = out;
        if (scheduled && within(end, schedEnd, p.onTimeOutGraceMinutes())) {
            end = schedEnd;
        }
        if (!end.isAfter(start)) {
            return new Recorded(start, null, List.of());
        }
        return new Recorded(start, end, List.of());
    }

    private static boolean within(LocalDateTime t, LocalDateTime target, int graceMinutes) {
        return graceMinutes > 0 && Math.abs(Duration.between(target, t).toMinutes()) <= graceMinutes;
    }

    /**
     * 계산 결과에 인정 규칙을 적용한다. 법정 근로시간 내 연장(법내 초과)을 구분하지 않으면 연장으로 합친다.
     * 연장·휴일·야간 분을 인정 단위로 맞추고, 실근로는 바뀐 연장·휴일 분만큼 함께 바꾼다(야간은 겹침 표시라 더하지 않는다).
     *
     * @param holidayThresholdMinutes 휴일근로 기준시간(근로기준법 제56조 제2항, legal_rate 적용값)
     */
    public static DayResult recognizeDay(DayResult r, Policy p, int holidayThresholdMinutes) {
        int extra = r.extraMinutes();
        int overtime = r.overtimeMinutes();
        if (!p.separateStatutoryExtra()) {
            overtime += extra;
            extra = 0;
        }
        int ot = recognize(overtime, p);
        int holidayTotal = r.holidayMinutes() + r.holidayExcessMinutes();
        int hol = recognize(holidayTotal, p);
        // 근로기준법 제56조 제2항: 기준시간 이내·초과를 나눈다.
        int holWithin = Math.min(hol, holidayThresholdMinutes);
        int worked = r.workedMinutes() + (ot - overtime) + (hol - holidayTotal);
        return new DayResult(r.workDate(), r.dayKind(), r.contractMinutes(), r.leaveKind(), r.leaveMinutes(),
                r.breakMinutes(), worked, r.regularMinutes(), extra, ot, recognize(r.nightMinutes(), p), holWithin,
                hol - holWithin);
    }

    /**
     * 허용 방식 위반 표시(관리자 확인 대상). 실제로 일한 시간은 줄이지 않는다(근로기준법 제43조 임금 전액 지급).
     *
     * @return 위반이면 "{prefix}_POLICY_DENIED" 또는 "{prefix}_POLICY_EXCEEDED", 아니면 null
     */
    public static String allowanceIssue(String prefix, Allowance a, int dayMinutes, int weekMinutes) {
        if (dayMinutes <= 0) {
            return null;
        }
        if (a.mode() == AllowMode.DENY) {
            return prefix + "_POLICY_DENIED";
        }
        if (a.mode() == AllowMode.LIMIT && (greater(dayMinutes, a.dayLimit()) || greater(weekMinutes, a.weekLimit()))) {
            return prefix + "_POLICY_EXCEEDED";
        }
        return null;
    }
}
