package com.glassehr.tenant;

import java.util.Arrays;
import java.util.Optional;

/**
 * 법인별 회사 설정 키(#131, docs/tenant-design.md 7장). 모두 법정 수치가 아니라 회사 운영 설정이다.
 * 법인에 값이 없으면 {@link #property} 의 application.yml 값을 기본값으로 쓴다(새 법인의 기본값).
 * 범위(min·max)는 입력 검증용 운영 한계이고 법령 기준이 아니다.
 */
public enum EnterSettingKey {
    /**
     * 그 법인(사업)의 상시근로자 수(명). 규모별로 시행일이 다른 조문(근로기준법 제55조 제2항·부칙 등)은
     * legal_rate 의 적용 규모 하한과 이 값을 비교한다({@link com.glassehr.attendance.PublicHolidayPaidRule}).
     */
    WORKPLACE_REGULAR_HEADCOUNT("glassehr.workplace.regular-headcount", "상시근로자 수", "명", 1, 1_000_000),
    /** 근로계약 만료 알림 일수(#17). */
    CONTRACT_EXPIRY_ALERT_DAYS("glassehr.contract.expiry-alert-days", "근로계약 만료 알림", "일", 0, 3650),
    /** 기간제 사용기간 상한 알림 일수(#17). 상한 자체(기간제법 제4조)는 legal_rate. */
    CONTRACT_FIXED_TERM_LIMIT_ALERT_DAYS("glassehr.contract.fixed-term-limit-alert-days", "기간제 사용기간 상한 알림",
            "일", 0, 3650),
    /** 근로자대표 서면합의서 만료 예정 알림 일수(#27). */
    AGREEMENT_EXPIRY_ALERT_DAYS("glassehr.agreement.expiry-alert-days", "서면합의서 만료 예정 알림", "일", 0, 3650),
    /** 주 52시간 한도까지 남은 분이 이 값 이하면 '한도 근접'(#31). 한도 자체(제50조·제53조)는 legal_rate. */
    WEEKLY_HOURS_WARN_REMAIN_MINUTES("glassehr.attendance.weekly-hours-warn-remain-minutes", "주 52시간 근접 경고 기준",
            "분", 0, 10_080);

    final String property;
    final String label;
    final String unit;
    final int min;
    final int max;

    EnterSettingKey(String property, String label, String unit, int min, int max) {
        this.property = property;
        this.label = label;
        this.unit = unit;
        this.min = min;
        this.max = max;
    }

    boolean inRange(long value) {
        return value >= min && value <= max;
    }

    static Optional<EnterSettingKey> parse(String name) {
        return Arrays.stream(values()).filter(k -> k.name().equals(name)).findFirst();
    }
}
