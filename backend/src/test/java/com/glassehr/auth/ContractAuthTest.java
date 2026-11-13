package com.glassehr.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.contract.ContractController;
import com.glassehr.contract.ContractService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 근로계약 API 권한 우회 시나리오(#19). 계약에는 임금 등 개인정보가 있어 일반 사원·부서장은 막고,
 * 급여 담당은 조회만, 인사·관리자는 등록·수정까지 허용한다. 역할 헤더로 권한을 올릴 수 없다.
 */
@WebMvcTest(ContractController.class)
@Import(TestAuthUsers.class)
class ContractAuthTest {

    private static final String BODY = "{\"contractType\":\"REGULAR\",\"startDate\":\"2026-03-02\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContractService service;

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String loginId) {
        return b.header(AuthInterceptor.ACTOR_ID_HEADER, loginId);
    }

    @Test
    void 일반_사원은_다른_사원의_계약을_볼_수_없다() throws Exception {
        mvc.perform(as(get("/api/employees/7/contracts"), "emp1")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/contracts/1"), "emp1")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/contracts/alerts"), "emp1")).andExpect(status().isForbidden());
        verify(service, never()).list(anyLong());
        verify(service, never()).get(anyLong());
    }

    @Test
    void 부서장도_계약_조회는_아직_막힌다() throws Exception {
        mvc.perform(as(get("/api/employees/7/contracts"), "head1")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/contracts/undelivered"), "head1")).andExpect(status().isForbidden());
        verify(service, never()).list(anyLong());
        verify(service, never()).undelivered();
    }

    @Test
    void 역할_헤더를_붙여도_권한이_오르지_않는다() throws Exception {
        mvc.perform(as(post("/api/employees/5/contracts"), "emp1").header("X-Actor-Role", "HR")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(service, never()).create(anyLong(), any());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/contracts/alerts")).andExpect(status().isUnauthorized());
    }

    @Test
    void 급여_담당은_조회만_되고_수정은_막힌다() throws Exception {
        mvc.perform(as(get("/api/employees/7/contracts"), "pay1")).andExpect(status().isOk());
        mvc.perform(as(put("/api/contracts/1"), "pay1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/contracts/1/delivery"), "pay1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveredOn\":\"2026-03-02\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).update(anyLong(), any());
        verify(service, never()).recordDelivery(anyLong(), any());
    }

    @Test
    void 인사담당은_계약_목록을_본다() throws Exception {
        mvc.perform(as(get("/api/employees/7/contracts"), "hr1")).andExpect(status().isOk());
        verify(service).list(7L);
    }
}
