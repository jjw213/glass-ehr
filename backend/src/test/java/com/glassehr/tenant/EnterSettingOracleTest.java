package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.tenant.EnterSettingService.SettingChange;
import com.glassehr.tenant.EnterSettingService.SettingView;
import com.glassehr.tenant.EnterSettingService.UpdateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V047(#131): 법인별 회사 설정을 Oracle 실DB에서 검증한다. "법인 A(C001) 세션으로 법인 B(ZZIT1) 설정 조회·수정 → 보이지 않고
 * 바뀌지 않음", 이력 FK 가 다른 법인 설정을 가리킬 수 없음을 본다. 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다.
 * 1900년대 날짜, ZZIT 코드만 쓴다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class EnterSettingOracleTest {

    private static final String A = "C001";
    private static final String B = "ZZIT1";
    private static final LocalDate D = LocalDate.of(1901, 3, 1);
    private static final String KEY = "WORKPLACE_REGULAR_HEADCOUNT";

    @Autowired
    private EnterSettingService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void 법인_B와_B의_설정을_넣는다() {
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)"
                + " VALUES (?, 'IT 법인 가(예시)', '0000000094', 'Y', 0)", B);
        service.update(B, KEY, new UpdateRequest(D, BigDecimal.valueOf(7)), "it-b");
    }

    @Test
    void 법인_A_세션은_법인_B_설정과_이력을_보지_못하고_수정해도_B_값은_그대로() {
        int aBefore = service.value(A, EnterSettingKey.WORKPLACE_REGULAR_HEADCOUNT, D);
        assertThat(service.value(B, EnterSettingKey.WORKPLACE_REGULAR_HEADCOUNT, D)).isEqualTo(7);
        assertThat(service.value(B, EnterSettingKey.WORKPLACE_REGULAR_HEADCOUNT, D.minusDays(1)))
                .as("적용시작일 전날은 기본값").isNotEqualTo(7);

        SettingView aView = service.list(A).stream().filter(v -> v.key().equals(KEY)).findFirst().orElseThrow();
        assertThat(aView.entries()).noneMatch(e -> e.effectiveFrom().equals(D) && e.value() == 7);
        assertThat(service.history(A)).noneMatch(c -> c.changedBy().equals("it-b"));

        // 법인 A 세션의 수정은 법인 A 행만 만든다.
        service.update(A, KEY, new UpdateRequest(D, BigDecimal.valueOf(99)), "it-a");
        assertThat(service.value(A, EnterSettingKey.WORKPLACE_REGULAR_HEADCOUNT, D)).isEqualTo(99);
        assertThat(service.value(B, EnterSettingKey.WORKPLACE_REGULAR_HEADCOUNT, D)).isEqualTo(7);
        assertThat(aBefore).isNotEqualTo(99);

        // 법인 B 의 이력은 B 세션에서만 보인다.
        assertThat(service.history(B)).extracting(SettingChange::changedBy).containsExactly("it-b");
        service.update(B, KEY, new UpdateRequest(D, BigDecimal.valueOf(8)), "it-b2");
        assertThat(service.history(B)).first().satisfies(c -> {
            assertThat(c.oldValue()).isEqualTo(7);
            assertThat(c.newValue()).isEqualTo(8);
        });
        assertThat(jdbc.queryForObject("SELECT setting_value FROM enter_setting WHERE enter_cd = ? AND setting_key = ?"
                + " AND effective_from = ?", Integer.class, A, KEY, java.sql.Date.valueOf(D))).isEqualTo(99);
    }

    @Test
    void DB_FK는_다른_법인_설정을_가리키는_이력과_없는_법인_설정을_거부한다() {
        // 법인 A 에는 1901-03-01 설정이 없다 → A 법인 이름으로 B 설정을 가리키는 이력은 거부.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO enter_setting_hist (enter_cd, setting_key, effective_from,"
                + " old_value, new_value, changed_by) VALUES (?, ?, ?, NULL, 1, 'x')", A, KEY, java.sql.Date.valueOf(D)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO enter_setting (enter_cd, setting_key, effective_from,"
                + " setting_value, updated_by) VALUES ('ZZIT9', ?, ?, 1, 'x')", KEY, java.sql.Date.valueOf(D)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
