package com.glassehr.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 공통코드 참조 목록({@link CodeReferences}) 누락 검사(#140, Oracle 없이).
 * 모든 마이그레이션의 CREATE TABLE·ALTER TABLE ADD 에서 *_code 컬럼을 모아, 참조 목록이나
 * 공통코드가 아닌 컬럼 목록({@link #NOT_COMMON_CODE}) 중 한 곳에 있는지 본다.
 * 새 *_code 컬럼을 만들고 둘 중 어디에도 적지 않으면 실패한다 → 공통코드를 참조하면 CodeReferences 에 추가한다.
 */
class CodeReferencesSchemaScriptTest {

    /** 공통코드(common_code)가 아닌 *_code 컬럼(table.column)과 이유. */
    static final Set<String> NOT_COMMON_CODE = Set.of(
            // 공통코드 자신·법인·조직·사업장 키
            "common_code_group.group_code", "common_code.group_code",
            "org_dept.dept_code", "biz_site.site_code", "emp_no_seq.site_code", "employee.site_code",
            "worker_rep_agreement.scope_site_code",
            // 법령 기준값 항목(legal_rate_item, 전 법인 공통)
            "legal_rate_item.item_code", "legal_rate.item_code",
            // 역할·권한(app_role·app_permission, 전 법인 공통)
            "app_role.role_code", "app_permission.perm_code", "app_role_permission.role_code",
            "app_role_permission.perm_code", "app_user_role.role_code",
            // 고정 값 CHECK 로 거는 열거형(공통코드 아님)
            "rrn_access_log.purpose_code", "privacy_access_log.item_code", "privacy_access_log.action_code",
            "employment_contract.wage_unit_code",
            // 경력증명서 발급 당시 근로계약 임금 단위 복사본(근로기준법 제39조, employment_contract 와 같은 열거형)
            "employment_certificate.wage_unit_code", "retirement_case.notice_exempt_code",
            "retirement_checklist_item.item_code", "login_access_log.event_code",
            "work_pattern.pattern_code", "flex_work_assign.job_code");

    private static final Pattern CREATE = Pattern.compile("(?is)^\\s*CREATE TABLE\\s+\"?(\\w+)\"?\\s*\\((.*)$");
    private static final Pattern ALTER_ADD = Pattern.compile("(?is)^\\s*ALTER TABLE\\s+(\\w+)\\s+ADD\\b(.*)$");
    private static final Pattern CODE_COLUMN =
            Pattern.compile("(?i)(?:^|[\\s(,])(\\w+_code)\\s+(?:VARCHAR2|CHAR|NVARCHAR2|NUMBER)\\b");

    /** 마이그레이션의 table.column(*_code). 주석 줄은 뺀다. */
    static Set<String> codeColumns() throws IOException {
        Set<String> out = new TreeSet<>();
        Resource[] rs = new PathMatchingResourcePatternResolver().getResources("classpath:db/oracle/V*.sql");
        for (Resource r : rs) {
            StringBuilder sb = new StringBuilder();
            for (String line : r.getContentAsString(StandardCharsets.UTF_8).split("\n")) {
                if (!line.strip().startsWith("--")) {
                    sb.append(line).append('\n');
                }
            }
            for (String stmt : sb.toString().split(";")) {
                Matcher m = CREATE.matcher(stmt);
                if (!m.find()) {
                    m = ALTER_ADD.matcher(stmt);
                    if (!m.find()) {
                        continue;
                    }
                }
                String table = m.group(1).toLowerCase(Locale.ROOT);
                Matcher c = CODE_COLUMN.matcher(m.group(2));
                while (c.find()) {
                    out.add(table + "." + c.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    static Set<String> referenced() {
        Set<String> out = new TreeSet<>();
        CodeReferences.BY_GROUP.values().forEach(refs -> refs.forEach(
                ref -> ref.columns().forEach(col -> out.add(ref.table() + "." + col))));
        return out;
    }

    @Test
    void 마이그레이션의_code_컬럼은_참조_목록이나_공통코드_아님_목록에_있다() throws IOException {
        Set<String> missing = new TreeSet<>(codeColumns());
        missing.removeAll(referenced());
        missing.removeAll(NOT_COMMON_CODE);
        assertThat(missing).as("공통코드를 참조하면 CodeReferences 에, 아니면 NOT_COMMON_CODE 에 추가").isEmpty();
    }

    @Test
    void 참조_목록의_컬럼은_스키마에_있다() throws IOException {
        assertThat(codeColumns()).containsAll(referenced()).containsAll(NOT_COMMON_CODE);
    }

    @Test
    void 참조_목록은_로직이_쓰는_그룹을_모두_다룬다() {
        assertThat(CodeReferences.BY_GROUP.keySet()).containsExactlyInAnyOrderElementsOf(CodeGroups.ALL);
        assertThat(CodeReferences.of(CodeGroups.JOB_GRADE)).extracting(CodeReferences.Ref::table)
                .containsExactly("employee", "hr_appointment");
        assertThat(CodeReferences.of("USER_MADE")).isEmpty();
    }
}
