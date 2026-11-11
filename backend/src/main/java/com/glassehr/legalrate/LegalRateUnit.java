package com.glassehr.legalrate;

/**
 * legal_rate.unit 에 쓰는 단위 코드.
 *
 * <p>요율은 고시 표기와 같은 퍼센트 숫자로 저장한다(예: 고시가 "x.xx%"이면 rate_value = x.xx, unit = PERCENT).
 * 2026-11-11 사람 결정, docs/DECISIONS.md / #3. 계산에 쓸 때는 {@link LegalRateRow#toRatio()}로 변환한다.
 */
public final class LegalRateUnit {

    /** 요율. 퍼센트 숫자(고시 표기 그대로). */
    public static final String PERCENT = "PERCENT";

    private LegalRateUnit() {
    }
}
