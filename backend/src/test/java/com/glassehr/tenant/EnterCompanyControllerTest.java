package com.glassehr.tenant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.TestAuthUsers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnterCompanyController.class)
@Import(TestAuthUsers.class) // 로그인 전에 불러야 하므로 사용자 헤더 없이 부른다
class EnterCompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnterCompanyRepository repository;

    private static EnterCompany company(String cd, String name) {
        EnterCompany c = mock(EnterCompany.class);
        when(c.getEnterCd()).thenReturn(cd);
        when(c.getEnterName()).thenReturn(name);
        return c;
    }

    @Test
    void 로그인_없이_사용중인_법인_코드와_이름만_준다() throws Exception {
        List<EnterCompany> companies = List.of(company("C001", "기본 법인(예시)"), company("C002", "둘째 법인(예시)"));
        when(repository.findByActiveTrueOrderBySortOrderAscEnterCdAsc()).thenReturn(companies);

        mockMvc.perform(get("/api/enter-companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].enterCd").value("C001"))
                .andExpect(jsonPath("$[0].enterName").value("기본 법인(예시)"))
                .andExpect(jsonPath("$[1].enterCd").value("C002"))
                .andExpect(jsonPath("$[0].bizRegNo").doesNotExist())
                .andExpect(jsonPath("$[0].active").doesNotExist())
                .andExpect(jsonPath("$[0].sortOrder").doesNotExist());
    }
}
