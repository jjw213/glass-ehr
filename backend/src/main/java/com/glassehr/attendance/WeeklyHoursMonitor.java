package com.glassehr.attendance;

import java.util.List;
import java.util.Objects;

/**
 * 주 52시간 모니터링 판정(#31). 순수 함수 — 한 사원의 한 주(월~일) 일별 집계 행과 한도로 상태를 정한다.
 *
 * <ul>
 *   <li>근로기준법 제2조 제1항 제7호: 1주는 휴일을 포함한 7일 → 휴일근로도 그 주 누적 근로시간에 넣는다.</li>
 *   <li>근로기준법 제50조 제1항: 1주 근로시간(휴게 제외) {@code WEEKLY_STANDARD_WORK_HOURS}.</li>
 *   <li>근로기준법 제53조 제1항: 1주 연장 한도 {@code WEEKLY_OVERTIME_LIMIT_HOURS}.</li>
 * </ul>
 * 두 가지를 함께 본다. (1) 누적 실근로(휴일 포함)가 제50조 제1항 + 제53조 제1항 시간을 넘는가,
 * (2) 1일·1주 기준을 넘은 연장(#29 집계의 overtime)과 휴일 1일 기준 초과분(holiday_excess)의 합이 제53조 제1항 한도를 넘는가.
 * 하나라도 넘으면 OVER, 남은 시간이 회사 설정 경고 기준 이하이면 NEAR. 한도와 같으면 넘은 것이 아니므로 NEAR.
 * 정답 사례 fixture 대조는 아직 없다(docs/weekly-hours-monitor-design.md).
 */
public final class WeeklyHoursMonitor {

    private WeeklyHoursMonitor() {
    }

    public enum Status { NORMAL, NEAR, OVER }

    /**
     * @param standardMinutes   제50조 제1항 1주 근로시간(분)
     * @param overtimeMinutes   제53조 제1항 1주 연장 한도(분)
     * @param warnRemainMinutes 남은 시간이 이 값 이하이면 NEAR(회사 설정, 법정 수치 아님)
     */
    public record Limits(int standardMinutes, int overtimeMinutes, int warnRemainMinutes) {
        public Limits {
            if (standardMinutes <= 0 || overtimeMinutes < 0 || warnRemainMinutes < 0) {
                throw new IllegalArgumentException("주간 한도 값이 잘못됐어요");
            }
        }

        public int totalMinutes() {
            return standardMinutes + overtimeMinutes;
        }
    }

    /**
     * 한 주 결과. 시간은 분.
     *
     * @param workedMinutes       실근로 합(휴일 포함, 게이지 값)
     * @param regularMinutes      소정근로 합
     * @param extraMinutes        법내 초과 합
     * @param overtimeMinutes     연장 합(1일·1주 기준 초과, 휴일 제외)
     * @param holidayMinutes      휴일근로 합(1일 기준 이내 + 초과)
     * @param limitOvertimeMinutes 제53조 한도 대상 연장(overtime + holiday_excess)
     * @param missingOut          퇴근 기록이 없는 날이 있어 일부 근무가 빠졌을 수 있음
     */
    public record Week(int workedMinutes, int regularMinutes, int extraMinutes, int overtimeMinutes,
            int holidayMinutes, int limitOvertimeMinutes, int limitMinutes, int overtimeLimitMinutes,
            int remainingMinutes, int overtimeRemainingMinutes, Status status, boolean missingOut) {
    }

    public static Week judge(List<DailyWorkSummaryRow> rows, Limits limits) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(limits, "limits");
        int worked = 0;
        int regular = 0;
        int extra = 0;
        int overtime = 0;
        int holiday = 0;
        int holidayExcess = 0;
        boolean missingOut = false;
        for (DailyWorkSummaryRow r : rows) {
            worked += n(r.workedMinutes());
            regular += n(r.regularMinutes());
            extra += n(r.extraMinutes());
            overtime += n(r.overtimeMinutes());
            holiday += n(r.holidayMinutes()) + n(r.holidayExcessMinutes());
            holidayExcess += n(r.holidayExcessMinutes());
            missingOut |= r.issues() != null && List.of(r.issues().split(",")).contains(WorkHoursService.ISSUE_MISSING_OUT);
        }
        int limitOvertime = overtime + holidayExcess;
        int remaining = limits.totalMinutes() - worked;
        int overtimeRemaining = limits.overtimeMinutes() - limitOvertime;
        Status status;
        if (remaining < 0 || overtimeRemaining < 0) {
            status = Status.OVER;
        } else if (remaining <= limits.warnRemainMinutes() || overtimeRemaining <= limits.warnRemainMinutes()) {
            status = Status.NEAR;
        } else {
            status = Status.NORMAL;
        }
        return new Week(worked, regular, extra, overtime, holiday, limitOvertime, limits.totalMinutes(),
                limits.overtimeMinutes(), remaining, overtimeRemaining, status, missingOut);
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}
