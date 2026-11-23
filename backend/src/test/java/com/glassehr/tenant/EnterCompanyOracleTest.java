package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.tenant.EnterCompanyController.EnterCompanyView;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 V031(법인 마스터·enter_cd 열)과 EnterCompany 매핑(ddl-auto: validate)을 검증한다(#118).
 * 실행 조건: {@code ORACLE_IT=true}. 스키마가 없으면 {@link com.glassehr.OracleItSchemaConfig}가 V031을 적용한다.
 * 각 테스트는 롤백한다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class EnterCompanyOracleTest {

    @Autowired
    private EnterCompanyController controller;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 법인별_테이블마다_enter_cd_열과_FK_CHECK가_있다() throws Exception {
        for (String t : TenantSchemaScriptTest.tenantTables()) {
            String table = t.toUpperCase(Locale.ROOT);
            List<Map<String, Object>> cols = jdbc.queryForList(
                    "SELECT data_type, char_length, nullable FROM user_tab_columns"
                            + " WHERE table_name = ? AND column_name = 'ENTER_CD'", table);
            assertThat(cols).as(table).hasSize(1);
            assertThat(cols.get(0).get("DATA_TYPE")).as(table).isEqualTo("VARCHAR2");
            assertThat(((Number) cols.get(0).get("CHAR_LENGTH")).intValue()).as(table).isEqualTo(10);
            boolean nullable = TenantSchemaScriptTest.NULLABLE_NO_FK.contains(t);
            assertThat(cols.get(0).get("NULLABLE")).as(table).isEqualTo(nullable ? "Y" : "N");
            Integer fk = jdbc.queryForObject("SELECT COUNT(*) FROM user_constraints c"
                    + " JOIN user_cons_columns cc ON cc.constraint_name = c.constraint_name"
                    + " JOIN user_constraints p ON p.constraint_name = c.r_constraint_name"
                    + " WHERE c.table_name = ? AND c.constraint_type = 'R' AND cc.column_name = 'ENTER_CD'"
                    + " AND p.table_name = 'ENTER_COMPANY'", Integer.class, table);
            assertThat(fk).as(table + " FK").isEqualTo(nullable ? 0 : 1);
        }
    }

    @Test
    void 기존_행은_모두_C001이다() throws Exception {
        for (String t : TenantSchemaScriptTest.tenantTables()) {
            Integer other = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + t + " WHERE enter_cd IS NULL OR enter_cd <> 'C001'", Integer.class);
            assertThat(other).as(t).isZero();
        }
    }

    @Test
    void 회사_목록은_사용중인_법인만_정렬순서대로_코드와_이름을_준다() {
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)"
                + " VALUES ('ZZIT1', 'IT 법인 가(예시)', '0000000091', 'Y', 0)");
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)"
                + " VALUES ('ZZIT2', 'IT 법인 나(예시)', '0000000092', 'N', 0)");

        List<EnterCompanyView> list = controller.list();

        assertThat(list).extracting(EnterCompanyView::enterCd).startsWith("ZZIT1").contains("C001")
                .doesNotContain("ZZIT2");
        assertThat(list).filteredOn(v -> v.enterCd().equals("C001")).singleElement()
                .satisfies(v -> assertThat(v.enterName()).isEqualTo("기본 법인(예시)"));
    }

    @Test
    void DB는_형식이_틀린_법인코드와_사업자등록번호를_거부한다() {
        for (String cd : List.of("c001", "C", "C-01")) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no)"
                    + " VALUES (?, '틀린 법인', '0000000093')", cd)).as(cd)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no)"
                + " VALUES ('ZZIT3', '틀린 번호', '00000A0000')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no)"
                + " VALUES ('ZZIT4', '중복 번호', '0000000000')")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void DB는_없는_법인을_가리키는_행을_거부한다() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code_group (group_code, group_name, enter_cd)"
                + " VALUES ('ZZ_IT_ENTER', 'IT 그룹', 'ZZNONE')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code_group (group_code, group_name, enter_cd)"
                + " VALUES ('ZZ_IT_ENTER', 'IT 그룹', 'c001')")).isInstanceOf(DataIntegrityViolationException.class);
        // 법인을 빠뜨리면(지금 코드) DEFAULT 로 C001 이 된다.
        jdbc.update("INSERT INTO common_code_group (group_code, group_name) VALUES ('ZZ_IT_ENTER', 'IT 그룹')");
        assertThat(jdbc.queryForObject("SELECT enter_cd FROM common_code_group WHERE group_code = 'ZZ_IT_ENTER'",
                String.class)).isEqualTo("C001");
    }
}
