package com.glassehr.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeGroupView;
import com.glassehr.code.CodeDtos.CodeUpdateRequest;
import com.glassehr.code.CodeDtos.CodeView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle 실DB에서 공통코드 DDL·시드·JPA 매핑을 검증한다.
 * 컨텍스트가 뜬다는 것 자체가 엔티티와 V006 스키마가 맞는다는 뜻이다(ddl-auto: validate).
 *
 * <p>실행 조건: 환경변수 {@code ORACLE_IT=true}와 DB_URL/DB_USER/DB_PASSWORD.
 * 스키마가 없으면 {@link com.glassehr.OracleItSchemaConfig}가 V006·V007을 적용한다. 각 테스트는 롤백한다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "ORACLE_IT", matches = "true")
class CommonCodeOracleTest {

    @Autowired
    private CommonCodeService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 시드_그룹이_정렬순서대로_조회된다() {
        assertThat(service.listGroups()).extracting(CodeGroupView::groupCode).containsExactlyElementsOf(CodeGroups.ALL);
    }

    @Test
    void 고용형태_시드는_정규_기간제_단시간_순() {
        assertThat(service.activeCodes(List.of(CodeGroups.EMPLOYMENT_TYPE)).get(CodeGroups.EMPLOYMENT_TYPE))
                .extracting(CodeView::code).containsExactly("REGULAR", "FIXED_TERM", "PART_TIME");
    }

    @Test
    void 추가_수정_사용중지가_조회에_반영된다() {
        service.create(CodeGroups.JOB_GRADE, new CodeCreateRequest("ZZ_IT_GRADE", "IT 테스트 직급", null, 0, true));
        assertThat(service.activeCodes(List.of(CodeGroups.JOB_GRADE)).get(CodeGroups.JOB_GRADE).get(0).code())
                .isEqualTo("ZZ_IT_GRADE");

        service.update(CodeGroups.JOB_GRADE, "ZZ_IT_GRADE", new CodeUpdateRequest("IT 테스트 직급", null, 0, false));

        assertThat(service.activeCodes(List.of(CodeGroups.JOB_GRADE)).get(CodeGroups.JOB_GRADE))
                .extracting(CodeView::code).doesNotContain("ZZ_IT_GRADE");
        assertThat(service.listCodes(CodeGroups.JOB_GRADE, true))
                .filteredOn(c -> c.code().equals("ZZ_IT_GRADE")).singleElement()
                .satisfies(c -> assertThat(c.active()).isFalse());
        assertThat(jdbc.queryForObject("SELECT use_yn FROM common_code WHERE group_code = 'JOB_GRADE' AND code = 'ZZ_IT_GRADE'",
                String.class)).isEqualTo("N");
    }

    @Test
    void 중복_코드값은_409() {
        assertThatThrownBy(() -> service.create(CodeGroups.JOB_GRADE, new CodeCreateRequest("STAFF", "사원", null, 1, true)))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void DB는_Y_N_외의_사용여부와_소문자_코드값과_음수_정렬순서를_거부한다() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code (group_code, code, code_name, use_yn)"
                + " VALUES ('JOB_GRADE', 'ZZ_IT_A', '가', 'X')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code (group_code, code, code_name)"
                + " VALUES ('JOB_GRADE', 'zz_it_b', '나')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code (group_code, code, code_name, sort_order)"
                + " VALUES ('JOB_GRADE', 'ZZ_IT_C', '다', -1)")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void DB는_없는_그룹의_코드를_거부한다() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code (group_code, code, code_name)"
                + " VALUES ('ZZ_NO_GROUP', 'A', '가')")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
