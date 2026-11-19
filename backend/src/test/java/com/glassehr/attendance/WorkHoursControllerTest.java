package com.glassehr.attendance;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.TestAuth;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 일별 근로시간 집계 API. 날짜는 가짜 값(2099년). 기본 요청자는 인사담당(hr1). */
@WebMvcTest(WorkHoursController.class)
@Import(TestAuth.class)
class WorkHoursControllerTest {

    private static final LocalDate MON = LocalDate.of(2099, 3, 2);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkHoursService service;

    @Test
    void 인사담당은_재계산하고_요청자를_계산자로_남긴다() throws Exception {
        when(service.recalculate(1L, MON, MON, "hr1")).thenReturn(List.of());
        mvc.perform(post("/api/attendance/daily-summaries/recalculate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empId\":1,\"from\":\"2099-03-02\",\"to\":\"2099-03-02\"}"))
                .andExpect(status().isOk());
        verify(service).recalculate(eq(1L), eq(MON), eq(MON), eq("hr1"));
    }

    @Test
    void 인사담당은_집계를_조회한다() throws Exception {
        when(service.find(1L, MON, MON.plusDays(6))).thenReturn(List.of());
        mvc.perform(get("/api/attendance/daily-summaries").param("empId", "1")
                        .param("from", "2099-03-02").param("to", "2099-03-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void 일반_사원은_재계산도_조회도_못_한다() throws Exception {
        mvc.perform(post("/api/attendance/daily-summaries/recalculate").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empId\":1,\"from\":\"2099-03-02\",\"to\":\"2099-03-02\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/daily-summaries").header("X-Actor-Id", "emp1").param("empId", "1")
                        .param("from", "2099-03-02").param("to", "2099-03-08"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 기간이_빠지면_400() throws Exception {
        mvc.perform(post("/api/attendance/daily-summaries/recalculate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empId\":1}"))
                .andExpect(status().isBadRequest());
    }
}
