package com.glassehr.retirement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.AuthInterceptor;
import com.glassehr.auth.TestAuth;
import com.glassehr.employee.EmployeeException;
import com.glassehr.retirement.DismissalNoticeChecker.NoticeCheck;
import com.glassehr.retirement.RetirementDtos.CertificateItem;
import com.glassehr.retirement.RetirementDtos.CertificateView;
import com.glassehr.retirement.RetirementDtos.NoticeView;
import com.glassehr.retirement.RetirementDtos.RetirementView;
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

@WebMvcTest(RetirementController.class)
@Import(TestAuth.class)
class RetirementControllerTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 30);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RetirementService service;

    private static RetirementView view(NoticeCheck check, List<String> warnings) {
        return new RetirementView(3, 1, "S01-2024-001", "예시사원", "DISMISSAL", D, D.plusYears(1),
                new NoticeView(check, D.minusDays(3), D.minusDays(3), null, 3, 7), List.of(), warnings, "hr1", null);
    }

    @Test
    void 등록은_201이고_예고_미달_경고를_돌려준다() throws Exception {
        when(service.register(eq("C001"), any(), eq("hr1"))).thenReturn(view(NoticeCheck.SHORT, List.of("7일 모자라요")));
        mvc.perform(post("/api/retirements").contentType(MediaType.APPLICATION_JSON).content("""
                        {"empId":1,"retireDate":"2026-11-30","reasonCode":"DISMISSAL",
                         "dismissalNoticeDate":"2026-11-27","writtenNoticeDate":"2026-11-27"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notice.check").value("SHORT"))
                .andExpect(jsonPath("$.notice.shortfallDays").value(7))
                .andExpect(jsonPath("$.warnings[0]").value("7일 모자라요"));
    }

    @Test
    void 필수값이_없으면_400() throws Exception {
        mvc.perform(post("/api/retirements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empId\":1,\"reasonCode\":\"VOLUNTARY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("퇴직일을 입력해 주세요"));
        mvc.perform(post("/api/retirements").contentType(MediaType.APPLICATION_JSON).content("""
                        {"empId":1,"retireDate":"2026-11-30","reasonCode":"DISMISSAL","noticeExempt":"NOPE"}"""))
                .andExpect(status().isBadRequest());
        verify(service, never()).register(any(), any(), any());
    }

    @Test
    void 서비스_오류와_명부_오류는_상태와_메시지로() throws Exception {
        when(service.register(any(), any(), any())).thenThrow(
                new RetirementException(HttpStatus.BAD_REQUEST, "해고는 해고 예고일을 입력해야 해요(근로기준법 제26조)"));
        mvc.perform(post("/api/retirements").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empId\":1,\"retireDate\":\"2026-11-30\",\"reasonCode\":\"DISMISSAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("해고는 해고 예고일을 입력해야 해요(근로기준법 제26조)"));

        when(service.get(eq("C001"), anyLong())).thenThrow(new EmployeeException(HttpStatus.CONFLICT, "이미 퇴직"));
        mvc.perform(get("/api/retirements/1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("이미 퇴직"));
    }

    @Test
    void 체크리스트_완료_표시() throws Exception {
        when(service.updateChecklist(eq("C001"), eq(1L), eq("HANDOVER"), any(), eq("hr1")))
                .thenReturn(view(NoticeCheck.OK, List.of()));
        mvc.perform(put("/api/retirements/1/checklist/HANDOVER").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"done\":true}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/retirements/1/checklist/HANDOVER").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 보존기한_경과_목록() throws Exception {
        when(service.retentionExpired(eq("C001"), LocalDate.of(2026, 11, 16))).thenReturn(List.of(new RetirementDtos.RetentionExpiredRow(
                5, "S01-2020-005", "예시사원", LocalDate.of(2023, 1, 1), "VOLUNTARY", LocalDate.of(2026, 1, 1), 319)));
        mvc.perform(get("/api/retirements/retention-expired").param("asOf", "2026-11-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].daysOverdue").value(319));
        mvc.perform(get("/api/retirements/retention-expired").param("asOf", "bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 사용증명서_발급과_조회() throws Exception {
        CertificateView v = new CertificateView(9, 1, "S01-2024-001", D, List.of(CertificateItem.USE_PERIOD), null,
                "예시사원", LocalDate.of(2024, 1, 2), D, null, null, null, null, null, "hr1", null);
        when(service.issueCertificate(eq("C001"), eq(1L), any(), eq("hr1"))).thenReturn(v);
        when(service.certificate(eq("C001"), eq(9L))).thenReturn(v);
        mvc.perform(post("/api/employees/1/employment-certificates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedOn\":\"2026-11-30\",\"items\":[\"USE_PERIOD\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0]").value("USE_PERIOD"))
                .andExpect(jsonPath("$.wageAmount").doesNotExist());
        mvc.perform(post("/api/employees/1/employment-certificates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedOn\":\"2026-11-30\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("근로자가 요구한 기재 항목을 하나 이상 골라 주세요"));
        mvc.perform(get("/api/employment-certificates/9")).andExpect(status().isOk())
                .andExpect(jsonPath("$.certId").value(9));
    }

    @Test
    void 급여_담당은_조회만_되고_발급은_403() throws Exception {
        mvc.perform(post("/api/employees/1/employment-certificates").header(AuthInterceptor.ACTOR_ID_HEADER, "pay1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedOn\":\"2026-11-30\",\"items\":[\"USE_PERIOD\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/employees/1/employment-certificates").header(AuthInterceptor.ACTOR_ID_HEADER, "pay1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/retirements/1").header(AuthInterceptor.ACTOR_ID_HEADER, "emp1"))
                .andExpect(status().isForbidden());
    }
}
