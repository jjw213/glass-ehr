package com.glassehr.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.org.OrgDtos.DeptChangeRequest;
import com.glassehr.org.OrgDtos.DeptCloseRequest;
import com.glassehr.org.OrgDtos.DeptCreateRequest;
import com.glassehr.org.OrgDtos.DeptNode;
import com.glassehr.org.OrgDtos.OrgTree;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 조직 DDL(V008)·JPA 매핑·기준일 조회를 검증한다.
 * 컨텍스트가 뜬다는 것 자체가 엔티티와 V008 스키마가 맞는다는 뜻이다(ddl-auto: validate).
 *
 * <p>실행 조건: 환경변수 {@code ORACLE_IT=true}와 DB_URL/DB_USER/DB_PASSWORD.
 * 스키마가 없으면 {@link com.glassehr.OracleItSchemaConfig}가 V008을 적용한다. 각 테스트는 롤백한다.
 * 다른 데이터와 섞이지 않도록 1900년대 날짜와 ZZIT_ 부서코드를 쓴다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class OrgDeptOracleTest {

    private static final LocalDate D0 = LocalDate.of(1901, 1, 1);

    @Autowired
    private OrgDeptService service;

    @Autowired
    private JdbcTemplate jdbc;

    private static DeptNode find(OrgTree t, String code) {
        return t.roots().stream().filter(n -> n.deptCode().equals(code)).findFirst().orElse(null);
    }

    @Test
    void 신설_이름변경_이동_폐지가_기준일_트리에_반영된다() {
        long a = service.create(new DeptCreateRequest("ZZIT_A", "가", null, null, 0, D0, null)).deptId();
        long b = service.create(new DeptCreateRequest("ZZIT_B", "나", a, null, 0, D0, null)).deptId();
        LocalDate d1 = LocalDate.of(1901, 6, 1);
        service.change(b, new DeptChangeRequest("나2", null, null, 0, d1, "독립"));

        OrgTree before = service.tree(d1.minusDays(1));
        assertThat(find(before, "ZZIT_A").children()).extracting(DeptNode::name).containsExactly("나");
        OrgTree after = service.tree(d1);
        assertThat(find(after, "ZZIT_B").name()).isEqualTo("나2");
        assertThat(find(after, "ZZIT_A").children()).isEmpty();

        LocalDate d2 = LocalDate.of(1901, 9, 1);
        service.close(a, new DeptCloseRequest(d2, "폐지"));
        assertThat(find(service.tree(d2.minusDays(1)), "ZZIT_A")).isNotNull();
        assertThat(find(service.tree(d2), "ZZIT_A")).isNull();
    }

    @Test
    void 자기_하위로_옮기면_거부한다() {
        long a = service.create(new DeptCreateRequest("ZZIT_C", "가", null, null, 0, D0, null)).deptId();
        long b = service.create(new DeptCreateRequest("ZZIT_D", "나", a, null, 0, D0, null)).deptId();
        assertThatThrownBy(() -> service.change(a, new DeptChangeRequest("가", b, null, 0, D0.plusDays(1), null)))
                .isInstanceOfSatisfying(OrgException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void DB는_같은_시작일_중복과_자기참조와_역전된_기간을_거부한다() {
        long a = service.create(new DeptCreateRequest("ZZIT_E", "가", null, null, 0, D0, null)).deptId();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, change_type)"
                + " VALUES (?, DATE '1901-01-01', '중복', 'UPDATE')", a)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_dept_hist (dept_id, valid_from, dept_name, parent_dept_id, change_type)"
                + " VALUES (?, DATE '1901-02-01', '자기', ?, 'MOVE')", a, a)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_dept_hist (dept_id, valid_from, valid_to, dept_name, change_type)"
                + " VALUES (?, DATE '1901-03-01', DATE '1901-03-01', '역전', 'UPDATE')", a))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_dept (dept_code, valid_from) VALUES ('zzit_low', DATE '1901-01-01')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
