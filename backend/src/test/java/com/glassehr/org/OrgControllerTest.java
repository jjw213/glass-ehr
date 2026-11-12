package com.glassehr.org;

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

import com.glassehr.org.OrgDtos.DeptChangeRequest;
import com.glassehr.org.OrgDtos.DeptCloseRequest;
import com.glassehr.org.OrgDtos.DeptCreateRequest;
import com.glassehr.org.OrgDtos.DeptNode;
import com.glassehr.org.OrgDtos.DeptView;
import com.glassehr.org.OrgDtos.OrgTree;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrgController.class)
class OrgControllerTest {

    private static final LocalDate D = LocalDate.of(2026, 7, 1);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrgDeptService service;

    private static DeptView view(long id, String code, String name, DeptChangeType type) {
        return new DeptView(id, code, name, null, null, 0, D, null, type, D, null);
    }

    @Test
    void 기준일_트리를_돌려준다() throws Exception {
        DeptNode child = new DeptNode(2, "D100", "인사부", 1L, 7L, 10, D, null, List.of());
        DeptNode root = new DeptNode(1, "HQ", "본사", null, null, 0, D, null, List.of(child));
        when(service.tree(D)).thenReturn(new OrgTree(D, List.of(root)));

        mvc.perform(get("/api/org/tree").param("date", "2026-07-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseDate").value("2026-07-01"))
                .andExpect(jsonPath("$.roots[0].deptCode").value("HQ"))
                .andExpect(jsonPath("$.roots[0].children[0].name").value("인사부"))
                .andExpect(jsonPath("$.roots[0].children[0].headEmpId").value(7))
                .andExpect(jsonPath("$.roots[0].children[0].validFrom").value("2026-07-01"));
    }

    @Test
    void 기준일을_생략하면_오늘() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 12);
        when(service.today()).thenReturn(today);
        when(service.tree(today)).thenReturn(new OrgTree(today, List.of()));

        mvc.perform(get("/api/org/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseDate").value("2026-11-12"));
    }

    @Test
    void 기준일_형식이_틀리면_400() throws Exception {
        mvc.perform(get("/api/org/tree").param("date", "2026/07/01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        verify(service, never()).tree(any());
    }

    @Test
    void 신설은_201() throws Exception {
        DeptCreateRequest req = new DeptCreateRequest("D100", "인사부", 1L, null, 10, D, null);
        when(service.create(req)).thenReturn(view(2, "D100", "인사부", DeptChangeType.CREATE));

        mvc.perform(post("/api/org/depts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptCode\":\"D100\",\"name\":\"인사부\",\"parentDeptId\":1,\"sortOrder\":10,"
                                + "\"effectiveDate\":\"2026-07-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deptId").value(2))
                .andExpect(jsonPath("$.changeType").value("CREATE"));
    }

    @Test
    void 부서코드_형식이_틀리거나_신설일이_없으면_400이고_서비스를_부르지_않는다() throws Exception {
        mvc.perform(post("/api/org/depts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptCode\":\"d-100\",\"name\":\"인사부\",\"sortOrder\":10,\"effectiveDate\":\"2026-07-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("부서코드는 영문 대문자·숫자·밑줄만 써 주세요(첫 글자는 영문 대문자나 숫자)"));
        mvc.perform(post("/api/org/depts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptCode\":\"D100\",\"name\":\"인사부\",\"sortOrder\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("신설일을 입력해 주세요"));
        verify(service, never()).create(any());
    }

    @Test
    void 변경() throws Exception {
        DeptChangeRequest req = new DeptChangeRequest("인사팀", null, null, 10, D, "개편");
        when(service.change(2L, req)).thenReturn(view(2, "D100", "인사팀", DeptChangeType.RENAME));

        mvc.perform(put("/api/org/depts/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"인사팀\",\"sortOrder\":10,\"effectiveDate\":\"2026-07-01\",\"reason\":\"개편\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("인사팀"))
                .andExpect(jsonPath("$.changeType").value("RENAME"));
    }

    @Test
    void 순환_참조는_400과_메시지() throws Exception {
        when(service.change(eq(1L), any())).thenThrow(OrgException.badRequest("자기 하위 부서 아래로는 옮길 수 없어요. 순환 참조가 생겨요"));
        mvc.perform(put("/api/org/depts/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"본사\",\"parentDeptId\":3,\"sortOrder\":0,\"effectiveDate\":\"2026-07-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("자기 하위 부서 아래로는 옮길 수 없어요. 순환 참조가 생겨요"));
    }

    @Test
    void 재직자가_있는_부서_폐지는_409() throws Exception {
        when(service.close(eq(2L), any())).thenThrow(OrgException.conflict("폐지일 이후에도 소속 재직자가 3명 있어요. 인사발령으로 먼저 옮겨 주세요"));
        mvc.perform(post("/api/org/depts/2/close").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-09-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("폐지일 이후에도 소속 재직자가 3명 있어요. 인사발령으로 먼저 옮겨 주세요"));
    }

    @Test
    void 폐지() throws Exception {
        LocalDate close = LocalDate.of(2026, 9, 1);
        when(service.close(2L, new DeptCloseRequest(close, "통폐합")))
                .thenReturn(new DeptView(2, "D100", "인사부", null, null, 0, D, close, DeptChangeType.CREATE, D, close));
        mvc.perform(post("/api/org/depts/2/close").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-09-01\",\"reason\":\"통폐합\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deptValidTo").value("2026-09-01"));
    }

    @Test
    void 없는_부서는_404() throws Exception {
        when(service.close(anyLong(), any())).thenThrow(OrgException.deptNotFound(99));
        mvc.perform(post("/api/org/depts/99/close").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-09-01\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("부서가 없어요: 99"));
    }
}
