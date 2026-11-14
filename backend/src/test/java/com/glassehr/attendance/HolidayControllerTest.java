package com.glassehr.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.attendance.HolidayDtos.HolidayRef;
import com.glassehr.attendance.HolidayDtos.HolidayView;
import com.glassehr.attendance.HolidayDtos.ImportResult;
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

/** 휴일 달력 API. 날짜·이름은 가짜 값(2099년)이다. */
@WebMvcTest(HolidayController.class)
@Import(TestAuth.class)
class HolidayControllerTest {

    private static final String SUBST = """
            {"originalHolidayId":1,"substituteDate":"2099-03-05","agreementId":3}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HolidayService service;

    private static HolidayView replacement() {
        return new HolidayView(2, LocalDate.of(2099, 3, 5), "시험 공휴일 대체", "REPLACEMENT", "대체된 휴일", true, true,
                null, new HolidayRef(1, LocalDate.of(2099, 3, 1), "시험 공휴일"), 3L, "시험 합의", null, null, null);
    }

    @Test
    void 달력_조회는_일반_사원도_되고_연도를_넘긴다() throws Exception {
        when(service.year(2099)).thenReturn(List.of(replacement()));
        mvc.perform(get("/api/attendance/holidays").param("year", "2099").header("X-Actor-Id", "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("REPLACEMENT"))
                .andExpect(jsonPath("$[0].date").value("2099-03-05"))
                .andExpect(jsonPath("$[0].replaces.date").value("2099-03-01"));
    }

    @Test
    void 연도가_없으면_올해() throws Exception {
        when(service.today()).thenReturn(LocalDate.of(2099, 6, 1));
        when(service.year(2099)).thenReturn(List.of());
        mvc.perform(get("/api/attendance/holidays")).andExpect(status().isOk());
        verify(service).year(2099);
    }

    @Test
    void 휴일_대체_등록은_요청자를_서버에서_받는다() throws Exception {
        when(service.substitute(any(), eq("hr1"))).thenReturn(replacement());
        mvc.perform(post("/api/attendance/holidays/substitutions").contentType(MediaType.APPLICATION_JSON).content(SUBST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agreementId").value(3));
    }

    @Test
    void 서면합의_없이_휴일_대체를_요청하면_400() throws Exception {
        mvc.perform(post("/api/attendance/holidays/substitutions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalHolidayId\":1,\"substituteDate\":\"2099-03-05\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("근로자대표 서면합의 문서를 연결해 주세요(근로기준법 제55조 제2항 단서)"));
        verify(service, never()).substitute(any(), anyString());
    }

    @Test
    void 합의_요건_위반은_422() throws Exception {
        when(service.substitute(any(), anyString())).thenThrow(
                new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "유효기간 밖", List.of()));
        mvc.perform(post("/api/attendance/holidays/substitutions").contentType(MediaType.APPLICATION_JSON).content(SUBST))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("유효기간 밖"));
    }

    @Test
    void 공휴일_CSV를_text_csv로_받는다() throws Exception {
        String csv = "날짜,이름,종류,출처,검증일\n2099-03-01,시험,법정공휴일,시험 출처,2099-01-01\n";
        when(service.importPublicHolidays(csv, "hr1")).thenReturn(new ImportResult(1, 0, List.of()));
        mvc.perform(post("/api/attendance/holidays/import").contentType("text/csv;charset=UTF-8").content(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(1));
    }

    @Test
    void 서면합의_등록은_문서_참조가_필수() throws Exception {
        mvc.perform(post("/api/attendance/agreements").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"시험 합의","repType":"MAJORITY_UNION","signedDate":"2099-01-01",
                         "effectiveFrom":"2099-01-01","documentRef":" "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("합의서 문서(파일 번호·보관 위치)를 입력해 주세요"));
    }

    @Test
    void 일반_사원은_등록_취소_불러오기와_합의_조회가_403() throws Exception {
        mvc.perform(post("/api/attendance/holidays/substitutions").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON).content(SUBST))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/attendance/holidays/company").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2099-05-10\",\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/attendance/holidays/import").header("X-Actor-Id", "emp1")
                        .contentType("text/csv").content("x"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/attendance/holidays/1").header("X-Actor-Id", "emp1"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/agreements").header("X-Actor-Id", "emp1"))
                .andExpect(status().isForbidden());
        verify(service, never()).delete(1L);
    }

    @Test
    void 로그인하지_않으면_달력도_401() throws Exception {
        mvc.perform(get("/api/attendance/holidays").header("X-Actor-Id", "nobody"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 취소는_204() throws Exception {
        mvc.perform(delete("/api/attendance/holidays/7")).andExpect(status().isNoContent());
        verify(service).delete(7L);
    }
}
