package com.glassehr.attendance;

import java.util.List;
import java.util.Objects;

/**
 * 근로자 유형 판정과 단시간근로자 비례 주휴·연차 시간 계산(#158). 법정 수치는 {@link Thresholds}로 받는다
 * (legal_rate 적용값, {@link WorkerTypeLegalRules}). 이 클래스에는 법정 수치를 쓰지 않는다. 시간은 모두 분 단위 정수.
 * 정답: tests/legal-fixtures/part-time-worker-type.csv, part-time-proportional-leave.csv.
 */
public final class WorkerTypeRules {

    /** 시간 → 분(시계 단위, 법정 수치 아님). */
    static final int MINUTES_PER_HOUR = 60;

    private WorkerTypeRules() {
    }

    /**
     * legal_rate 에서 읽은 기준값(분·주 수).
     *
     * @param ultraShortWeeklyMinutes 근로기준법 제18조 제3항: 이 값 미만이면 초단시간(SHORT_TIME_WORKER_WEEKLY_HOURS_THRESHOLD)
     * @param averageWeeks 같은 항: 평균을 내는 주 수(SHORT_TIME_WORKER_AVERAGE_WEEKS)
     * @param annualLeaveHourMinutes 시행령 [별표 2] 제4호나목 연차 산식의 "8시간"(DAILY_STANDARD_WORK_HOURS, 제50조 제2항)
     */
    public record Thresholds(int ultraShortWeeklyMinutes, int averageWeeks, int annualLeaveHourMinutes) {
        public Thresholds {
            if (ultraShortWeeklyMinutes <= 0 || averageWeeks <= 0 || annualLeaveHourMinutes <= 0) {
                throw new IllegalArgumentException("기준값은 1 이상이어야 해요");
            }
        }
    }

    /**
     * 근로자 유형. weeklyMinutes 는 평균을 낼 기간의 주별 소정근로시간(분)이다.
     * 근로기준법 제18조 제3항: 4주 동안(4주 미만으로 근로하는 경우에는 그 기간) 평균하여 1주 소정근로시간이
     * 기준 미만이면 초단시간. 제2조 제1항 제9호: 1주 소정근로시간이 통상근로자보다 짧으면 단시간.
     * 평균은 나누지 않고 합계끼리 비교한다(끝수 오차 없음).
     */
    public static WorkerType classify(List<Integer> weeklyMinutes, int fullTimeWeeklyMinutes, Thresholds t) {
        Objects.requireNonNull(t, "thresholds");
        if (weeklyMinutes == null || weeklyMinutes.isEmpty() || weeklyMinutes.size() > t.averageWeeks()) {
            throw new IllegalArgumentException("주별 소정근로시간은 1~" + t.averageWeeks() + "주를 주어야 해요");
        }
        if (fullTimeWeeklyMinutes <= 0) {
            throw new IllegalArgumentException("통상근로자 1주 소정근로시간은 1분 이상이어야 해요");
        }
        long sum = 0;
        for (Integer m : weeklyMinutes) {
            if (m == null || m < 0) {
                throw new IllegalArgumentException("주별 소정근로시간은 0분 이상이어야 해요");
            }
            sum += m;
        }
        long weeks = weeklyMinutes.size();
        if (sum < (long) t.ultraShortWeeklyMinutes() * weeks) {
            return WorkerType.ULTRA_SHORT;
        }
        if (sum < (long) fullTimeWeeklyMinutes * weeks) {
            return WorkerType.PART_TIME;
        }
        return WorkerType.FULL_TIME;
    }

    /** 매주 같은 소정근로시간(근로계약·근무유형)일 때. */
    public static WorkerType classify(int weeklyMinutes, int fullTimeWeeklyMinutes, Thresholds t) {
        return classify(List.of(weeklyMinutes), fullTimeWeeklyMinutes, t);
    }

    /**
     * 단시간근로자 연차 유급휴가 시간(분).
     * 근로기준법 시행령 제9조 제1항 [별표 2] 제4호나목: 통상근로자의 연차휴가일수 × (단시간근로자의 소정근로시간
     * ÷ 통상근로자의 소정근로시간) × 8시간. 시간 단위로 계산하고 1시간 미만은 1시간으로 본다(올림).
     * 근로기준법 제18조 제3항: 초단시간이면 제60조 미적용 → 0.
     */
    public static int proportionalAnnualLeaveMinutes(WorkerType type, int fullTimeLeaveDays, int weeklyMinutes,
            int fullTimeWeeklyMinutes, Thresholds t) {
        Objects.requireNonNull(type, "type");
        if (fullTimeLeaveDays < 0 || weeklyMinutes <= 0 || fullTimeWeeklyMinutes <= 0) {
            throw new IllegalArgumentException("연차일수는 0 이상, 소정근로시간은 1분 이상이어야 해요");
        }
        if (!type.weeklyHolidayAndAnnualLeaveApply()) {
            return 0;
        }
        // 분 = 일수 × 단시간 ÷ 통상 × 8시간(분). 시간 = 분 ÷ 60 을 1시간 미만 올림.
        long numerator = (long) fullTimeLeaveDays * weeklyMinutes * t.annualLeaveHourMinutes();
        long denominator = (long) fullTimeWeeklyMinutes * MINUTES_PER_HOUR;
        long hours = Math.ceilDiv(numerator, denominator);
        return Math.toIntExact(hours * MINUTES_PER_HOUR);
    }

    /**
     * 단시간근로자 유급휴일(주휴)에 줄 1일 소정근로시간(분).
     * 근로기준법 시행령 [별표 2] 제4호나목·제5호나목: 4주 동안의 단시간근로자 총 소정근로시간 ÷ 같은 기간 통상근로자의
     * 총 소정근로일수. 근로기준법 제18조 제3항: 초단시간이면 제55조 미적용 → 0.
     * 원문에 끝수 처리 규정이 없어 분 미만은 버린다(AGENTS.md: 근태 시간은 분 단위 정수).
     */
    public static int paidHolidayMinutes(WorkerType type, long periodContractMinutes, int fullTimePeriodWorkDays) {
        Objects.requireNonNull(type, "type");
        if (periodContractMinutes < 0 || fullTimePeriodWorkDays <= 0) {
            throw new IllegalArgumentException("소정근로시간은 0 이상, 통상근로자 소정근로일수는 1일 이상이어야 해요");
        }
        if (!type.weeklyHolidayAndAnnualLeaveApply()) {
            return 0;
        }
        return Math.toIntExact(periodContractMinutes / fullTimePeriodWorkDays);
    }
}
