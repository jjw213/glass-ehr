package com.glassehr.attendance;

/**
 * 근로자 유형(#158). 근로계약·근무유형의 소정근로시간으로 정한다({@link WorkerTypeRules#classify}).
 *
 * <ul>
 *   <li>{@link #FULL_TIME} 통상근로자</li>
 *   <li>{@link #PART_TIME} 단시간근로자 — 근로기준법 제2조 제1항 제9호: 1주 소정근로시간이 통상근로자보다 짧은 근로자.
 *       제18조 제1항·시행령 제9조 제1항 [별표 2]: 주휴·연차를 통상근로자 대비 시간 비례로 준다.</li>
 *   <li>{@link #ULTRA_SHORT} 초단시간근로자 — 제18조 제3항: 4주 동안(4주 미만이면 그 기간) 평균 1주 소정근로시간이
 *       legal_rate {@code SHORT_TIME_WORKER_WEEKLY_HOURS_THRESHOLD} 미만이면 제55조(주휴)·제60조(연차)를 적용하지 않는다.</li>
 * </ul>
 */
public enum WorkerType {
    FULL_TIME,
    PART_TIME,
    ULTRA_SHORT;

    /** 근로기준법 제18조 제3항: 초단시간근로자는 제55조(주휴)·제60조(연차) 미적용. */
    public boolean weeklyHolidayAndAnnualLeaveApply() {
        return this != ULTRA_SHORT;
    }
}
