package com.glassehr.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.PrivacyAccessLog.Action;
import com.glassehr.auth.PrivacyAccessLog.Item;
import com.glassehr.auth.PrivacyAccessLog.Result;
import com.glassehr.code.CommonCodeController;
import com.glassehr.code.CommonCodeService;
import com.glassehr.employee.EmployeeController;
import com.glassehr.employee.EmployeeDtos.EmployeeSummary;
import com.glassehr.employee.EmployeeDtos.EmployeeView;
import com.glassehr.employee.EmployeeException;
import com.glassehr.employee.EmployeeService;
import com.glassehr.employee.RrnService;
import com.glassehr.org.OrgController;
import com.glassehr.org.OrgDeptService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 권한 우회 시나리오(#19 완료 기준). 서비스는 mock 이고, 권한 검사는 실제 AuthInterceptor·컨트롤러가 한다.
 * 사용자·권한표는 {@link TestAuthUsers}(V013 권한표를 그대로 읽음).
 */
@WebMvcTest(controllers = {EmployeeController.class, OrgController.class, CommonCodeController.class,
        AuthController.class})
@Import(TestAuthUsers.class)
class AuthBypassTest {

    private static final LocalDate HIRE = LocalDate.of(2026, 3, 2);
    /** 부서장(head1) 범위 밖 부서 */
    private static final long OTHER_DEPT = 30;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PrivacyAccessLogger privacyLog;

    @MockitoBean
    private EmployeeService employees;

    @MockitoBean
    private RrnService rrns;

    @MockitoBean
    private OrgDeptService org;

    @MockitoBean
    private CommonCodeService codes;

    @MockitoBean
    private PrivacyLogService logs;

    private static EmployeeView view(long empId, long deptId) {
        return new EmployeeView(empId, "S01-2026-" + String.format("%03d", empId), "S01", "예시사원" + empId, "F",
                LocalDate.of(1999, 1, 1), "가상시 예시구 샘플로 " + empId, "(예시) 이력", "개발", "ENGINEERING", HIRE,
                null, null, null, "REGULAR", 2400, "HQ", "(예시) 메모", deptId, "STAFF", "MEMBER", null, null, null,
                null, "(예시) 기타", "000000-*******");
    }

    private static EmployeeSummary summary(long empId, Long deptId) {
        return new EmployeeSummary(empId, "S01-2026-" + String.format("%03d", empId), "예시사원" + empId, deptId,
                "STAFF", "MEMBER", "REGULAR", HIRE, null);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String loginId) {
        return b.header(AuthInterceptor.ACTOR_ID_HEADER, loginId);
    }

    private static final String CREATE_BODY = """
            {"siteCode":"S01","name":"예시사원","gender":"F","birthDate":"1999-01-01","address":"가상시 예시구 샘플로 1",
             "jobDescription":"개발","hireDate":"2026-03-02","employmentTypeCode":"REGULAR","deptId":10}""";

    @BeforeEach
    void setUp() {
        clearInvocations(privacyLog);
        when(employees.list(anyBoolean())).thenReturn(List.of(
                summary(TestAuthUsers.EMP_EMP, OTHER_DEPT),
                summary(TestAuthUsers.HEAD_EMP, TestAuthUsers.HEAD_DEPT),
                summary(20, TestAuthUsers.HEAD_DEPT),
                summary(21, TestAuthUsers.HEAD_SUB_DEPT),
                summary(22, OTHER_DEPT),
                summary(23, null)));
        when(employees.get(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            return switch ((int) id) {
                case 5 -> view(5, OTHER_DEPT);
                case 7 -> view(7, TestAuthUsers.HEAD_DEPT);
                case 20 -> view(20, TestAuthUsers.HEAD_DEPT);
                case 21 -> view(21, TestAuthUsers.HEAD_SUB_DEPT);
                case 22 -> view(22, OTHER_DEPT);
                default -> throw new EmployeeException(HttpStatus.NOT_FOUND, "없음");
            };
        });
    }

    // ------------------------------------------------------------ 인증 없음·위조

    @Test
    void 사용자_헤더가_없으면_모든_API가_401() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").exists());
        mvc.perform(get("/api/employees/5")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/employees/5/rrn")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/employees/export")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/org/tree")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/codes").param("groups", "JOB_GRADE")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/privacy-logs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(employees, rrns, org, codes, logs);
    }

    @Test
    void 등록되지_않은_사용자나_사용중지_사용자는_401() throws Exception {
        mvc.perform(as(get("/api/employees"), "nobody")).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/employees"), "off1")).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/employees"), "  ")).andExpect(status().isUnauthorized());
        verifyNoInteractions(employees);
    }

    @Test
    void 개인정보_API를_로그인없이_부르면_거부_로그가_남는다() throws Exception {
        mvc.perform(as(get("/api/employees/22/rrn"), "nobody")).andExpect(status().isUnauthorized());
        verify(privacyLog).logAnonymousDenied(eq("nobody"), any(), eq(22L), eq(Item.RRN), eq(Action.VIEW),
                eq("/api/employees/22/rrn"));
        mvc.perform(get("/api/employees/22")).andExpect(status().isUnauthorized());
        verify(privacyLog).logAnonymousDenied(isNull(), any(), eq(22L), eq(Item.EMP_PERSONAL), eq(Action.VIEW),
                anyString());
    }

    @Test
    void 역할_헤더를_위조해도_DB_역할만_쓴다() throws Exception {
        // 일반 사원이 X-Actor-Role: HR / ADMIN 을 붙여도 등록·주민번호·로그 화면이 막힌다.
        for (String fake : new String[] {"HR", "ADMIN", "PAYROLL"}) {
            mvc.perform(as(post("/api/employees"), "emp1").header("X-Actor-Role", fake)
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isForbidden());
            mvc.perform(as(get("/api/employees/22/rrn"), "emp1").header("X-Actor-Role", fake))
                    .andExpect(status().isForbidden());
            mvc.perform(as(get("/api/admin/privacy-logs"), "emp1").header("X-Actor-Role", fake))
                    .andExpect(status().isForbidden());
        }
        verify(employees, never()).create(any(), any());
        verifyNoInteractions(rrns, logs);
    }

    // ------------------------------------------------------------ 일반 사원: 본인만

    @Test
    void 일반_사원_목록에는_본인만_나온다() throws Exception {
        mvc.perform(as(get("/api/employees"), "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].empId").value(TestAuthUsers.EMP_EMP));
    }

    @Test
    void 일반_사원은_다른_사원_상세를_못_보고_거부가_기록된다() throws Exception {
        mvc.perform(as(get("/api/employees/22"), "emp1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.address").doesNotExist());
        verify(privacyLog).log(any(), eq(22L), eq(Item.EMP_PERSONAL), eq(Action.VIEW), eq(Result.DENIED),
                eq("/api/employees/22"));
    }

    @Test
    void 일반_사원은_본인_인적사항을_원문으로_보고_조회가_기록된다() throws Exception {
        mvc.perform(as(get("/api/employees/5"), "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("가상시 예시구 샘플로 5"))
                .andExpect(jsonPath("$.birthDate").value("1999-01-01"));
        ArgumentCaptor<AuthUser> who = ArgumentCaptor.forClass(AuthUser.class);
        verify(privacyLog).log(who.capture(), eq(5L), eq(Item.EMP_PERSONAL), eq(Action.VIEW), eq(Result.SUCCESS),
                eq("/api/employees/5"));
        assertThat(who.getValue().loginId()).isEqualTo("emp1");
    }

    @Test
    void 없는_사원_조회도_기록된다() throws Exception {
        mvc.perform(as(get("/api/employees/999"), "hr1")).andExpect(status().isNotFound());
        verify(privacyLog).log(any(), eq(999L), eq(Item.EMP_PERSONAL), eq(Action.VIEW), eq(Result.NOT_FOUND), any());
    }

    @Test
    void 일반_사원은_쓰기_API를_모두_못_쓴다() throws Exception {
        mvc.perform(as(put("/api/employees/5"), "emp1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/employees/5/retire"), "emp1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(put("/api/employees/5/rrn"), "emp1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rrn\":\"000000-0000001\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/org/depts"), "emp1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(put("/api/org/depts/10"), "emp1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/org/depts/10/close"), "emp1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/code-groups/JOB_GRADE/codes"), "emp1").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(put("/api/code-groups/JOB_GRADE/codes/STAFF"), "emp1").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verify(employees, never()).update(anyLong(), any());
        verify(employees, never()).retire(anyLong(), any());
        verify(org, never()).create(any());
        verify(org, never()).change(anyLong(), any());
        verify(org, never()).close(anyLong(), any());
        verify(codes, never()).create(any(), any());
        verify(codes, never()).update(any(), any(), any());
        verifyNoInteractions(rrns);
    }

    @Test
    void 일반_사원_다운로드는_거부되고_기록된다() throws Exception {
        mvc.perform(as(get("/api/employees/export"), "emp1")).andExpect(status().isForbidden());
        verify(privacyLog).log(any(), isNull(), eq(Item.EMP_LIST), eq(Action.DOWNLOAD), eq(Result.DENIED),
                eq("/api/employees/export"));
        verify(privacyLog, never()).logEach(any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------ 부서장: 자기 부서(하위 포함)

    @Test
    void 부서장_목록은_자기_부서와_하위부서_그리고_본인만() throws Exception {
        mvc.perform(as(get("/api/employees"), "head1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].empId").value(TestAuthUsers.HEAD_EMP))
                .andExpect(jsonPath("$[1].empId").value(20))
                .andExpect(jsonPath("$[2].empId").value(21));
    }

    @Test
    void 부서장은_하위부서원_상세를_보되_인적사항은_서버에서_가려진다() throws Exception {
        mvc.perform(as(get("/api/employees/21"), "head1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("예시사원21"))
                .andExpect(jsonPath("$.address").value("가상시 ***"))
                .andExpect(jsonPath("$.birthDate").doesNotExist())
                .andExpect(jsonPath("$.careerHistory").doesNotExist())
                .andExpect(jsonPath("$.employmentNote").doesNotExist())
                .andExpect(jsonPath("$.otherNote").doesNotExist())
                .andExpect(jsonPath("$.rrnMasked").doesNotExist());
        // 원문을 주지 않았으므로 조회 로그 대상이 아니다.
        verify(privacyLog, never()).log(any(), any(), any(), any(), eq(Result.SUCCESS), any());
    }

    @Test
    void 부서장은_범위_밖_사원을_경로만_바꿔_조회할_수_없다() throws Exception {
        mvc.perform(as(get("/api/employees/22"), "head1")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/employees/5"), "head1")).andExpect(status().isForbidden());
        verify(privacyLog).log(any(), eq(22L), eq(Item.EMP_PERSONAL), eq(Action.VIEW), eq(Result.DENIED), any());
        verify(privacyLog).log(any(), eq(5L), eq(Item.EMP_PERSONAL), eq(Action.VIEW), eq(Result.DENIED), any());
    }

    @Test
    void 부서장은_주민번호_원문_다운로드_쓰기를_못한다() throws Exception {
        mvc.perform(as(get("/api/employees/21/rrn"), "head1")).andExpect(status().isForbidden());
        verify(privacyLog).log(any(), eq(21L), eq(Item.RRN), eq(Action.VIEW), eq(Result.DENIED), any());
        mvc.perform(as(get("/api/employees/export"), "head1")).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/org/depts/10/close"), "head1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rrns);
    }

    // ------------------------------------------------------------ 관리자·담당자

    @Test
    void 관리자는_주민번호_원문을_못_보고_거부가_기록된다() throws Exception {
        mvc.perform(as(get("/api/employees/22/rrn"), "admin1")).andExpect(status().isForbidden());
        verify(privacyLog).log(any(), eq(22L), eq(Item.RRN), eq(Action.VIEW), eq(Result.DENIED), any());
        verifyNoInteractions(rrns);
    }

    @Test
    void 로그_화면은_관리자만() throws Exception {
        when(logs.search(any(), any(), any(), any()))
                .thenReturn(new PrivacyLogService.LogPage(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 11, 13),
                        List.of(), false));
        for (String u : new String[] {"hr1", "pay1", "emp1", "head1"}) {
            mvc.perform(as(get("/api/admin/privacy-logs"), u)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(logs);
        mvc.perform(as(get("/api/admin/privacy-logs"), "admin1").param("from", "2026-10-14").param("to", "2026-11-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated").value(false));
        verify(logs).search(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 11, 13), null, null);
    }

    @Test
    void 급여담당은_조회만_하고_쓰기는_못한다() throws Exception {
        mvc.perform(as(get("/api/employees"), "pay1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
        mvc.perform(as(post("/api/employees"), "pay1").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/code-groups/JOB_GRADE/codes"), "pay1").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verify(employees, never()).create(any(), any());
    }

    @Test
    void 인사담당_다운로드는_파일에_든_사원마다_기록된다() throws Exception {
        byte[] body = mvc.perform(as(get("/api/employees/export"), "hr1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("예시사원22").doesNotContain("가상시").doesNotContain("000000");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(privacyLog).logEach(any(), ids.capture(), eq(Item.EMP_LIST), eq(Action.DOWNLOAD),
                eq("/api/employees/export"));
        assertThat(ids.getValue()).containsExactly(5L, 7L, 20L, 21L, 22L, 23L);
    }

    @Test
    void 내_권한_정보() throws Exception {
        mvc.perform(as(get("/api/auth/me"), "head1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("head1"))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.permissions[0]").value("EMP_READ_DEPT"))
                .andExpect(jsonPath("$.managedDeptIds.length()").value(2));
    }
}
