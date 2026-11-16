package com.glassehr.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.attendance.PunchDtos.PunchView;
import com.glassehr.attendance.PunchDtos.TodayView;
import com.glassehr.auth.TestAuth;
import com.glassehr.auth.TestAuthUsers;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 출퇴근 기록 API. 사원·시각은 가짜 값(2099년). */
@WebMvcTest(PunchController.class)
@Import(TestAuth.class)
class PunchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PunchService service;

    private static final OffsetDateTime AT = OffsetDateTime.parse("2099-03-02T09:00:30+09:00");

    @Test
    void 출근은_요청자의_사원으로_기록하고_201() throws Exception {
        when(service.punch(eq(TestAuthUsers.EMP_EMP), eq("emp1"), any()))
                .thenReturn(new PunchView(1, AttendancePunch.Type.IN, AT, LocalDate.of(2099, 3, 2), null, null));
        mvc.perform(post("/api/attendance/punches").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"IN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.punchedAt").value("2099-03-02T09:00:30+09:00"))
                .andExpect(jsonPath("$.type").value("IN"));
    }

    @Test
    void 본문에_사원_ID나_기록_시각을_보내도_쓰지_않는다() throws Exception {
        when(service.punch(eq(TestAuthUsers.EMP_EMP), eq("emp1"), any()))
                .thenReturn(new PunchView(1, AttendancePunch.Type.OUT, AT, LocalDate.of(2099, 3, 2),
                        AttendancePunch.Check.OUT_WITHOUT_IN, "msg"));
        mvc.perform(post("/api/attendance/punches").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OUT\",\"empId\":100,\"punchedAt\":\"2000-01-01T00:00:00+09:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.check").value("OUT_WITHOUT_IN"));
        verify(service).punch(eq(TestAuthUsers.EMP_EMP), eq("emp1"), any());
    }

    @Test
    void 종류가_없거나_틀리면_400() throws Exception {
        mvc.perform(post("/api/attendance/punches").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("출근(IN)인지 퇴근(OUT)인지 보내 주세요"));
        mvc.perform(post("/api/attendance/punches").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"BREAK\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).punch(any(), any(), any());
    }

    @Test
    void 서비스_거부는_상태와_메시지로() throws Exception {
        when(service.punch(any(), any(), any())).thenThrow(
                new AttendanceException(HttpStatus.FORBIDDEN, "사원과 연결된 계정만 출퇴근을 기록할 수 있어요", List.of()));
        mvc.perform(post("/api/attendance/punches").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"IN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("사원과 연결된 계정만 출퇴근을 기록할 수 있어요"));
    }

    @Test
    void 오늘_요약() throws Exception {
        when(service.today(TestAuthUsers.EMP_EMP)).thenReturn(new TodayView(AT, LocalDate.of(2099, 3, 2),
                AT.minusMinutes(90), null, true, 90L, List.of()));
        mvc.perform(get("/api/attendance/punches/today").header("X-Actor-Id", "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working").value(true))
                .andExpect(jsonPath("$.elapsedMinutes").value(90))
                .andExpect(jsonPath("$.firstInAt").value("2099-03-02T07:30:30+09:00"));
    }

    @Test
    void 로그인하지_않으면_거부() throws Exception {
        mvc.perform(get("/api/attendance/punches/today").header("X-Actor-Id", ""))
                .andExpect(status().is4xxClientError());
    }
}
