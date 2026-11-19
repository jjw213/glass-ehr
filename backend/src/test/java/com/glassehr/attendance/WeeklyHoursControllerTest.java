package com.glassehr.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.attendance.WeeklyHoursMonitor.Limits;
import com.glassehr.attendance.WeeklyHoursMonitor.Week;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.TestAuth;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 주 52시간 모니터링 API 권한. 날짜·한도는 가짜 값(2099년, 임의 분). 기본 요청자는 인사담당(hr1). */
@WebMvcTest(WeeklyHoursController.class)
@Import(TestAuth.class)
class WeeklyHoursControllerTest {

    private static final LocalDate MON = LocalDate.of(2099, 3, 2);
    private static final Limits LIMITS = new Limits(600, 120, 60);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WeeklyHoursService service;

    @Test
    void 인사담당은_부서별_표를_본다() throws Exception {
        when(service.team(any(AuthUser.class)))
                .thenReturn(new WeeklyHoursService.TeamWeek(MON, MON.plusDays(6), MON, LIMITS, 0, 0, List.of()));
        mvc.perform(get("/api/attendance/weekly-hours/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2099-03-02"))
                .andExpect(jsonPath("$.departments").isArray());
    }

    @Test
    void 일반_사원은_부서별_표를_못_보고_내_주간만_본다() throws Exception {
        Week empty = WeeklyHoursMonitor.judge(List.of(), LIMITS);
        when(service.mine(any(AuthUser.class))).thenReturn(new WeeklyHoursService.MyWeek(MON, MON.plusDays(6), MON, LIMITS,
                new WeeklyHoursService.EmpWeek(1L, "E0001", "가짜사원", 10L, empty)));
        mvc.perform(get("/api/attendance/weekly-hours/team").header("X-Actor-Id", "emp1"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/weekly-hours/me").header("X-Actor-Id", "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.week.status").value("NORMAL"))
                .andExpect(jsonPath("$.me.week.limitMinutes").value(720));
    }

    @Test
    void 모르는_사용자는_거부() throws Exception {
        mvc.perform(get("/api/attendance/weekly-hours/me").header("X-Actor-Id", "nobody"))
                .andExpect(status().is4xxClientError());
    }
}
