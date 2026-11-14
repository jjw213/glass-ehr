package com.glassehr.attendance;

/**
 * 근로자대표와의 서면합의가 전제인 제도(#27). 값은 V019 ck_wra_kind 와 같다(LaborAgreementSchemaScriptTest).
 * HOLIDAY_SUBSTITUTION 은 #26(V017)이 먼저 쓰던 값이라 이름을 그대로 둔다.
 * 조문 표기는 이슈 #27 본문 기준이며, law.go.kr 현행 원문 대조는 docs/labor-agreement-design.md 7장(미확인 표시) 참고.
 */
public enum AgreementType {
    /** 근로기준법 제51조 제2항: 3개월 이내 탄력적 근로시간제(서면합의). */
    FLEXIBLE_WRITTEN("탄력적 근로시간제(3개월 이내)", "근로기준법 제51조 제2항"),
    /** 근로기준법 제51조의2: 3개월을 초과하는 탄력적 근로시간제. */
    FLEXIBLE_LONG("탄력적 근로시간제(3개월 초과)", "근로기준법 제51조의2"),
    /** 근로기준법 제52조: 선택적 근로시간제. */
    SELECTIVE("선택적 근로시간제", "근로기준법 제52조"),
    /** 근로기준법 제55조 제2항 단서: 공휴일을 특정한 근로일로 대체. */
    HOLIDAY_SUBSTITUTION("휴일 대체", "근로기준법 제55조 제2항 단서"),
    /** 근로기준법 제57조: 보상 휴가제(연장·야간·휴일근로 임금 대신 휴가). */
    COMPENSATORY_LEAVE("보상휴가제", "근로기준법 제57조"),
    /** 근로기준법 제58조 제3항: 재량근로. */
    DISCRETIONARY("재량근로", "근로기준법 제58조 제3항"),
    /** 근로기준법 제62조: 유급휴가(연차)의 대체. */
    ANNUAL_LEAVE_SUBSTITUTE("연차 유급휴가 대체", "근로기준법 제62조");

    private final String label;
    private final String article;

    AgreementType(String label, String article) {
        this.label = label;
        this.article = article;
    }

    public String label() {
        return label;
    }

    public String article() {
        return article;
    }
}
