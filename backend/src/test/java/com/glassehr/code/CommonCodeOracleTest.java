package com.glassehr.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeGroupView;
import com.glassehr.code.CodeDtos.CodeUpdateRequest;
import com.glassehr.code.CodeDtos.CodeView;
import java.util.List;
import java.util.stream.Stream;
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

    private static final String E = "C001";

    @Test
    void 시드_그룹이_정렬순서대로_조회된다() {
        List<String> allSeedGroups = Stream.concat(CodeGroups.ALL.stream(), CodeGroups.ATTENDANCE.stream()).toList();
        assertThat(service.listGroups(E)).extracting(CodeGroupView::groupCode).containsExactlyElementsOf(allSeedGroups);
    }

    @Test
    void 고용형태_시드는_정규_기간제_단시간_순() {
        assertThat(service.activeCodes(E, List.of(CodeGroups.EMPLOYMENT_TYPE)).get(CodeGroups.EMPLOYMENT_TYPE))
                .extracting(CodeView::code).containsExactly("REGULAR", "FIXED_TERM", "PART_TIME");
    }

    @Test
    void V037_시스템_그룹과_기본_코드가_표시되고_기본_코드_삭제는_409() {
        assertThat(service.listGroups(E)).allSatisfy(g -> assertThat(g.system()).isTrue());
        assertThat(service.listCodes(E, CodeGroups.EMPLOYMENT_TYPE, true))
                .filteredOn(CodeDtos.CodeListItem::system).extracting(CodeDtos.CodeListItem::code)
                .containsExactlyInAnyOrder("FIXED_TERM", "PART_TIME");
        assertThatThrownBy(() -> service.deleteCode(E, CodeGroups.EMPLOYMENT_TYPE, "PART_TIME"))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.deleteGroup(E, CodeGroups.JOB_GRADE))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 사용처_건수는_사원_테이블_직접_집계와_같고_행_목록_SQL이_돈다() {
        Integer staff = jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE enter_cd = ? AND job_grade_code = 'STAFF'",
                Integer.class, E);
        var summary = service.usages(E, CodeGroups.JOB_GRADE, "STAFF", true);
        assertThat(summary.tables().stream().filter(t -> t.table().equals("employee"))
                .mapToLong(CodeDtos.UsageTable::count).sum()).isEqualTo(staff.longValue());
        assertThat(service.listCodes(E, CodeGroups.JOB_GRADE, true)).filteredOn(c -> c.code().equals("STAFF"))
                .allSatisfy(c -> assertThat(c.inUse()).isEqualTo(summary.total() > 0));
        for (var ref : CodeReferences.BY_GROUP.values().stream().flatMap(List::stream).toList()) {
            String group = CodeReferences.BY_GROUP.entrySet().stream().filter(en -> en.getValue().contains(ref))
                    .findFirst().orElseThrow().getKey();
            String code = service.listCodes(E, group, true).get(0).code();
            assertThat(service.usageRows(E, group, code, ref.table(), 0).rows()).hasSizeLessThanOrEqualTo(
                    CommonCodeService.USAGE_PAGE_SIZE);
        }
        // 사용자 그룹: 추가 → 코드 추가 → 코드 삭제 → 그룹 삭제
        service.createGroup(E, new CodeDtos.GroupCreateRequest("ZZ_IT_USER", "IT 사용자 그룹", null, 999, true));
        service.create(E, "ZZ_IT_USER", new CodeCreateRequest("A1", "가", null, 0, true, null));
        service.create(E, "ZZ_IT_USER", new CodeCreateRequest("A2", "나", null, 1, true, null));
        service.deleteCode(E, "ZZ_IT_USER", "A1");
        service.deleteGroup(E, "ZZ_IT_USER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM common_code WHERE enter_cd = ? AND group_code = 'ZZ_IT_USER'",
                Integer.class, E)).isZero();
    }

    @Test
    void 추가_수정_사용중지가_조회에_반영된다() {
        service.create(E, CodeGroups.JOB_GRADE, new CodeCreateRequest("ZZ_IT_GRADE", "IT 테스트 직급", null, 0, true, null));
        assertThat(service.activeCodes(E, List.of(CodeGroups.JOB_GRADE)).get(CodeGroups.JOB_GRADE).get(0).code())
                .isEqualTo("ZZ_IT_GRADE");

        service.update(E, CodeGroups.JOB_GRADE, "ZZ_IT_GRADE", new CodeUpdateRequest("IT 테스트 직급", null, 0, false, null));

        assertThat(service.activeCodes(E, List.of(CodeGroups.JOB_GRADE)).get(CodeGroups.JOB_GRADE))
                .extracting(CodeView::code).doesNotContain("ZZ_IT_GRADE");
        assertThat(service.listCodes(E, CodeGroups.JOB_GRADE, true))
                .filteredOn(c -> c.code().equals("ZZ_IT_GRADE")).singleElement()
                .satisfies(c -> assertThat(c.active()).isFalse());
        assertThat(jdbc.queryForObject("SELECT use_yn FROM common_code WHERE group_code = 'JOB_GRADE' AND code = 'ZZ_IT_GRADE'",
                String.class)).isEqualTo("N");
    }

    @Test
    void 중복_코드값은_409() {
        assertThatThrownBy(() -> service.create(E, CodeGroups.JOB_GRADE, new CodeCreateRequest("STAFF", "사원", null, 1, true, null)))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void DB는_Y_N_외의_사용여부와_소문자_코드값과_음수_정렬순서를_거부한다() {
        // V034(#121): enter_cd 에 DEFAULT 가 없으므로 법인을 넣어 다른 제약만 걸리게 한다.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code (enter_cd, group_code, code, code_name, use_yn)"
                + " VALUES ('C001', 'JOB_GRADE', 'ZZ_IT_A', '가', 'X')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code (enter_cd, group_code, code, code_name)"
                + " VALUES ('C001', 'JOB_GRADE', 'zz_it_b', '나')")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code (enter_cd, group_code, code, code_name, sort_order)"
                + " VALUES ('C001', 'JOB_GRADE', 'ZZ_IT_C', '다', -1)")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void DB는_없는_그룹의_코드를_거부한다() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code (enter_cd, group_code, code, code_name)"
                + " VALUES ('C001', 'ZZ_NO_GROUP', 'A', '가')")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void DB는_법인을_빠뜨린_코드를_거부한다() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO common_code (group_code, code, code_name)"
                + " VALUES ('JOB_GRADE', 'ZZ_IT_D', '라')")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
