package com.glassehr.tenant;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

/** 단위 테스트용: 법인 값이 하나도 없고 기본값만 있는 설정 서비스(#131). */
public final class EnterSettingTestSupport {

    private EnterSettingTestSupport() {
    }

    /** 주어진 키는 그 값, 나머지 키는 application.yml 기본값과 같은 값. */
    public static EnterSettingService defaults(Map<EnterSettingKey, Integer> values) {
        Map<EnterSettingKey, Integer> m = new EnumMap<>(EnterSettingKey.class);
        m.put(EnterSettingKey.WORKPLACE_REGULAR_HEADCOUNT, 30);
        m.put(EnterSettingKey.CONTRACT_EXPIRY_ALERT_DAYS, 30);
        m.put(EnterSettingKey.CONTRACT_FIXED_TERM_LIMIT_ALERT_DAYS, 30);
        m.put(EnterSettingKey.AGREEMENT_EXPIRY_ALERT_DAYS, 30);
        m.put(EnterSettingKey.WEEKLY_HOURS_WARN_REMAIN_MINUTES, 240);
        m.putAll(values);
        // mock 저장소는 findEffective 에 Optional.empty() 를 돌려준다 = 법인 값 없음 → 기본값.
        return new EnterSettingService(mock(EnterSettingRepository.class), m, Clock.systemUTC());
    }
}
