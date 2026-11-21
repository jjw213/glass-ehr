package com.glassehr.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.TestAuth;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 홈 대시보드 요약 API 권한. */
@WebMvcTest(DashboardController.class)
@Import(TestAuth.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService service;

    private static final DashboardService.Summary SUMMARY = new DashboardService.Summary(LocalDate.of(2099, 3, 2), 4, 1, 2,
            List.of(new DashboardService.TrendPoint("2099-03", 1, 0)));

    @Test
    void 인사와_부서장은_요약을_받는다() throws Exception {
        when(service.summary(any())).thenReturn(SUMMARY);
        for (String actor : List.of("hr1", "pay1", "admin1", "head1")) {
            mvc.perform(get("/api/dashboard/summary").header("X-Actor-Id", actor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.today").value("2099-03-02"))
                    .andExpect(jsonPath("$.headcount").value(4))
                    .andExpect(jsonPath("$.trend[0].month").value("2099-03"));
        }
    }

    @Test
    void 일반_사원은_403() throws Exception {
        mvc.perform(get("/api/dashboard/summary").header("X-Actor-Id", "emp1"))
                .andExpect(status().isForbidden());
        verify(service, never()).summary(any());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/dashboard/summary").header("X-Actor-Id", "nobody"))
                .andExpect(status().isUnauthorized());
    }
}
