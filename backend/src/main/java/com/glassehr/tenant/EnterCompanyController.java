package com.glassehr.tenant;

import com.glassehr.auth.PublicApi;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 화면 회사 선택용 법인 목록(#118, docs/tenant-design.md 4.1).
 * 로그인 전에 불러야 하므로 {@link PublicApi}다. 사용 중인(use_yn='Y') 법인의 코드·이름만 돌려주고
 * 사업자등록번호 등 다른 열은 내보내지 않는다.
 * <ul>
 *   <li>GET /api/enter-companies — 200 [{enterCd, enterName}] (정렬 순서·코드 순)</li>
 * </ul>
 */
@RestController
public class EnterCompanyController {

    private final EnterCompanyRepository repository;

    public EnterCompanyController(EnterCompanyRepository repository) {
        this.repository = repository;
    }

    public record EnterCompanyView(String enterCd, String enterName) {
    }

    @GetMapping("/api/enter-companies")
    @PublicApi
    public List<EnterCompanyView> list() {
        return repository.findByActiveTrueOrderBySortOrderAscEnterCdAsc().stream()
                .map(c -> new EnterCompanyView(c.getEnterCd(), c.getEnterName()))
                .toList();
    }
}
