package com.glassehr.legalrate;

/**
 * legal_rate.unit / legal_rate_item.unit 에 쓰는 단위 코드. 목록은 V002 의 CHECK 제약과 같아야 한다.
 *
 * <p>요율은 고시 표기와 같은 퍼센트 숫자로 저장한다(예: 고시가 "x.xx%"이면 rate_value = x.xx, unit = PERCENT).
 * 2026-11-11 사람 결정, docs/DECISIONS.md / #3. 계산에 쓸 때는 {@link LegalRateRow#toRatio()}로 변환한다.
 * 시간·일수 등도 같은 원칙으로 조문 표기 단위 그대로 저장하고, 계산하는 쪽에서 변환한다.
 */
public final class LegalRateUnit {

    /** 요율. 퍼센트 숫자(고시 표기 그대로). */
    public static final String PERCENT = "PERCENT";

    /** 시간 수(조문 표기 그대로). 근태 계산은 분 단위이므로 쓰는 쪽에서 60을 곱한다. */
    public static final String HOURS = "HOURS";

    /** 일수. */
    public static final String DAYS = "DAYS";

    /** 개월 수. */
    public static final String MONTHS = "MONTHS";

    /** 연 수. */
    public static final String YEARS = "YEARS";

    /** 하루 중 시각(0~24시, 조문 표기 그대로). */
    public static final String HOUR_OF_DAY = "HOUR_OF_DAY";

    private LegalRateUnit() {
    }
}
