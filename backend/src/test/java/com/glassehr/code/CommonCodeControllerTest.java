package com.glassehr.code;

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

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeGroupView;
import com.glassehr.code.CodeDtos.CodeUpdateRequest;
import com.glassehr.code.CodeDtos.CodeView;
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
        return new CodeView(group, code, name, null, sort, active);
    }

    @Test
    void 여러_그룹_코드를_요청_순서대로_돌려준다() throws Exception {
        Map<String, List<CodeView>> result = new LinkedHashMap<>();
        result.put("EMPLOYMENT_TYPE", List.of(view("EMPLOYMENT_TYPE", "REGULAR", "정규직", 10, true)));
        result.put("JOB_GRADE", List.of());
        when(service.activeCodes(List.of("EMPLOYMENT_TYPE", "JOB_GRADE"))).thenReturn(result);

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
        when(service.listCodes("NOPE", false)).thenThrow(CodeException.groupNotFound("NOPE"));
        mvc.perform(get("/api/code-groups/NOPE/codes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("코드그룹이 없어요: NOPE"));
    }

    @Test
    void 그룹_목록() throws Exception {
        when(service.listGroups()).thenReturn(List.of(new CodeGroupView("JOB_GRADE", "직급", null, 10, true)));
        mvc.perform(get("/api/code-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupCode").value("JOB_GRADE"))
                .andExpect(jsonPath("$[0].name").value("직급"));
    }

    @Test
    void 관리화면은_사용중지_코드도_요청할_수_있다() throws Exception {
        when(service.listCodes("JOB_GRADE", true)).thenReturn(List.of(view("JOB_GRADE", "OLD", "옛 직급", 1, false)));
        mvc.perform(get("/api/code-groups/JOB_GRADE/codes").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void 코드_추가는_201() throws Exception {
        CodeCreateRequest req = new CodeCreateRequest("INTERN", "인턴", null, 40, true);
        when(service.create("EMPLOYMENT_TYPE", req)).thenReturn(view("EMPLOYMENT_TYPE", "INTERN", "인턴", 40, true));
        mvc.perform(post("/api/code-groups/EMPLOYMENT_TYPE/codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"INTERN\",\"name\":\"인턴\",\"sortOrder\":40,\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("INTERN"));
    }

    @Test
    void 코드값_형식이_틀리면_400이고_서비스를_부르지_않는다() throws Exception {
        mvc.perform(post("/api/code-groups/JOB_GRADE/codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"staff-1\",\"name\":\"사원\",\"sortOrder\":1,\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("코드값은 영문 대문자로 시작하고 영문 대문자·숫자·밑줄만 30자 이내로 써 주세요"));
        verify(service, never()).create(any(), any());
    }

    @Test
    void 정렬순서_음수와_빈_코드명은_400() throws Exception {
        mvc.perform(put("/api/code-groups/JOB_GRADE/codes/STAFF").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"sortOrder\":-1,\"active\":true}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).update(any(), any(), any());
    }

    @Test
    void 중복_코드값은_409() throws Exception {
        when(service.create(eq("JOB_GRADE"), any())).thenThrow(CodeException.duplicate("JOB_GRADE", "STAFF"));
        mvc.perform(post("/api/code-groups/JOB_GRADE/codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"STAFF\",\"name\":\"사원\",\"sortOrder\":1,\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("이미 있는 코드값이에요: JOB_GRADE/STAFF"));
    }

    @Test
    void 코드_수정() throws Exception {
        CodeUpdateRequest req = new CodeUpdateRequest("사원(구)", null, 15, false);
        when(service.update("JOB_GRADE", "STAFF", req)).thenReturn(view("JOB_GRADE", "STAFF", "사원(구)", 15, false));
        mvc.perform(put("/api/code-groups/JOB_GRADE/codes/STAFF").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"사원(구)\",\"sortOrder\":15,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.sortOrder").value(15));
    }
}
