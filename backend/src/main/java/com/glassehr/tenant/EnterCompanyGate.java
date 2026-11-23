package com.glassehr.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 이 법인으로 로그인·요청할 수 있는가(docs/tenant-design.md 4.1·8.2). 로그인과 요청마다의 사용자 확인이 같은 규칙을 쓴다.
 * <ul>
 *   <li>형식이 맞고, enter_company 에 있고 사용 중(use_yn='Y')이어야 한다.</li>
 *   <li>다법인 배포 잠금: {@code glassehr.tenant.multi-company-enabled} 가 false(기본)면 C001 만 허용한다.
 *       법인별 테이블의 법인 격리(후속 이슈 3~6)가 모두 배포되기 전에는 다른 법인으로 업무 기능에 들어갈 수 없게 서버에서 막는다.</li>
 * </ul>
 */
@Component
public class EnterCompanyGate {

    private final EnterCompanyRepository companies;
    private final boolean multiCompanyEnabled;

    public EnterCompanyGate(EnterCompanyRepository companies,
            @Value("${glassehr.tenant.multi-company-enabled:false}") boolean multiCompanyEnabled) {
        this.companies = companies;
        this.multiCompanyEnabled = multiCompanyEnabled;
    }

    public boolean allows(String enterCd) {
        if (!EnterCompany.isValidCode(enterCd)) {
            return false;
        }
        if (!multiCompanyEnabled && !EnterCompany.DEFAULT_ENTER_CD.equals(enterCd)) {
            return false;
        }
        return companies.existsByEnterCdAndActiveTrue(enterCd);
    }
}
