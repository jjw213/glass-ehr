package com.glassehr.tenant;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 이 법인으로 로그인·요청할 수 있는가(docs/tenant-design.md 4.1·8.2). 로그인과 요청마다의 사용자 확인이 같은 규칙을 쓴다.
 * <ul>
 *   <li>형식이 맞고, enter_company 에 있고 사용 중(use_yn='Y')이어야 한다.</li>
 *   <li>다법인 배포 잠금: {@code glassehr.tenant.multi-company-enabled} 가 false(기본)면 C001 만 허용한다.
 *       법인별 테이블의 법인 격리(후속 이슈 3~6)가 모두 배포되기 전에는 다른 법인으로 업무 기능에 들어갈 수 없게 서버에서 막는다.</li>
 *   <li>법인이 속한 고객사가 지금 이용 가능해야 한다(#151, docs/saas-design.md 2.2). 고객사 상태는 따로 확인해
 *       정지 안내 문구를 보여 줄 수 있게 한다({@link #tenantUsable}).</li>
 * </ul>
 */
@Component
public class EnterCompanyGate {

    private final EnterCompanyRepository companies;
    private final TenantRepository tenants;
    private final boolean multiCompanyEnabled;
    private final Clock clock;

    public EnterCompanyGate(EnterCompanyRepository companies, TenantRepository tenants,
            @Value("${glassehr.tenant.multi-company-enabled:false}") boolean multiCompanyEnabled) {
        this.companies = companies;
        this.tenants = tenants;
        this.multiCompanyEnabled = multiCompanyEnabled;
        this.clock = Clock.system(ZoneId.of("Asia/Seoul"));
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

    /** 법인이 속한 고객사. 없는 법인이면 null. 고객사는 이 값(로그인 법인 → 고객사)으로만 정한다(saas-design.md 2.3). */
    public String tenantOf(String enterCd) {
        if (!EnterCompany.isValidCode(enterCd)) {
            return null;
        }
        return companies.findById(enterCd).map(EnterCompany::getTenantId).orElse(null);
    }

    /**
     * 고객사가 지금(Asia/Seoul) 로그인·요청을 받을 수 있는가. SUSPENDED·TERMINATED·체험 종료가 지난 TRIAL 이면 false
     * (saas-design.md 2.2). 없는 고객사도 false.
     */
    public boolean tenantUsable(String tenantId) {
        if (tenantId == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return tenants.findById(tenantId).map(t -> t.usableAt(now)).orElse(false);
    }
}
