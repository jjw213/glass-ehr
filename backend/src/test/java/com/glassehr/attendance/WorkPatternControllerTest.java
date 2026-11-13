package com.glassehr.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.attendance.WorkPatternDtos.AssignmentView;
import com.glassehr.attendance.WorkPatternDtos.WorkPatternView;
import com.glassehr.auth.TestAuth;
import com.glassehr.legalrate.LegalRateException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WorkPatternController.class)
@Import(TestAuth.class)
class WorkPatternControllerTest {

    private static final String BODY = """
            {"patternCode":"DAY","patternName":"주간","workStartMin":540,"workEndMin":945,
             "breakStartMin":720,"breakMinutes":45,"dayTypes":"WWWWWOH"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkPatternService service;

    private static WorkPatternView view() {
        return new WorkPatternView(1, "DAY", "주간", 540, 945, 720, 45, "WWWWWOH", 360, 1800, true, null, false);
    }

    @Test
    void 목록() throws Exception {
        when(service.list()).thenReturn(List.of(view()));
        mvc.perform(get("/api/attendance/work-patterns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patternCode").value("DAY"))
                .andExpect(jsonPath("$[0].dayTypes").value("WWWWWOH"));
    }

    @Test
    void 등록() throws Exception {
        when(service.create(any())).thenReturn(view());
        mvc.perform(post("/api/attendance/work-patterns").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weeklyContractMinutes").value(1800));
    }

    @Test
    void 법정_기준_위반은_422와_위반_목록() throws Exception {
        when(service.create(any())).thenThrow(AttendanceException.rejected(List.of(
                new WorkPatternRules.Violation(WorkPatternRules.Code.WEEKLY_HOLIDAY_REQUIRED, "주휴일을 지정해 주세요"))));
        mvc.perform(post("/api/attendance/work-patterns").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("주휴일을 지정해 주세요"))
                .andExpect(jsonPath("$.violations[0].code").value("WEEKLY_HOLIDAY_REQUIRED"));
    }

    @Test
    void 기준값이_없으면_409() throws Exception {
        LegalRateException missing = org.mockito.Mockito.mock(LegalRateException.class);
        when(missing.itemCode()).thenReturn("DAILY_STANDARD_WORK_HOURS");
        when(missing.asOfDate()).thenReturn(LocalDate.of(2026, 11, 13));
        when(service.create(any())).thenThrow(missing);
        mvc.perform(post("/api/attendance/work-patterns").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("DAILY_STANDARD_WORK_HOURS")));
    }

    @Test
    void 요일_구분_형식이_틀리면_400() throws Exception {
        String bad = BODY.replace("WWWWWOH", "WWWWW");
        mvc.perform(post("/api/attendance/work-patterns").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
        String noHoliday = BODY.replace("\"dayTypes\":\"WWWWWOH\"", "\"dayTypes\":null");
        mvc.perform(post("/api/attendance/work-patterns").contentType(MediaType.APPLICATION_JSON).content(noHoliday))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any());
    }

    @Test
    void 미리보기_검사() throws Exception {
        when(service.check(any())).thenReturn(new WorkPatternRules.Result(360, 1800, List.of()));
        mvc.perform(post("/api/attendance/work-patterns/check").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.dailyContractMinutes").value(360));
    }

    @Test
    void 배정과_이력() throws Exception {
        AssignmentView a = new AssignmentView(7, 5, 1, "DAY", "주간", LocalDate.of(2026, 12, 1), null, null);
        when(service.assign(eq(5L), any(), eq("hr1"))).thenReturn(a);
        mvc.perform(post("/api/attendance/employees/5/work-patterns").header("X-Actor-Id", "hr1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patternId\":1,\"validFrom\":\"2026-12-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validFrom").value("2026-12-01"));
        // 요청자는 서버가 확인한 로그인 사용자(hr1)로 기록된다.
        verify(service).assign(eq(5L), any(), eq("hr1"));

        when(service.history(5)).thenReturn(List.of(a));
        mvc.perform(get("/api/attendance/employees/5/work-patterns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patternCode").value("DAY"));
    }

    @Test
    void 적용_시작일이_없으면_400() throws Exception {
        mvc.perform(post("/api/attendance/employees/5/work-patterns").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patternId\":1}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).assign(anyLong(), any(), any());
    }

    @Test
    void 일반_사원은_근무유형을_볼_수_있지만_등록_배정은_403() throws Exception {
        when(service.list()).thenReturn(List.of(view()));
        mvc.perform(get("/api/attendance/work-patterns").header("X-Actor-Id", "emp1"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/attendance/work-patterns").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/attendance/employees/5/work-patterns").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patternId\":1,\"validFrom\":\"2026-12-01\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/employees/5/work-patterns").header("X-Actor-Id", "emp1"))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any());
        verify(service, never()).assign(anyLong(), any(), any());
        verify(service, never()).history(anyLong());
    }

    @Test
    void 급여담당은_배정을_조회만_할_수_있다() throws Exception {
        when(service.history(5)).thenReturn(List.of());
        mvc.perform(get("/api/attendance/employees/5/work-patterns").header("X-Actor-Id", "pay1"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/attendance/work-patterns").header("X-Actor-Id", "pay1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any());
    }

    @Test
    void 사용자를_모르면_401() throws Exception {
        mvc.perform(get("/api/attendance/work-patterns").header("X-Actor-Id", "nobody"))
                .andExpect(status().isUnauthorized());
        verify(service, never()).list();
    }

    @Test
    void 기준일_배정이_없으면_204() throws Exception {
        when(service.effective(5, LocalDate.of(2026, 1, 1))).thenReturn(Optional.empty());
        mvc.perform(get("/api/attendance/employees/5/work-patterns/effective").param("date", "2026-01-01"))
                .andExpect(status().isNoContent());
    }
}
