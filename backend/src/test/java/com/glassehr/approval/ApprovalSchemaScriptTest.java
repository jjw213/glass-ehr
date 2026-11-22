package com.glassehr.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** V024 정적 검사: 코드 enum 과 DB CHECK 값이 같은지, 이력이 추가만 되는 구조인지. */
class ApprovalSchemaScriptTest {

    static String sql() throws IOException {
        return new ClassPathResource("db/oracle/V024__approval.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    static String quoted(Enum<?>[] values) {
        return String.join(", ", Arrays.stream(values).map(v -> "'" + v.name() + "'").toList());
    }

    @Test
    void enum_값이_CHECK_제약과_같다() throws IOException {
        String s = sql();
        assertThat(s).contains("approver_kind IN (" + quoted(ApproverKind.values()) + ")");
        assertThat(s).contains("status IN (" + quoted(ApprovalDoc.Status.values()) + ")");
        assertThat(s).contains("step_status IN (" + quoted(ApprovalDocStep.Status.values()) + ")");
        assertThat(s).contains("action IN (" + quoted(ApprovalActionLog.Action.values()) + ")");
    }

    @Test
    void 열린_템플릿은_유형마다_하나이고_중괄호를_쓰지_않는다() throws IOException {
        String s = sql();
        assertThat(s).contains("CREATE UNIQUE INDEX ux_approval_template_open");
        assertThat(s).contains("CONSTRAINT uq_approval_doc_ref UNIQUE (request_type, request_ref)");
        assertThat(s).doesNotContain("{");
    }
}
