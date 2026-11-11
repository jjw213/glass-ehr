package com.glassehr.legalrate;

/**
 * 인사·근태 모듈이 쓰는 legal_rate 항목코드(#7). 코드·단위만 두고 값은 두지 않는다.
 *
 * <p>DB 등록은 {@code db/oracle/V003__legal_rate_items_hr_attendance.sql}과
 * {@code V005__legal_rate_items_hr_attendance_ext.sql}(추가 18개, 합계 47개)이며, 이 enum과 목록·단위가 같아야 한다
 * (LegalRateItemRegistrationTest). 근거 조문은 legal_rate_item.legal_basis 에 있다.
 * 값은 {@link LegalRateService#getEffective(LegalRateItem, java.time.LocalDate)}로 적용일 기준 조회한다.
 */
public enum LegalRateItem {

    // 근로기준법 제53조 제1항: 1주 연장근로 한도
    WEEKLY_OVERTIME_LIMIT_HOURS(LegalRateUnit.HOURS),

    // 근로기준법 제56조: 연장·야간·휴일 근로 가산
    OVERTIME_PREMIUM_RATE(LegalRateUnit.PERCENT),
    HOLIDAY_PREMIUM_RATE(LegalRateUnit.PERCENT),
    HOLIDAY_EXCESS_PREMIUM_RATE(LegalRateUnit.PERCENT),
    HOLIDAY_PREMIUM_THRESHOLD_HOURS(LegalRateUnit.HOURS),
    NIGHT_PREMIUM_RATE(LegalRateUnit.PERCENT),
    NIGHT_WORK_START_HOUR(LegalRateUnit.HOUR_OF_DAY),
    NIGHT_WORK_END_HOUR(LegalRateUnit.HOUR_OF_DAY),

    // 근로기준법 제60조: 연차 유급휴가
    ANNUAL_LEAVE_BASE_DAYS(LegalRateUnit.DAYS),
    ANNUAL_LEAVE_ATTENDANCE_RATE(LegalRateUnit.PERCENT),
    ANNUAL_LEAVE_MONTHLY_DAYS(LegalRateUnit.DAYS),
    ANNUAL_LEAVE_INCREMENT_BASE_YEARS(LegalRateUnit.YEARS),
    ANNUAL_LEAVE_INCREMENT_CYCLE_YEARS(LegalRateUnit.YEARS),
    ANNUAL_LEAVE_INCREMENT_DAYS(LegalRateUnit.DAYS),
    ANNUAL_LEAVE_MAX_DAYS(LegalRateUnit.DAYS),

    // 근로기준법 제73조: 생리휴가
    MENSTRUAL_LEAVE_DAYS_PER_MONTH(LegalRateUnit.DAYS),

    // 근로기준법 제74조 제1항: 출산전후휴가
    MATERNITY_LEAVE_DAYS(LegalRateUnit.DAYS),
    MATERNITY_LEAVE_MULTIPLE_DAYS(LegalRateUnit.DAYS),
    MATERNITY_LEAVE_POSTNATAL_MIN_DAYS(LegalRateUnit.DAYS),
    MATERNITY_LEAVE_POSTNATAL_MIN_MULTIPLE_DAYS(LegalRateUnit.DAYS),
    // 근로기준법 제74조 제1항: 미숙아 출산 시 출산전후휴가 일수 (V005)
    MATERNITY_LEAVE_PREMATURE_DAYS(LegalRateUnit.DAYS),

    // 근로기준법 제74조 제3항, 같은 법 시행령 제43조: 유산·사산휴가 임신기간 구간별 일수 (V005)
    // TIER는 시행령 제43조 제3항의 호 번호. 해당 기준일에 유효한 구간의 다음 시작 전까지다.
    // 2025-02-23부터 제2호가 삭제되어 TIER2 값이 없다. 0으로 대체하거나 5개를 강제 조회하지 않는다.
    // 실제 임신기간별 휴가 계산은 별도 구현하며, 이 enum은 항목 조회만 제공한다.
    MISCARRIAGE_LEAVE_TIER1_FROM_WEEKS(LegalRateUnit.WEEKS),
    MISCARRIAGE_LEAVE_TIER1_DAYS(LegalRateUnit.DAYS),
    MISCARRIAGE_LEAVE_TIER2_FROM_WEEKS(LegalRateUnit.WEEKS),
    MISCARRIAGE_LEAVE_TIER2_DAYS(LegalRateUnit.DAYS),
    MISCARRIAGE_LEAVE_TIER3_FROM_WEEKS(LegalRateUnit.WEEKS),
    MISCARRIAGE_LEAVE_TIER3_DAYS(LegalRateUnit.DAYS),
    MISCARRIAGE_LEAVE_TIER4_FROM_WEEKS(LegalRateUnit.WEEKS),
    MISCARRIAGE_LEAVE_TIER4_DAYS(LegalRateUnit.DAYS),
    MISCARRIAGE_LEAVE_TIER5_FROM_WEEKS(LegalRateUnit.WEEKS),
    MISCARRIAGE_LEAVE_TIER5_DAYS(LegalRateUnit.DAYS),

    // 근로기준법 제74조 제7항: 임신기 근로시간 단축 (V005)
    PREGNANCY_REDUCED_HOURS_EARLY_WEEKS(LegalRateUnit.WEEKS),
    PREGNANCY_REDUCED_HOURS_LATE_FROM_WEEKS(LegalRateUnit.WEEKS),
    PREGNANCY_REDUCED_HOURS_PER_DAY(LegalRateUnit.HOURS),
    PREGNANCY_REDUCED_SHORT_DAY_THRESHOLD_HOURS(LegalRateUnit.HOURS),
    PREGNANCY_REDUCED_SHORT_DAY_TARGET_HOURS(LegalRateUnit.HOURS),

    // 남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2: 배우자 출산휴가
    SPOUSE_MATERNITY_LEAVE_DAYS(LegalRateUnit.DAYS),
    SPOUSE_MATERNITY_LEAVE_CLAIM_DAYS(LegalRateUnit.DAYS),
    SPOUSE_MATERNITY_LEAVE_SPLIT_COUNT(LegalRateUnit.COUNT), // V005

    // 같은 법 제19조: 육아휴직 (분할 사용은 제19조의4 제1항)
    PARENTAL_LEAVE_MAX_MONTHS(LegalRateUnit.MONTHS),
    PARENTAL_LEAVE_EXTENSION_MONTHS(LegalRateUnit.MONTHS),
    PARENTAL_LEAVE_SPLIT_COUNT(LegalRateUnit.COUNT), // V005

    // 같은 법 제19조의2: 육아기 근로시간 단축
    CHILDCARE_REDUCED_HOURS_MAX_MONTHS(LegalRateUnit.MONTHS),
    CHILDCARE_REDUCED_WEEKLY_HOURS_MIN(LegalRateUnit.HOURS),
    CHILDCARE_REDUCED_WEEKLY_HOURS_MAX(LegalRateUnit.HOURS),

    // 같은 법 제22조의2: 가족돌봄휴직·가족돌봄휴가
    FAMILY_CARE_LEAVE_OF_ABSENCE_DAYS_PER_YEAR(LegalRateUnit.DAYS),
    FAMILY_CARE_LEAVE_DAYS_PER_YEAR(LegalRateUnit.DAYS);

    private final String unit;

    LegalRateItem(String unit) {
        this.unit = unit;
    }

    /** legal_rate.item_code 값. */
    public String code() {
        return name();
    }

    /** 이 항목 값 행이 가져야 하는 단위({@link LegalRateUnit}). */
    public String unit() {
        return unit;
    }
}
