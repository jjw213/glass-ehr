package com.glassehr.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.attendance.LaborAgreementDtos.AgreementView;
import com.glassehr.attendance.LaborAgreementDtos.CheckResult;
import com.glassehr.attendance.LaborAgreementDtos.ExpiringView;
import com.glassehr.attendance.LaborAgreementDtos.Status;
import com.glassehr.auth.TestAuth;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LaborAgreementController.class)
@Import(TestAuth.class)
class LaborAgreementControllerTest {

    private static final LocalDate D = LocalDate.of(2026, 1, 1);
    private static final String BODY = """
            {"agreementType":"SELECTIVE","title":"선택적 근로시간제 합의","signedDate":"2025-12-20",
             "validFrom":"2026-01-01","validUntil":"2026-12-31","scopeType":"ALL","targetDesc":"연구직 전원",
             "repType":"MAJORITY_UNION","repUnionName":"가상노동조합","documentRef":"문서함 A-1"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LaborAgreementService service;

    @MockitoBean
    private LaborAgreementGuard guard;

    private static AgreementView view() {
        return new AgreementView(1, AgreementType.SELECTIVE, "선택적 근로시간제", "근로기준법 제52조", "합의", D, D,
                D.plusYears(1).minusDays(1), LaborAgreement.Scope.ALL, null, "연구직 전원",
                LaborAgreement.RepType.MAJORITY_UNION, "가상노동조합", null, null, null, null, "문서함 A-1", Status.ACTIVE, 30L, 0,
                "hr1", LocalDateTime.of(2026, 1, 1, 9, 0));
    }

    @Test
    void 유형_목록에_근거_조문() throws Exception {
        mvc.perform(get("/api/attendance/agreements/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(AgreementType.values().length))
                .andExpect(jsonPath("$[0].code").value("FLEXIBLE_WRITTEN"))
                .andExpect(jsonPath("$[0].article").value("근로기준법 제51조 제2항"));
    }

    @Test
    void 등록은_요청자를_서버가_정한다() throws Exception {
        when(service.create(any(), eq("hr1"))).thenReturn(view());
        mvc.perform(post("/api/attendance/agreements").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void 필수값이_없으면_400() throws Exception {
        mvc.perform(post("/api/attendance/agreements").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"targetDesc\":\"연구직 전원\",", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("대상 근로자 범위를 입력해 주세요"));
        mvc.perform(post("/api/attendance/agreements").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("SELECTIVE", "NOPE")))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any(), any());
    }

    /** #26 HolidayControllerTest 에 있던 검사. 서면합의 등록 API 가 이 컨트롤러로 옮겨 와 여기서 확인한다. */
    @Test
    void 서면합의_등록은_문서_참조가_필수() throws Exception {
        mvc.perform(post("/api/attendance/agreements").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"documentRef\":\"문서함 A-1\"", "\"documentRef\":\" \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("합의서 문서(파일 번호·보관 위치)를 입력해 주세요"));
        verify(service, never()).create(any(), any());
    }

    @Test
    void 유효_합의서_확인() throws Exception {
        when(guard.check(AgreementType.SELECTIVE, D, null, "S01"))
                .thenReturn(new CheckResult(false, AgreementType.SELECTIVE, null, null, null, null, "없어요"));
        mvc.perform(get("/api/attendance/agreements/check").param("type", "SELECTIVE").param("from", "2026-01-01")
                        .param("siteCode", " S01 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("없어요"));
        when(guard.check(eq(AgreementType.SELECTIVE), eq(D), eq(D), isNull()))
                .thenThrow(AttendanceException.badRequest("x"));
        mvc.perform(get("/api/attendance/agreements/check").param("type", "SELECTIVE").param("from", "2026-01-01")
                        .param("until", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 만료_예정() throws Exception {
        when(service.expiring(null)).thenReturn(new ExpiringView(30, List.of(view())));
        mvc.perform(get("/api/attendance/agreements/expiring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.agreements[0].daysUntilExpiry").value(30));
    }

    @Test
    void 첨부_올리기와_형식_거부() throws Exception {
        when(service.addFile(eq(1L), eq("a.pdf"), any(), eq("hr1"))).thenReturn(new AgreementFileView(9, 1, "a.pdf",
                "application/pdf", 10, "0".repeat(64), "hr1", LocalDateTime.of(2026, 1, 1, 9, 0)));
        mvc.perform(multipart("/api/attendance/agreements/1/files")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.7 x".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileId").value(9));
        when(service.addFile(eq(1L), eq("b.exe"), any(), eq("hr1"))).thenThrow(
                new AttendanceException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDF만", List.of()));
        mvc.perform(multipart("/api/attendance/agreements/1/files")
                        .file(new MockMultipartFile("file", "b.exe", "application/pdf", new byte[] {1})))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("PDF만"));
    }

    @Test
    void 첨부_내려받기는_저장된_형식과_파일명으로() throws Exception {
        LaborAgreementFile f = new LaborAgreementFile(1, "합의서.pdf", "application/pdf", "%PDF-1.7".getBytes(),
                "0".repeat(64), "hr1", LocalDateTime.of(2026, 1, 1, 9, 0));
        when(service.file(1, 9)).thenReturn(f);
        mvc.perform(get("/api/attendance/agreements/1/files/9"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes("%PDF-1.7".getBytes()));
    }

    @Test
    void 첨부_삭제() throws Exception {
        mvc.perform(delete("/api/attendance/agreements/1/files/9")).andExpect(status().isNoContent());
        verify(service).deleteFile(1, 9);
    }

    @Test
    void 급여담당은_조회만_되고_등록은_403() throws Exception {
        when(service.list()).thenReturn(List.of(view()));
        mvc.perform(get("/api/attendance/agreements").header("X-Actor-Id", "pay1")).andExpect(status().isOk());
        mvc.perform(post("/api/attendance/agreements").header("X-Actor-Id", "pay1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any(), any());
    }

    @Test
    void 일반_사원은_조회도_403() throws Exception {
        mvc.perform(get("/api/attendance/agreements").header("X-Actor-Id", "emp1")).andExpect(status().isForbidden());
        verify(service, never()).list();
        verify(service, never()).get(anyLong());
    }
}
