package com.glassehr.approval;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.AuthInterceptor;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.TestAuth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApprovalController.class)
@Import(TestAuth.class)
class ApprovalControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApprovalService service;

    @MockitoBean
    private ApprovalTemplateService templateService;

    @Test
    void 일반_사원도_결재함을_본다() throws Exception {
        when(service.inbox(any())).thenReturn(List.of());
        mvc.perform(get("/api/approvals/inbox").header(AuthInterceptor.ACTOR_ID_HEADER, "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 승인_의견은_선택이고_반려_사유는_서비스로_넘긴다() throws Exception {
        mvc.perform(post("/api/approvals/9/approve")).andExpect(status().isOk());
        verify(service).approve(eq(9L), any(AuthUser.class), isNull());
        mvc.perform(post("/api/approvals/9/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"사유\"}")).andExpect(status().isOk());
        verify(service).reject(eq(9L), any(AuthUser.class), eq("사유"));
    }

    @Test
    void 서비스_오류는_상태코드와_메시지로_돌려준다() throws Exception {
        when(service.approve(eq(9L), any(), any()))
                .thenThrow(new ApprovalException(HttpStatus.FORBIDDEN, "지금 결재 차례가 아니에요"));
        mvc.perform(post("/api/approvals/9/approve"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("지금 결재 차례가 아니에요"));
    }

    @Test
    void 결재선_설정은_관리자_인사만_할_수_있다() throws Exception {
        String body = "{\"requestType\":\"OVERTIME\",\"validFrom\":\"2026-12-01\","
                + "\"steps\":[{\"approverKind\":\"DEPT_HEAD\",\"skipIfSelf\":true}]}";
        mvc.perform(post("/api/approvals/templates").header(AuthInterceptor.ACTOR_ID_HEADER, "emp1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verify(templateService, never()).create(any(), any());
        mvc.perform(post("/api/approvals/templates").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void 단계가_없는_결재선은_400() throws Exception {
        mvc.perform(post("/api/approvals/templates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestType\":\"OVERTIME\",\"validFrom\":\"2026-12-01\",\"steps\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("결재 단계를 하나 이상 넣어 주세요"));
    }
}
