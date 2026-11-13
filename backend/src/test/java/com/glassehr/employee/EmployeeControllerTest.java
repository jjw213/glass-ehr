package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeSummary;
import com.glassehr.employee.EmployeeDtos.EmployeeView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import com.glassehr.auth.TestAuth;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(EmployeeController.class)
@Import(TestAuth.class)
class EmployeeControllerTest {

    private static final LocalDate HIRE = LocalDate.of(2026, 3, 2);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EmployeeService service;

    @MockitoBean
    private RrnService rrns;

    private static EmployeeView view(String rrnMasked) {
        return new EmployeeView(1, "S01-2026-001", "S01", "예시사원", "F", LocalDate.of(1999, 1, 1), "가상시 예시구 샘플로 1",
                null, "개발", "ENGINEERING", HIRE, null, null, null, "REGULAR", 2400, "HQ", null, 10L, "STAFF",
                "MEMBER", null, null, null, null, null, rrnMasked);
    }

    private static final String UPDATE_BODY = """
            {"name":"예시사원","gender":"F","birthDate":"1999-01-01","address":"가상시 예시구 샘플로 1",
             "jobDescription":"개발","employmentTypeCode":"REGULAR"%s}""";

    private static final String CREATE_BODY = """
            {"siteCode":"S01","name":"예시사원","gender":"F","birthDate":"1999-01-01","address":"가상시 예시구 샘플로 1",
             "jobDescription":"개발","hireDate":"2026-03-02","employmentTypeCode":"REGULAR","deptId":10%s}""";

    @Test
    void 등록은_201이고_주민등록번호는_마스킹_값만_나간다() throws Exception {
        when(service.create(any(), any())).thenReturn(view("000000-*******"));
        MvcResult r = mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Actor-Id", "hr1").header("X-Actor-Role", "hr")
                        .content(CREATE_BODY.formatted(",\"rrn\":\"000000-0000001\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empNo").value("S01-2026-001"))
                .andExpect(jsonPath("$.rrnMasked").value("000000-*******"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("0000001");

        ArgumentCaptor<EmployeeCreateRequest> req = ArgumentCaptor.forClass(EmployeeCreateRequest.class);
        ArgumentCaptor<RequestActor> actor = ArgumentCaptor.forClass(RequestActor.class);
        verify(service).create(req.capture(), actor.capture());
        assertThat(req.getValue().rrn()).isEqualTo(Rrn.parse("000000-0000001"));
        assertThat(req.getValue().toString()).doesNotContain("0000001");
        assertThat(actor.getValue().actorId()).isEqualTo("hr1");
        assertThat(actor.getValue().role()).isEqualTo(RequestActor.Role.HR);
    }

    @Test
    void 주민등록번호_형식이_틀리면_400이고_값을_되돌려주지_않는다() throws Exception {
        MvcResult r = mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.formatted(",\"rrn\":\"12345-6789012\"")))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("6789012");
        verify(service, never()).create(any(), any());
    }

    @Test
    void 필수값이_없으면_400() throws Exception {
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"siteCode\":\"S01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        verify(service, never()).create(any(), any());
    }

    @Test
    void 수정_요청에_소속_직급_직책을_넣으면_400() throws Exception {
        for (String extra : new String[] {",\"deptId\":20", ",\"currentDeptId\":20", ",\"jobGradeCode\":\"MANAGER\"",
                ",\"jobTitleCode\":\"TEAM_LEADER\"", ",\"empNo\":\"S01-2026-999\""}) {
            mvc.perform(put("/api/employees/1").contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY.formatted(extra)))
                    .andExpect(status().isBadRequest());
        }
        verify(service, never()).update(anyLong(), any());
    }

    @Test
    void 수정은_기본정보만으로_200() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(view(null));
        mvc.perform(put("/api/employees/1").contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY.formatted("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentDeptId").value(10));
    }

    @Test
    void 목록과_상세() throws Exception {
        when(service.list(true)).thenReturn(List.of(
                new EmployeeSummary(1, "S01-2026-001", "예시사원", 10L, "STAFF", "MEMBER", "REGULAR", HIRE, null)));
        mvc.perform(get("/api/employees").param("includeRetired", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].empNo").value("S01-2026-001"))
                .andExpect(jsonPath("$[0].address").doesNotExist());
        when(service.get(1L)).thenReturn(view("000000-*******"));
        mvc.perform(get("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rrnMasked").value("000000-*******"));
        when(service.get(2L)).thenThrow(EmployeeException.notFound(2L));
        mvc.perform(get("/api/employees/2")).andExpect(status().isNotFound());
    }

    @Test
    void 퇴직_처리() throws Exception {
        when(service.retire(eq(1L), any())).thenReturn(view(null));
        mvc.perform(post("/api/employees/1/retire").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retireDate\":\"2026-10-31\",\"reasonCode\":\"VOLUNTARY\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/employees/1/retire").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 원문_조회는_캐시_금지_헤더를_붙이고_요청자와_목적을_넘긴다() throws Exception {
        when(rrns.reveal(eq(1L), any(), eq(RrnAccessLog.Purpose.SOCIAL_INSURANCE))).thenReturn(Rrn.parse("000000-0000001"));
        mvc.perform(get("/api/employees/1/rrn").param("purpose", "SOCIAL_INSURANCE")
                        .header("X-Actor-Id", "pay1").header("X-Actor-Role", "PAYROLL"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.rrn").value("000000-0000001"));
        ArgumentCaptor<RequestActor> actor = ArgumentCaptor.forClass(RequestActor.class);
        verify(rrns).reveal(eq(1L), actor.capture(), eq(RrnAccessLog.Purpose.SOCIAL_INSURANCE));
        assertThat(actor.getValue().role()).isEqualTo(RequestActor.Role.PAYROLL);
        assertThat(actor.getValue().clientIp()).isNotBlank();
    }

    @Test
    void 원문_조회_거부는_403() throws Exception {
        when(rrns.reveal(anyLong(), any(), any())).thenThrow(EmployeeException.forbidden("거부"));
        mvc.perform(get("/api/employees/1/rrn").header("X-Actor-Role", "ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/employees/1/rrn").param("purpose", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void 암호화_키가_없으면_503() throws Exception {
        when(rrns.reveal(anyLong(), any(), any())).thenThrow(new RrnCrypto.KeyNotConfiguredException("x"));
        mvc.perform(get("/api/employees/1/rrn").header("X-Actor-Role", "HR"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void 주민등록번호_등록_정정() throws Exception {
        when(rrns.save(eq(1L), any(), any())).thenReturn("000000-*******");
        mvc.perform(put("/api/employees/1/rrn").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rrn\":\"0000000000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rrnMasked").value("000000-*******"));
        verify(rrns).save(eq(1L), eq(Rrn.parse("000000-0000001")), any());
    }
}
