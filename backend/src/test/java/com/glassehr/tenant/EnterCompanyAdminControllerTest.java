package com.glassehr.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.AuthInterceptor;
import com.glassehr.auth.TestAuthUsers;
import com.glassehr.tenant.EnterCompanyAdminService.AdminException;
import com.glassehr.tenant.EnterCompanyAdminService.CompanyView;
import com.glassehr.tenant.EnterCompanyAdminService.CreateResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnterCompanyAdminController.class)
@Import(TestAuthUsers.class)
class EnterCompanyAdminControllerTest {

    private static final String BODY = "{\"enterCd\":\"C002\",\"enterName\":\"둘째 법인(예시)\",\"bizRegNo\":\"0000000097\","
            + "\"sourceEnterCd\":\"C001\",\"adminLoginId\":\"c2.admin\",\"adminPassword\":\"example-pass-1\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EnterCompanyAdminService service;

    @Test
    void 그룹_관리자는_세션_고객사의_법인_목록을_본다() throws Exception {
        when(service.list(argThat(u -> u.tenantId().equals("T001"))))
                .thenReturn(List.of(new CompanyView("C001", "기본 법인(예시)", "0000000000", true, 0)));
        mvc.perform(get("/api/admin/enter-companies").header(AuthInterceptor.ACTOR_ID_HEADER, "group1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enterCd").value("C001"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void 법인_관리자와_인사는_법인_관리를_못_한다() throws Exception {
        for (String who : List.of("admin1", "hr1")) {
            mvc.perform(get("/api/admin/enter-companies").header(AuthInterceptor.ACTOR_ID_HEADER, who))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/enter-companies").header(AuthInterceptor.ACTOR_ID_HEADER, who)
                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden());
        }
        verify(service, never()).create(any(), any());
    }

    @Test
    void 새_법인은_201과_복사_건수를_주고_비밀번호는_돌려주지_않는다() throws Exception {
        when(service.create(any(), any())).thenReturn(new CreateResult(
                new CompanyView("C002", "둘째 법인(예시)", "0000000097", true, 10), Map.of("common_code", 30),
                List.of("OVERTIME"), "c2.admin"));
        mvc.perform(post("/api/admin/enter-companies").header(AuthInterceptor.ACTOR_ID_HEADER, "group1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company.enterCd").value("C002"))
                .andExpect(jsonPath("$.copied.common_code").value(30))
                .andExpect(jsonPath("$.skippedTemplates[0]").value("OVERTIME"))
                .andExpect(jsonPath("$.adminPassword").doesNotExist());
        verify(service).create(argThat(u -> u.tenantId().equals("T001") && u.loginId().equals("group1")),
                argThat(r -> r.sourceEnterCd().equals("C001") && r.adminLoginId().equals("c2.admin")));
    }

    @Test
    void 서비스_거부는_상태코드와_메시지로() throws Exception {
        when(service.create(any(), any())).thenThrow(new AdminException(HttpStatus.NOT_FOUND, "복사할 원본 법인을 찾을 수 없어요"));
        mvc.perform(post("/api/admin/enter-companies").header(AuthInterceptor.ACTOR_ID_HEADER, "group1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("복사할 원본 법인을 찾을 수 없어요"));
        when(service.setActive(any(), eq("C001"), any()))
                .thenThrow(new AdminException(HttpStatus.CONFLICT, "중지 불가"));
        mvc.perform(put("/api/admin/enter-companies/C001/active").header(AuthInterceptor.ACTOR_ID_HEADER, "group1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isConflict());
    }
}
