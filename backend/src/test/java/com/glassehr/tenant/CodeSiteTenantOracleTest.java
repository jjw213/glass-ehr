package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeUpdateRequest;
import com.glassehr.code.CodeDtos.CodeListItem;
import com.glassehr.code.CodeException;
import com.glassehr.code.CommonCodeService;
import com.glassehr.employee.EmpNoGenerator;
import com.glassehr.employee.EmployeeException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * V034(#121): 공통코드·사업장·사번 채번의 법인 분리를 Oracle 실DB에서 검증한다.
 * "법인 A(C001) 세션으로 법인 B(ZZIT1) 데이터 조회·수정 → 404/거부"와 복합 PK·FK 를 본다(docs/tenant-design.md 5.2).
 * 실행 조건: {@code ORACLE_IT=true}. 각 테스트는 롤백한다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class CodeSiteTenantOracleTest {

    private static final String A = "C001";
    private static final String B = "ZZIT1";

    @Autowired
    private CommonCodeService codes;

    @Autowired
    private EmpNoGenerator empNos;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void 법인_B와_B의_코드_사업장을_넣는다() {
        jdbc.update("INSERT INTO enter_company (enter_cd, enter_name, biz_reg_no, use_yn, sort_order)"
                + " VALUES (?, 'IT 법인 가(예시)', '0000000091', 'Y', 0)", B);
        jdbc.update("INSERT INTO common_code_group (enter_cd, group_code, group_name) VALUES (?, 'ZZ_IT_ONLY_B', 'B 전용')", B);
        jdbc.update("INSERT INTO common_code (enter_cd, group_code, code, code_name) VALUES (?, 'ZZ_IT_ONLY_B', 'X1', '가')", B);
        // 같은 그룹코드(JOB_GRADE)가 법인마다 따로 있을 수 있다(복합 PK).
        jdbc.update("INSERT INTO common_code_group (enter_cd, group_code, group_name) VALUES (?, 'JOB_GRADE', 'B 직급')", B);
        jdbc.update("INSERT INTO common_code (enter_cd, group_code, code, code_name) VALUES (?, 'JOB_GRADE', 'ZZ_B_GRADE', 'B 직급')", B);
        // 같은 사업장코드 S01 이 법인 B 에도 있을 수 있다. S98 은 B 에만 있다.
        jdbc.update("INSERT INTO biz_site (enter_cd, site_code, site_name) VALUES (?, 'S01', 'B 본사(예시)')", B);
        jdbc.update("INSERT INTO biz_site (enter_cd, site_code, site_name) VALUES (?, 'S98', 'B 전용(예시)')", B);
    }

    @Test
    void 법인_A_세션은_법인_B의_코드그룹을_조회_수정할_수_없다_404() {
        assertNotFound(() -> codes.listCodes(A, "ZZ_IT_ONLY_B", true));
        assertNotFound(() -> codes.activeCodes(A, List.of("ZZ_IT_ONLY_B")));
        assertNotFound(() -> codes.update(A, "ZZ_IT_ONLY_B", "X1", new CodeUpdateRequest("바꿈", null, 0, false, null)));
        assertNotFound(() -> codes.create(A, "ZZ_IT_ONLY_B", new CodeCreateRequest("X2", "나", null, 0, true, null)));
        assertThat(codes.listGroups(A)).extracting(g -> g.groupCode()).doesNotContain("ZZ_IT_ONLY_B");
        assertThat(jdbc.queryForObject("SELECT code_name FROM common_code WHERE enter_cd = ? AND code = 'X1'",
                String.class, B)).isEqualTo("가");
    }

    @Test
    void 법인_A_세션은_법인_B의_그룹과_코드를_삭제할_수_없다_404() {
        assertNotFound(() -> codes.deleteCode(A, "ZZ_IT_ONLY_B", "X1"));
        assertNotFound(() -> codes.deleteGroup(A, "ZZ_IT_ONLY_B"));
        assertNotFound(() -> codes.deleteCode(A, "JOB_GRADE", "ZZ_B_GRADE"));
        assertNotFound(() -> codes.usages(A, "JOB_GRADE", "ZZ_B_GRADE", true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM common_code WHERE enter_cd = ?"
                + " AND code IN ('X1', 'ZZ_B_GRADE')", Integer.class, B)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM common_code_group WHERE enter_cd = ?"
                + " AND group_code = 'ZZ_IT_ONLY_B'", Integer.class, B)).isEqualTo(1);
        // 같은 법인(B) 세션은 참조 없는 사용자 그룹을 코드와 함께 지울 수 있다.
        codes.deleteGroup(B, "ZZ_IT_ONLY_B");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM common_code WHERE enter_cd = ?"
                + " AND group_code = 'ZZ_IT_ONLY_B'", Integer.class, B)).isZero();
    }

    @Test
    void 같은_그룹의_코드도_법인_A에는_A_것만_보이고_B_코드는_수정할_수_없다() {
        assertThat(codes.listCodes(A, "JOB_GRADE", true)).extracting(CodeListItem::code).doesNotContain("ZZ_B_GRADE");
        assertThat(codes.listCodes(B, "JOB_GRADE", true)).extracting(CodeListItem::code).containsExactly("ZZ_B_GRADE");
        assertNotFound(() -> codes.update(A, "JOB_GRADE", "ZZ_B_GRADE", new CodeUpdateRequest("바꿈", null, 0, false, null)));
        // 법인 B 에 같은 코드값(STAFF)을 따로 만들 수 있다.
        codes.create(B, "JOB_GRADE", new CodeCreateRequest("STAFF", "B 사원", null, 1, true, null));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM common_code WHERE group_code = 'JOB_GRADE' AND code = 'STAFF'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void DB_복합_FK는_다른_법인의_코드그룹과_사업장을_참조하지_못하게_한다() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code (enter_cd, group_code, code, code_name)"
                + " VALUES (?, 'ZZ_IT_ONLY_B', 'X3', '다')", A)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO emp_no_seq (enter_cd, site_code, hire_year, last_seq)"
                + " VALUES (?, 'S98', 2026, 1)", A)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO biz_site (site_code, site_name) VALUES ('S97', '법인 없음')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 법인_A_세션은_법인_B의_사업장으로_채번할_수_없다() {
        assertThatThrownBy(() -> empNos.next(A, "S98", LocalDate.of(2026, 3, 2)))
                .isInstanceOfSatisfying(EmployeeException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emp_no_seq WHERE site_code = 'S98'", Integer.class))
                .isZero();
    }

    @Test
    void 사번_순번은_법인마다_따로_센다() {
        Integer before = jdbc.queryForObject("SELECT NVL(MAX(last_seq), 0) FROM emp_no_seq"
                + " WHERE enter_cd = ? AND site_code = 'S01' AND hire_year = 2031", Integer.class, A);

        String b1 = empNos.next(B, "S01", LocalDate.of(2031, 1, 2));
        String b2 = empNos.next(B, "S01", LocalDate.of(2031, 6, 30));
        String a1 = empNos.next(A, "S01", LocalDate.of(2031, 1, 2));

        assertThat(b1).isEqualTo("S01-2031-001");
        assertThat(b2).isEqualTo("S01-2031-002");
        assertThat(a1).isEqualTo(String.format("S01-2031-%03d", before + 1));
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CodeException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
