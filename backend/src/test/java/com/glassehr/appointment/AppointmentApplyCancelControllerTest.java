package com.glassehr.appointment;

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

import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.appointment.AppointmentDtos.AssignmentView;
import com.glassehr.appointment.AppointmentDtos.LeavePeriodView;
import com.glassehr.appointment.HrAppointment.Snapshot;
import com.glassehr.auth.TestAuth;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** #59 취소 발령·기준일 조회·휴직 기간 API 경로. */
@WebMvcTest(AppointmentController.class)
@Import(TestAuth.class)
class AppointmentApplyCancelControllerTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 1);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AppointmentService service;

    @Test
    void 취소_발령_등록은_201이고_원_발령을_돌려준다() throws Exception {
        AppointmentView v = new AppointmentView(9, "AP-2026-000009", "TRANSFER", 1, D,
                new Snapshot(20L, "STAFF", null, EmpStatus.ACTIVE), new Snapshot(10L, "STAFF", null, EmpStatus.ACTIVE),
                "발령 착오", null, null, false, null, LocalDateTime.of(2026, 11, 13, 9, 0),
                LocalDateTime.of(2026, 11, 13, 9, 0), 7L, null);
        when(service.cancel(eq(7L), any())).thenReturn(v);
        mvc.perform(post("/api/appointments/7/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"발령 착오\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cancelOfApptId").value(7))
                .andExpect(jsonPath("$.after.deptId").value(10));
    }

    @Test
    void 취소_사유가_없으면_400() throws Exception {
        mvc.perform(post("/api/appointments/7/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("발령 사유를 입력해 주세요(근로기준법 제23조)"));
        verify(service, never()).cancel(anyLong(), any());
    }

    @Test
    void 기준일_조회와_휴직_기간_조회() throws Exception {
        when(service.assignmentOn(1L, D)).thenReturn(new AssignmentView(1, D, 20L, "MANAGER", null, EmpStatus.ON_LEAVE));
        mvc.perform(get("/api/employees/1/assignment").param("date", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deptId").value(20))
                .andExpect(jsonPath("$.empStatus").value("ON_LEAVE"));
        mvc.perform(get("/api/employees/1/assignment")).andExpect(status().isBadRequest());

        LeavePeriodView p = new LeavePeriodView(1, 5, D, LocalDate.of(2027, 4, 30), LocalDate.of(2027, 1, 31), 6L, "육아휴직");
        when(service.leavePeriods(1L, D, LocalDate.of(2026, 12, 31))).thenReturn(List.of(p));
        mvc.perform(get("/api/employees/1/leave-periods").param("from", "2026-11-01").param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].endDate").value("2027-01-31"))
                .andExpect(jsonPath("$[0].endedByApptId").value(6));
        when(service.leavePeriods(D, LocalDate.of(2026, 12, 31))).thenReturn(List.of(p));
        mvc.perform(get("/api/leave-periods").param("from", "2026-11-01").param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].empId").value(1));
    }
}
