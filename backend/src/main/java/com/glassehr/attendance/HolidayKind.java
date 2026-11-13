package com.glassehr.attendance;

/** 휴일 종류. V017 holiday.holiday_kind CHECK 와 같아야 한다(HolidaySchemaScriptTest). */
public enum HolidayKind {
    /** 법정공휴일 — 관공서의 공휴일에 관한 규정 제2조. 근로기준법 제55조 제2항·시행령 제30조 제2항 유급휴일 */
    PUBLIC("법정공휴일"),
    /** 대체공휴일 — 관공서의 공휴일에 관한 규정 제3조. 근로기준법 시행령 제30조 제2항 유급휴일 */
    SUBSTITUTE("대체공휴일"),
    /** 회사휴일(창립기념일 등) — 취업규칙·단체협약으로 정함. 유급 여부는 회사가 정한다 */
    COMPANY("회사휴일"),
    /** 대체된 휴일 — 근로기준법 제55조 제2항 단서: 근로자대표 서면합의로 공휴일 대신 휴일이 된 근로일 */
    REPLACEMENT("대체된 휴일");

    private final String label;

    HolidayKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 관공서 공휴일(제55조 제2항 본문 대상). 휴일 대체의 원래 휴일이 될 수 있고, 사람이 넣은 자료로만 만든다. */
    public boolean isStatutory() {
        return this == PUBLIC || this == SUBSTITUTE;
    }

    /** CSV '종류' 열 값(법정공휴일/대체공휴일)으로 찾는다. 공휴일 자료에는 이 두 종류만 쓴다. 없으면 null. */
    public static HolidayKind fromStatutoryLabel(String label) {
        for (HolidayKind k : values()) {
            if (k.isStatutory() && k.label.equals(label)) {
                return k;
            }
        }
        return null;
    }
}
