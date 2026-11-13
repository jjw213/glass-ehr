package com.glassehr.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.appointment.AppointmentDtos.AppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.appointment.HrAppointment.Snapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppointmentController.class)
class AppointmentControllerTest {

    private static final LocalDate D = LocalDate.of(2026, 12, 1);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AppointmentService service;

    private static AppointmentView view(boolean confirmed) {
        return new AppointmentView(7, "AP-2026-000007", "LEAVE", 1, D,
                new Snapshot(10L, "STAFF", null, EmpStatus.ACTIVE), new Snapshot(10L, "STAFF", null, EmpStatus.ON_LEAVE),
                "육아휴직", D, LocalDate.of(2027, 5, 31), confirmed, confirmed ? LocalDateTime.of(2026, 11, 13, 10, 0) : null,
                LocalDateTime.of(2026, 11, 13, 9, 0), LocalDateTime.of(2026, 11, 13, 9, 0));
    }

    private static final String LEAVE_BODY = """
            {"apptTypeCode":"LEAVE","effectiveDate":"2026-12-01","reason":%s,
             "leaveStartDate":"2026-12-01","leaveEndDate":"2027-05-31"}""";

    @Test
    void 등록은_201이고_휴직일과_이전_이후값을_돌려준다() throws Exception {
        when(service.create(anyLong(), any())).thenReturn(view(false));
        mvc.perform(post("/api/employees/1/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(LEAVE_BODY.formatted("\"육아휴직\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apptNo").value("AP-2026-000007"))
                .andExpect(jsonPath("$.before.status").value("ACTIVE"))
                .andExpect(jsonPath("$.after.status").value("ON_LEAVE"))
                .andExpect(jsonPath("$.leaveEndDate").value("2027-05-31"))
                .andExpect(jsonPath("$.confirmed").value(false));
        ArgumentCaptor<AppointmentRequest> req = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(service).create(eq(1L), req.capture());
        assertThat(req.getValue().leaveStartDate()).isEqualTo(D);
    }

    @Test
    void 사유가_없거나_공백이면_400() throws Exception {
        for (String reason : List.of("null", "\"\"", "\"  \"")) {
            mvc.perform(post("/api/employees/1/appointments").contentType(MediaType.APPLICATION_JSON)
                            .content(LEAVE_BODY.formatted(reason)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("발령 사유를 입력해 주세요(근로기준법 제23조)"));
        }
        verify(service, never()).create(anyLong(), any());
    }

    @Test
    void 날짜_형식_오류는_400() throws Exception {
        mvc.perform(post("/api/employees/1/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apptTypeCode\":\"TRANSFER\",\"effectiveDate\":\"2026/12/01\",\"reason\":\"r\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 확정된_발령_수정_삭제는_서비스_오류대로_409() throws Exception {
        when(service.update(anyLong(), any())).thenThrow(AppointmentException.conflict("확정된 발령은 고칠 수 없어요"));
        mvc.perform(put("/api/appointments/7").contentType(MediaType.APPLICATION_JSON)
                        .content(LEAVE_BODY.formatted("\"r\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("확정된 발령은 고칠 수 없어요"));
        org.mockito.Mockito.doThrow(AppointmentException.conflict("지울 수 없어요")).when(service).delete(8L);
        mvc.perform(delete("/api/appointments/8")).andExpect(status().isConflict());
    }

    @Test
    void 확정_삭제_조회_경로() throws Exception {
        when(service.confirm(7L)).thenReturn(view(true));
        mvc.perform(post("/api/appointments/7/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.confirmedAt").value("2026-11-13T10:00:00"));
        mvc.perform(delete("/api/appointments/9")).andExpect(status().isNoContent());
        verify(service).delete(9L);

        when(service.search(any(), any(), any(), any())).thenReturn(List.of(view(false)));
        mvc.perform(get("/api/appointments").param("confirmed", "false").param("type", "LEAVE")
                        .param("from", "2026-12-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apptId").value(7));
        verify(service).search(eq(false), eq("LEAVE"), eq(D), isNull());

        when(service.get(7L)).thenThrow(AppointmentException.notFound("발령이 없어요: 7"));
        mvc.perform(get("/api/appointments/7")).andExpect(status().is(HttpStatus.NOT_FOUND.value()));
        when(service.listByEmployee(1L)).thenReturn(List.of());
        mvc.perform(get("/api/employees/1/appointments")).andExpect(status().isOk());
    }
}
