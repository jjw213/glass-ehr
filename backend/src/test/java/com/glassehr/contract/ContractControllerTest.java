package com.glassehr.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.contract.ContractDtos.AlertRow;
import com.glassehr.contract.ContractDtos.AlertType;
import com.glassehr.contract.ContractDtos.ContractRequest;
import com.glassehr.contract.ContractDtos.ContractView;
import com.glassehr.contract.ContractTermsChecker.MissingTerm;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContractController.class)
class ContractControllerTest {

    private static final LocalDate D = LocalDate.of(2026, 3, 2);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContractService service;

    private static ContractView view(List<MissingTerm> missing) {
        return new ContractView(7, 1, "REGULAR", D, D, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, missing);
    }

    private static final String BODY = """
            {"contractTypeCode":"REGULAR","contractDate":"2026-03-02","startDate":"2026-03-02"%s}""";

    @Test
    void 등록은_201이고_누락_경고를_함께_돌려준다() throws Exception {
        when(service.create(anyLong(), any())).thenReturn(
                view(List.of(new MissingTerm("WAGE", "임금(금액·지급 단위)", "근로기준법 제17조 제1항 제1호"))));
        mvc.perform(post("/api/employees/1/contracts").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(",\"wageAmount\":2500000,\"wageUnitCode\":\"MONTHLY\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractId").value(7))
                .andExpect(jsonPath("$.missingTerms[0].code").value("WAGE"))
                .andExpect(jsonPath("$.missingTerms[0].basis").value("근로기준법 제17조 제1항 제1호"));
        ArgumentCaptor<ContractRequest> req = ArgumentCaptor.forClass(ContractRequest.class);
        verify(service).create(eq(1L), req.capture());
        assertThat(req.getValue().wageAmount()).isEqualTo(2_500_000L);
    }

    @Test
    void 필수값_누락과_형식_오류는_400() throws Exception {
        mvc.perform(post("/api/employees/1/contracts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractTypeCode\":\"REGULAR\",\"startDate\":\"2026-03-02\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("계약 체결일을 입력해 주세요"));
        mvc.perform(post("/api/employees/1/contracts").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(",\"wageUnitCode\":\"YEARLY\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/employees/1/contracts").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(",\"wageAmount\":2500000.5")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/employees/1/contracts").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(",\"wageAmount\":-1")))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(anyLong(), any());
    }

    @Test
    void 서비스_오류는_상태와_메시지로_돌려준다() throws Exception {
        when(service.update(anyLong(), any())).thenThrow(new ContractException(HttpStatus.CONFLICT, "고칠 수 없어요"));
        mvc.perform(put("/api/contracts/7").contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("고칠 수 없어요"));
    }

    @Test
    void 교부일_기록() throws Exception {
        when(service.recordDelivery(anyLong(), any())).thenReturn(view(List.of()));
        mvc.perform(post("/api/contracts/7/delivery").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveredDate\":\"2026-03-02\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/contracts/7/delivery").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("서면 교부일을 입력해 주세요"));
    }

    @Test
    void 알림은_기준일을_받고_없으면_null로_넘긴다() throws Exception {
        when(service.alerts(any())).thenReturn(List.of(new AlertRow(AlertType.CONTRACT_EXPIRING, 1, "S01-2026-001",
                "예시사원", 7, D, null, D, 3L)));
        mvc.perform(get("/api/contracts/alerts").param("asOf", "2026-02-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CONTRACT_EXPIRING"))
                .andExpect(jsonPath("$[0].daysLeft").value(3));
        verify(service).alerts(LocalDate.of(2026, 2, 27));
        mvc.perform(get("/api/contracts/alerts")).andExpect(status().isOk());
        verify(service).alerts(isNull());
        mvc.perform(get("/api/contracts/alerts").param("asOf", "2026/02/27")).andExpect(status().isBadRequest());
    }

    @Test
    void 미교부_목록과_이력() throws Exception {
        when(service.undelivered()).thenReturn(List.of());
        mvc.perform(get("/api/contracts/undelivered")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        when(service.list(1)).thenReturn(List.of(view(List.of())));
        mvc.perform(get("/api/employees/1/contracts")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contractId").value(7));
    }
}
