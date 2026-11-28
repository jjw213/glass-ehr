package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.auth.AuthUser;
import com.glassehr.tenant.EnterCompanyAdminService.ActiveRequest;
import com.glassehr.tenant.EnterCompanyAdminService.AdminException;
import com.glassehr.tenant.EnterCompanyAdminService.CompanyView;
import com.glassehr.tenant.EnterCompanyAdminService.CreateRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** 입력 검증·고객사 범위(DB 없이). 실제 복사는 EnterCompanyAdminOracleTest. */
class EnterCompanyAdminServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EnterCompanyAdminService service = new EnterCompanyAdminService(jdbc);
    private final AuthUser group = new AuthUser("T001", "C001", "C001", "group1", null, Set.of("GROUP_ADMIN"), Set.of(),
            Set.of(), "127.0.0.1");

    private static CreateRequest req(String cd, String name, String biz, String src, String id, String pw) {
        return new CreateRequest(cd, name, biz, src, id, pw);
    }

    private HttpStatus statusOf(CreateRequest r) {
        try {
            service.create(group, r);
        } catch (AdminException e) {
            return e.status();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void sourceInTenant(String cd) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(cd), eq("T001")))
                .thenReturn(List.of(new CompanyView(cd, "원본(예시)", "0000000000", true, 0)));
    }

    @Test
    void 형식이_틀린_입력은_400이고_아무것도_쓰지_않는다() {
        String pw = "example-pass-1";
        assertThat(statusOf(req("c2", "법인", "0000000097", "C001", "a", pw))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(req("C", "법인", "0000000097", "C001", "a", pw))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(req("C002", " ", "0000000097", "C001", "a", pw))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(req("C002", "법인", "123", "C001", "a", pw))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(req("C002", "법인", "0000000097", "C001", " ", pw))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(req("C002", "법인", "0000000097", "C001", "a", "short"))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statusOf(null)).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void 원본이_세션_고객사_법인이_아니면_404() {
        // 다른 고객사 법인(T002 소속)은 tenant_id = T001 조건으로 찾히지 않는다.
        assertThat(statusOf(req("C002", "법인", "000-00-00097", "OTHER1", "a", "example-pass-1")))
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void 법인_코드나_사업자등록번호가_겹치면_409() {
        sourceInTenant("C001");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("C002"), eq("0000000097"))).thenReturn(1);
        assertThat(statusOf(req("C002", "법인", "000-00-00097", "C001", "a", "example-pass-1")))
                .isEqualTo(HttpStatus.CONFLICT);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void 로그인한_법인과_보고_있는_법인은_사용_중지할_수_없다() {
        sourceInTenant("C001");
        assertThatThrownBy(() -> service.setActive(group, "C001", new ActiveRequest(false)))
                .isInstanceOf(AdminException.class)
                .satisfies(e -> assertThat(((AdminException) e).status()).isEqualTo(HttpStatus.CONFLICT));
        AuthUser switched = new AuthUser("T001", "C003", "C001", "group1", null, Set.of("GROUP_ADMIN"), Set.of(), Set.of(),
                "127.0.0.1");
        sourceInTenant("C003");
        assertThatThrownBy(() -> service.setActive(switched, "C003", new ActiveRequest(false)))
                .isInstanceOf(AdminException.class);
        assertThatThrownBy(() -> service.setActive(group, "ZZ9", new ActiveRequest(false)))
                .isInstanceOf(AdminException.class)
                .satisfies(e -> assertThat(((AdminException) e).status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
