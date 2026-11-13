package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.AuthInterceptor;
import com.glassehr.auth.TestAuthUsers;
import com.glassehr.employee.EmployeeImportService.Confirmed;
import com.glassehr.employee.EmployeeImportService.NotConfirmableException;
import com.glassehr.employee.EmployeeImportService.Preview;
import com.glassehr.employee.EmployeeImportService.RowResult;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 일괄 등록 API 권한·응답 형식(#20). 권한 검사는 실제 AuthInterceptor 가 V013 권한표로 한다. */
@WebMvcTest(EmployeeImportController.class)
@Import(TestAuthUsers.class)
class EmployeeImportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EmployeeImportService imports;

    private static final MockMultipartFile FILE = new MockMultipartFile("file", "employees.csv", "text/csv",
            "사업장코드*\r\nS01\r\n".getBytes(StandardCharsets.UTF_8));

    @Test
    void 양식_내려받기는_등록_권한자만() throws Exception {
        byte[] body = mvc.perform(get("/api/employees/import/template").header(AuthInterceptor.ACTOR_ID_HEADER, "hr1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("employee-import-template.csv")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(body, StandardCharsets.UTF_8)).contains("사업장코드*,성명*");
        mvc.perform(get("/api/employees/import/template").header(AuthInterceptor.ACTOR_ID_HEADER, "admin1"))
                .andExpect(status().isOk());
        for (String who : new String[] {"pay1", "head1", "emp1"}) {
            mvc.perform(get("/api/employees/import/template").header(AuthInterceptor.ACTOR_ID_HEADER, who))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/employees/import/template")).andExpect(status().isUnauthorized());
    }

    @Test
    void 급여담당_부서장_일반사원은_올릴_수_없다() throws Exception {
        for (String who : new String[] {"pay1", "head1", "emp1"}) {
            mvc.perform(multipart("/api/employees/import/preview").file(FILE)
                            .header(AuthInterceptor.ACTOR_ID_HEADER, who))
                    .andExpect(status().isForbidden());
            mvc.perform(multipart("/api/employees/import/confirm").file(FILE)
                            .header(AuthInterceptor.ACTOR_ID_HEADER, who))
                    .andExpect(status().isForbidden());
        }
        verify(imports, never()).preview(any());
        verify(imports, never()).confirm(any(), any());
    }

    @Test
    void 미리보기는_행별_오류를_돌려준다() throws Exception {
        when(imports.preview(any())).thenReturn(new Preview(1, 1, false, List.of(),
                List.of(new RowResult(2, "예시사원", "S99", LocalDate.of(2026, 3, 2), List.of("사업장이 없어요: S99")))));
        mvc.perform(multipart("/api/employees/import/preview").file(FILE).header(AuthInterceptor.ACTOR_ID_HEADER, "hr1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.confirmable").value(false))
                .andExpect(jsonPath("$.rows[0].rowNo").value(2))
                .andExpect(jsonPath("$.rows[0].errors[0]").value("사업장이 없어요: S99"));
    }

    @Test
    void 확정은_오류가_있으면_422와_미리보기() throws Exception {
        when(imports.confirm(any(), any())).thenThrow(new NotConfirmableException(new Preview(1, 1, false, List.of(),
                List.of(new RowResult(2, "예시사원", "S01", null, List.of("성명을 입력해 주세요"))))));
        mvc.perform(multipart("/api/employees/import/confirm").file(FILE).header(AuthInterceptor.ACTOR_ID_HEADER, "hr1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.preview.errorRows").value(1))
                .andExpect(jsonPath("$.preview.rows[0].errors[0]").value("성명을 입력해 주세요"));
    }

    @Test
    void 확정은_201이고_요청자는_서버가_확인한_사용자() throws Exception {
        when(imports.confirm(any(), any())).thenReturn(new Confirmed(1, List.of("S01-2026-001")));
        mvc.perform(multipart("/api/employees/import/confirm").file(FILE).header(AuthInterceptor.ACTOR_ID_HEADER, "hr1")
                        .header("X-Actor-Role", "admin"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empNos[0]").value("S01-2026-001"));
        ArgumentCaptor<RequestActor> actor = ArgumentCaptor.forClass(RequestActor.class);
        verify(imports).confirm(any(), actor.capture());
        assertThat(actor.getValue().actorId()).isEqualTo("hr1");
        assertThat(actor.getValue().role()).isEqualTo(RequestActor.Role.HR);
    }

    @Test
    void 파일_칸이_없으면_400() throws Exception {
        mvc.perform(multipart("/api/employees/import/preview").header(AuthInterceptor.ACTOR_ID_HEADER, "hr1"))
                .andExpect(status().isBadRequest());
    }
}
