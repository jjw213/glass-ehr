package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 고객사 상태별 로그인 가능 여부(#151, docs/saas-design.md 2.2). Oracle 없이. */
class TenantStatusTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 28, 9, 0);

    @Test
    void 이용중은_가능하고_정지_해지는_거부한다() {
        assertThat(new Tenant("T001", Tenant.Status.ACTIVE, null).usableAt(NOW)).isTrue();
        assertThat(new Tenant("T001", Tenant.Status.SUSPENDED, null).usableAt(NOW)).isFalse();
        assertThat(new Tenant("T001", Tenant.Status.TERMINATED, null).usableAt(NOW)).isFalse();
    }

    @Test
    void 체험은_종료_시각_전까지만_가능하고_종료_시각부터_거부한다() {
        assertThat(new Tenant("T001", Tenant.Status.TRIAL, NOW.plusSeconds(1)).usableAt(NOW)).isTrue();
        assertThat(new Tenant("T001", Tenant.Status.TRIAL, NOW).usableAt(NOW)).isFalse();
        assertThat(new Tenant("T001", Tenant.Status.TRIAL, NOW.minusDays(1)).usableAt(NOW)).isFalse();
        assertThat(new Tenant("T001", Tenant.Status.TRIAL, null).usableAt(NOW)).isFalse();
    }
}
