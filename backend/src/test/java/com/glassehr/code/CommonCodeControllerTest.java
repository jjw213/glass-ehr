package com.glassehr.code;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeGroupView;
import com.glassehr.code.CodeDtos.CodeUpdateRequest;
import com.glassehr.code.CodeDtos.CodeView;
import com.glassehr.code.CodeDtos.CodeListItem;
import com.glassehr.code.CodeDtos.GroupCreateRequest;
import com.glassehr.code.CodeDtos.UsagePage;
import com.glassehr.code.CodeDtos.UsageSummary;
import com.glassehr.code.CodeDtos.UsageTable;
import com.glassehr.code.CodeUsageRepository.UsageRow;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.glassehr.auth.TestAuth;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommonCodeController.class)
@Import(TestAuth.class)
class CommonCodeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CommonCodeService service;

    private static CodeView view(String group, String code, String name, int sort, boolean active) {
        return new CodeView(group, code, name, null, sort, active, false, null);
    }

    @Test
    void 여러_그룹_코드를_요청_순서대로_돌려준다() throws Exception {
        Map<String, List<CodeView>> result = new LinkedHashMap<>();
        result.put("EMPLOYMENT_TYPE", List.of(view("EMPLOYMENT_TYPE", "REGULAR", "정규직", 10, true)));
        result.put("JOB_GRADE", List.of());
        when(service.activeCodes("C001", List.of("EMPLOYMENT_TYPE", "JOB_GRADE"))).thenReturn(result);

        mvc.perform(get("/api/codes").param("groups", "EMPLOYMENT_TYPE, JOB_GRADE,"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.EMPLOYMENT_TYPE[0].code").value("REGULAR"))
                .andExpect(jsonPath("$.EMPLOYMENT_TYPE[0].name").value("정규직"))
                .andExpect(jsonPath("$.EMPLOYMENT_TYPE[0].active").value(true))
                .andExpect(jsonPath("$.JOB_GRADE").isEmpty());
    }

    @Test
    void groups_파라미터가_없으면_400() throws Exception {
        mvc.perform(get("/api/codes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void 없는_그룹은_404와_메시지() throws Exception {
        when(service.listCodes("C001", "NOPE", false)).thenThrow(CodeException.groupNotFound("NOPE"));
        mvc.perform(get("/api/code-groups/NOPE/codes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("코드그룹이 없어요: NOPE"));
    }

    @Test
    void 그룹_목록() throws Exception {
        when(service.listGroups("C001")).thenReturn(List.of(new CodeGroupView("JOB_GRADE", "직급", null, 10, true, true, null)));
        mvc.perform(get("/api/code-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupCode").value("JOB_GRADE"))
                .andExpect(jsonPath("$[0].name").value("직급"))
                .andExpect(jsonPath("$[0].system").value(true));
    }

    @Test
    void 관리화면은_사용중지_코드도_요청할_수_있다() throws Exception {
        when(service.listCodes("C001", "JOB_GRADE", true)).thenReturn(List.of(
                new CodeListItem("JOB_GRADE", "OLD", "옛 직급", null, 1, false, false, null, true, 3)));
        mvc.perform(get("/api/code-groups/JOB_GRADE/codes").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].inUse").value(true))
                .andExpect(jsonPath("$[0].usageCount").value(3));
    }

    @Test
    void 코드_추가는_201() throws Exception {
        CodeCreateRequest req = new CodeCreateRequest("INTERN", "인턴", null, 40, true, "FIXED_TERM");
        when(service.create("C001", "EMPLOYMENT_TYPE", req)).thenReturn(view("EMPLOYMENT_TYPE", "INTERN", "인턴", 40, true));
        mvc.perform(post("/api/code-groups/EMPLOYMENT_TYPE/codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"INTERN\",\"name\":\"인턴\",\"sortOrder\":40,\"active\":true,"
                                + "\"sysType\":\"FIXED_TERM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("INTERN"));
    }

    @Test
    void 처리방식_목록() throws Exception {
        when(service.sysTypes("C001", "EMPLOYMENT_TYPE")).thenReturn(CodeSysTypes.of("EMPLOYMENT_TYPE"));
        mvc.perform(get("/api/code-groups/EMPLOYMENT_TYPE/sys-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("REGULAR"))
                .andExpect(jsonPath("$[3].code").value("FIXED_TERM_PART_TIME"))
                .andExpect(jsonPath("$[3].name").value("기간제·단시간"));
    }

    @Test
    void 코드값_형식이_틀리면_400이고_서비스를_부르지_않는다() throws Exception {
        mvc.perform(post("/api/code-groups/JOB_GRADE/codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"staff-1\",\"name\":\"사원\",\"sortOrder\":1,\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("코드값은 영문 대문자로 시작하고 영문 대문자·숫자·밑줄만 30자 이내로 써 주세요"));
        verify(service, never()).create(any(), any(), any());
    }

    @Test
    void 정렬순서_음수와_빈_코드명은_400() throws Exception {
        mvc.perform(put("/api/code-groups/JOB_GRADE/codes/STAFF").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"sortOrder\":-1,\"active\":true}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).update(any(), any(), any(), any());
    }

    @Test
    void 중복_코드값은_409() throws Exception {
        when(service.create(eq("C001"), eq("JOB_GRADE"), any())).thenThrow(CodeException.duplicate("JOB_GRADE", "STAFF"));
        mvc.perform(post("/api/code-groups/JOB_GRADE/codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"STAFF\",\"name\":\"사원\",\"sortOrder\":1,\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("이미 있는 코드값이에요: JOB_GRADE/STAFF"));
    }

    @Test
    void 코드_수정() throws Exception {
        CodeUpdateRequest req = new CodeUpdateRequest("사원(구)", null, 15, false, null);
        when(service.update("C001", "JOB_GRADE", "STAFF", req)).thenReturn(view("JOB_GRADE", "STAFF", "사원(구)", 15, false));
        mvc.perform(put("/api/code-groups/JOB_GRADE/codes/STAFF").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"사원(구)\",\"sortOrder\":15,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.sortOrder").value(15));
    }

    @Test
    void 그룹_추가는_201이고_그룹코드_형식이_틀리면_400() throws Exception {
        GroupCreateRequest req = new GroupCreateRequest("ZZ_USER", "사용자 그룹", null, 100, true);
        when(service.createGroup("C001", req))
                .thenReturn(new CodeGroupView("ZZ_USER", "사용자 그룹", null, 100, true, false, null));
        mvc.perform(post("/api/code-groups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"ZZ_USER\",\"name\":\"사용자 그룹\",\"sortOrder\":100,\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.system").value(false));
        mvc.perform(post("/api/code-groups").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupCode\":\"zz-user\",\"name\":\"x\",\"sortOrder\":1,\"active\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 시스템_그룹_삭제는_409와_안내() throws Exception {
        doThrow(CodeException.conflict("시스템 그룹이라 삭제할 수 없어요. 사용하지 않는 코드는 사용 안 함으로 바꿔 주세요"))
                .when(service).deleteGroup("C001", "JOB_GRADE");
        mvc.perform(delete("/api/code-groups/JOB_GRADE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("시스템 그룹이라 삭제할 수 없어요. 사용하지 않는 코드는 사용 안 함으로 바꿔 주세요"));
    }

    @Test
    void 코드_삭제는_204이고_권한_없는_역할은_403() throws Exception {
        mvc.perform(delete("/api/code-groups/ZZ_USER/codes/A1")).andExpect(status().isNoContent());
        verify(service).deleteCode("C001", "ZZ_USER", "A1");
        mvc.perform(delete("/api/code-groups/ZZ_USER/codes/A2").header("X-Actor-Id", "emp1"))
                .andExpect(status().isForbidden());
        verify(service, never()).deleteCode("C001", "ZZ_USER", "A2");
    }

    @Test
    void 사원이_쓰는_직급_코드의_사용처는_인사담당에게_건수와_목록이_보인다() throws Exception {
        when(service.usages("C001", "JOB_GRADE", "STAFF", true)).thenReturn(new UsageSummary("JOB_GRADE", "STAFF", 15,
                List.of(new UsageTable("employee", "사원", 12), new UsageTable("hr_appointment", "발령 이력", 3)), true));
        when(service.usageRows("C001", "JOB_GRADE", "STAFF", "employee", 0)).thenReturn(new UsagePage("employee", "사원",
                "입사일", 12, 0, 20, List.of(new UsageRow("S01-2026-001", "가나다", LocalDate.of(2026, 3, 2)))));

        mvc.perform(get("/api/code-groups/JOB_GRADE/codes/STAFF/usages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(15))
                .andExpect(jsonPath("$.tables[0].label").value("사원"))
                .andExpect(jsonPath("$.tables[0].count").value(12))
                .andExpect(jsonPath("$.rowsVisible").value(true));
        mvc.perform(get("/api/code-groups/JOB_GRADE/codes/STAFF/usages").param("table", "employee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].empNo").value("S01-2026-001"))
                .andExpect(jsonPath("$.rows[0].name").value("가나다"));
    }

    @Test
    void 전체_사원_조회_권한이_없으면_사용처_건수만_보이고_목록은_403() throws Exception {
        when(service.usages("C001", "JOB_GRADE", "STAFF", false)).thenReturn(new UsageSummary("JOB_GRADE", "STAFF", 12,
                List.of(new UsageTable("employee", "사원", 12)), false));
        mvc.perform(get("/api/code-groups/JOB_GRADE/codes/STAFF/usages").header("X-Actor-Id", "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables[0].count").value(12))
                .andExpect(jsonPath("$.rowsVisible").value(false));
        mvc.perform(get("/api/code-groups/JOB_GRADE/codes/STAFF/usages").header("X-Actor-Id", "emp1")
                        .param("table", "employee"))
                .andExpect(status().isForbidden());
        verify(service, never()).usageRows(any(), any(), any(), any(), anyInt());
    }
}
