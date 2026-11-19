package com.glassehr.appointment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.appointment.AppointmentDtos.BulkAppointmentResult;
import com.glassehr.appointment.AppointmentDtos.BulkItemResult;
import com.glassehr.appointment.HrAppointment.Snapshot;
import com.glassehr.auth.AuthInterceptor;
import com.glassehr.auth.TestAuthUsers;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** #60 발령 권한: 인사담당만 등록·확정·일괄 발령, 일반 사원은 본인의 확정 발령 이력만. */
@WebMvcTest(AppointmentController.class)
@Import(TestAuthUsers.class)
class AppointmentAuthTest {

    private static final LocalDate D = LocalDate.of(2026, 12, 1);
    private static final String ONE = """
            {"apptTypeCode":"TRANSFER","effectiveDate":"2026-12-01","afterDeptId":20,"reason":"조직 개편"}""";
    private static final String BULK = "{\"empIds\":[1,2],\"confirm\":true,\"appointment\":" + ONE + "}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AppointmentService service;

    @MockitoBean
    private AppointmentBulkService bulk;

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String loginId) {
        return b.header(AuthInterceptor.ACTOR_ID_HEADER, loginId);
    }

    private static AppointmentView view(long id, boolean confirmed) {
        return new AppointmentView(id, "AP-2026-00000" + id, "TRANSFER", TestAuthUsers.EMP_EMP, D,
                new Snapshot(10L, "STAFF", null, EmpStatus.ACTIVE), new Snapshot(20L, "STAFF", null, EmpStatus.ACTIVE),
                "조직 개편", null, null, confirmed, confirmed ? LocalDateTime.of(2026, 11, 18, 9, 0) : null,
                LocalDateTime.of(2026, 11, 18, 9, 0), LocalDateTime.of(2026, 11, 18, 9, 0));
    }

    @Test
    void 일반_사원은_본인의_확정_발령만_본다() throws Exception {
        when(service.listByEmployee(TestAuthUsers.EMP_EMP)).thenReturn(List.of(view(1, true), view(2, false)));
        mvc.perform(as(get("/api/employees/" + TestAuthUsers.EMP_EMP + "/appointments"), "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].apptId").value(1));
    }

    @Test
    void 일반_사원과_부서장은_다른_사원_이력을_볼_수_없다() throws Exception {
        mvc.perform(as(get("/api/employees/7/appointments"), "emp1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("본인 발령 이력만 볼 수 있어요"));
        mvc.perform(as(get("/api/employees/5/appointments"), "head1")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/appointments"), "emp1")).andExpect(status().isForbidden());
        verify(service, never()).listByEmployee(anyLong());
        verify(service, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void 인사담당은_다른_사원의_미확정_발령까지_본다() throws Exception {
        when(service.listByEmployee(TestAuthUsers.EMP_EMP)).thenReturn(List.of(view(1, true), view(2, false)));
        mvc.perform(as(get("/api/employees/" + TestAuthUsers.EMP_EMP + "/appointments"), "hr1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 인사담당이_아니면_등록_확정_취소_일괄_발령을_못_한다() throws Exception {
        for (String who : List.of("emp1", "head1", "pay1")) {
            mvc.perform(as(post("/api/employees/5/appointments"), who).contentType(MediaType.APPLICATION_JSON)
                    .content(ONE)).andExpect(status().isForbidden());
            mvc.perform(as(post("/api/appointments/1/confirm"), who)).andExpect(status().isForbidden());
            mvc.perform(as(post("/api/appointments/1/cancel"), who).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"착오\"}")).andExpect(status().isForbidden());
            mvc.perform(as(post("/api/appointments/bulk"), who).contentType(MediaType.APPLICATION_JSON)
                    .content(BULK)).andExpect(status().isForbidden());
        }
        verify(service, never()).create(anyLong(), any());
        verify(service, never()).confirm(anyLong());
        verify(bulk, never()).createAll(any());
    }

    @Test
    void 인사담당의_일괄_발령은_사원별_결과를_돌려준다() throws Exception {
        when(bulk.createAll(any())).thenReturn(new BulkAppointmentResult(1, 1, List.of(
                new BulkItemResult(1, true, view(1, true), null),
                new BulkItemResult(2, false, null, "퇴직한 사원이에요"))));
        mvc.perform(as(post("/api/appointments/bulk"), "hr1").contentType(MediaType.APPLICATION_JSON).content(BULK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.items[1].ok").value(false))
                .andExpect(jsonPath("$.items[1].error").value("퇴직한 사원이에요"));
    }

    @Test
    void 일괄_발령_요청_검사() throws Exception {
        mvc.perform(as(post("/api/appointments/bulk"), "hr1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empIds\":[],\"confirm\":false,\"appointment\":" + ONE + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("발령할 사원을 선택해 주세요"));
        mvc.perform(as(post("/api/appointments/bulk"), "hr1").contentType(MediaType.APPLICATION_JSON)
                        .content(BULK.replace("조직 개편", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("발령 사유를 입력해 주세요(근로기준법 제23조)"));
        verify(bulk, never()).createAll(any());
    }
}
