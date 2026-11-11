package com.glassehr.legalrate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 쓰기 패키지·권한 스크립트의 정적 검사. DB 없이 돈다. 실제 동작은 {@link LegalRateApiOracleTest}. */
class LegalRateApiScriptTest {

    @Test
    void V004는_패키지_명세와_본문_두_블록이다() {
        List<String> blocks = OracleLegalRateSchema.plsqlBlocks("db/oracle/V004__legal_rate_api.sql");
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).startsWith("CREATE OR REPLACE PACKAGE legal_rate_api AUTHID DEFINER");
        assertThat(blocks.get(1)).startsWith("CREATE OR REPLACE PACKAGE BODY legal_rate_api");
        // 잠금 → 겹침 검사 순서를 지킨다(설계 3절).
        String body = blocks.get(1);
        assertThat(body).contains("FOR UPDATE");
        assertThat(body.indexOf("lock_item(p_item_code);")).isLessThan(body.indexOf("check_overlap(p_item_code"));
        // 커밋은 호출자 책임
        assertThat(body).doesNotContainPattern("(?i)\\bCOMMIT\\b");
    }

    @Test
    void 권한_스크립트는_앱_계정에_DML을_주지_않는다() throws IOException {
        String sql = new ClassPathResource("db/oracle/grants/legal_rate_grants.sql")
                .getContentAsString(StandardCharsets.UTF_8)
                .lines().filter(l -> !l.trim().startsWith("--")).reduce("", (a, b) -> a + b + "\n");
        assertThat(sql).doesNotContainPattern("(?i)GRANT\\s+[^;]*(INSERT|UPDATE|DELETE)");
        assertThat(sql).contains("GRANT EXECUTE ON legal_rate_api TO &&app_user");
        assertThat(sql).contains("GRANT SELECT ON legal_rate TO &&app_user");
    }
}
