package com.glassehr.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.attendance.FlexibleWorkCalculator.WorkSystem;
import com.glassehr.attendance.FlexibleWorkService.AssignView;
import com.glassehr.attendance.FlexibleWorkService.Detail;
import com.glassehr.auth.TestAuth;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FlexibleWorkController.class)
@Import(TestAuth.class)
class FlexibleWorkControllerTest {

    private static final String BODY = """
            {"empId":5,"workSystem":"SELECTIVE","periodStart":"2026-03-01","periodEnd":"2026-03-31",
             "jobCode":"GENERAL","coreStart":"10:00","coreEnd":"15:00"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FlexibleWorkService service;

    private static AssignView view() {
        return new AssignView(1, 5, WorkSystem.SELECTIVE, "근로기준법 제52조", LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), "GENERAL", "10:00", "15:00", null, 7L, null);
    }

    @Test
    void 배정은_인사가_하고_요청자를_서버가_정한다() throws Exception {
        when(service.assign(eq("C001"), any(), eq("hr1"))).thenReturn(view());
        mvc.perform(post("/api/attendance/flex-work").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agreementId").value(7));
    }

    @Test
    void 사원은_배정할_수_없다() throws Exception {
        mvc.perform(post("/api/attendance/flex-work").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(service, never()).assign(any(), any(), any());
    }

    @Test
    void 서면합의가_없으면_422() throws Exception {
        when(service.assign(any(), any(), any())).thenThrow(new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY,
                "선택적 근로시간제는 근로자대표와의 서면합의가 있어야 해요(근로기준법 제52조).", List.of()));
        mvc.perform(post("/api/attendance/flex-work").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 사원은_자기_배정과_계획을_저장한다() throws Exception {
        when(service.mine(any())).thenReturn(List.of(view()));
        mvc.perform(get("/api/attendance/flex-work/me").header("X-Actor-Id", "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workSystem").value("SELECTIVE"));
        when(service.savePlan(eq(1L), any(), any())).thenReturn(new Detail(view(), 10628, List.of()));
        mvc.perform(put("/api/attendance/flex-work/1/plan").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"date\":\"2026-03-02\",\"start\":\"2026-03-02T09:00:00\","
                                + "\"end\":\"2026-03-02T18:00:00\",\"minutes\":480}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLimitMinutes").value(10628));
    }

    @Test
    void 사원은_전체_목록을_볼_수_없다() throws Exception {
        mvc.perform(get("/api/attendance/flex-work").header("X-Actor-Id", "emp1"))
                .andExpect(status().isForbidden());
    }
}
