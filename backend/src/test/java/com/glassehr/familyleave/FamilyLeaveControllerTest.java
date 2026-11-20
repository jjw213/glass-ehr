package com.glassehr.familyleave;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.TestAuth;
import com.glassehr.familyleave.FamilyLeaveDtos.BalanceView;
import com.glassehr.familyleave.FamilyLeaveDtos.ChildBalance;
import com.glassehr.familyleave.FamilyLeaveDtos.MonthUsage;
import com.glassehr.familyleave.FamilyLeaveDtos.RequestView;
import com.glassehr.familyleave.FamilyLeaveDtos.YearUsage;
import com.glassehr.familyleave.FamilyLeaveRequest.Kind;
import com.glassehr.familyleave.FamilyLeaveRequest.Status;
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

@WebMvcTest(FamilyLeaveController.class)
@Import(TestAuth.class)
class FamilyLeaveControllerTest {

    private static final LocalDate S = LocalDate.of(2027, 3, 1);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FamilyLeaveService service;

    private static RequestView view(Status status) {
        return new RequestView(7, 1, Kind.PARENTAL_LEAVE, "첫째", null, null, S, S.plusMonths(3), null, false, "양육",
                status, List.of("확인해 주세요"), "hr1", null, null, null, null, null, null, null, null);
    }

    @Test
    void 신청은_201이고_로그인_사용자를_신청자로_넘긴다() throws Exception {
        when(service.create(eq(1L), any(), eq("hr1"))).thenReturn(view(Status.REQUESTED));
        mvc.perform(post("/api/employees/1/family-leaves").contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind":"PARENTAL_LEAVE","childLabel":"첫째","startDate":"2027-03-01",
                         "endDate":"2027-05-31","reason":"양육"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.warnings[0]").value("확인해 주세요"));
    }

    @Test
    void 필수값이_없거나_종류가_틀리면_400() throws Exception {
        mvc.perform(post("/api/employees/1/family-leaves").contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind":"PARENTAL_LEAVE","childLabel":"첫째","startDate":"2027-03-01","endDate":"2027-05-31"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("신청 사유를 입력해 주세요"));
        mvc.perform(post("/api/employees/1/family-leaves").contentType(MediaType.APPLICATION_JSON).content("""
                        {"kind":"NOPE","startDate":"2027-03-01","endDate":"2027-05-31","reason":"r"}"""))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(anyLong(), any(), any());
    }

    @Test
    void 승인_반려_취소() throws Exception {
        when(service.approve(7L, "hr1")).thenReturn(view(Status.APPROVED));
        mvc.perform(post("/api/family-leaves/7/approve")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        when(service.reject(7L, "서류 미비", "hr1")).thenReturn(view(Status.REJECTED));
        mvc.perform(post("/api/family-leaves/7/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"서류 미비\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        when(service.cancel(eq(7L), isNull(), eq("hr1"))).thenReturn(view(Status.CANCELLED));
        mvc.perform(post("/api/family-leaves/7/cancel")).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void 서비스_오류는_상태코드와_메시지로_돌려준다() throws Exception {
        when(service.approve(7L, "hr1")).thenThrow(FamilyLeaveException.conflict("이미 처리된 신청"));
        mvc.perform(post("/api/family-leaves/7/approve"))
                .andExpect(status().is(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.error").value("이미 처리된 신청"));
    }

    @Test
    void 남은_기간_조회() throws Exception {
        MonthUsage p = new MonthUsage(12, 3, 0, 9, 0, 1, 4);
        MonthUsage c = new MonthUsage(null, 0, 0, null, 0, 0, null);
        when(service.balance(1L, LocalDate.of(2027, 6, 1))).thenReturn(new BalanceView(1, LocalDate.of(2027, 6, 1),
                List.of(new ChildBalance("첫째", p, c)), new YearUsage(2027, 90, 25, 65), List.of("안내")));
        mvc.perform(get("/api/employees/1/family-leave-balance").param("asOf", "2027-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[0].parentalLeave.remainingMonths").value(9))
                .andExpect(jsonPath("$.children[0].reducedHours.limitMonths").doesNotExist())
                .andExpect(jsonPath("$.familyCareLeave.remainingDays").value(65));
    }

    @Test
    void 상태별_목록() throws Exception {
        when(service.search(Status.REQUESTED)).thenReturn(List.of(view(Status.REQUESTED)));
        mvc.perform(get("/api/family-leaves").param("status", "REQUESTED"))
                .andExpect(jsonPath("$[0].reqId").value(7));
    }
}
