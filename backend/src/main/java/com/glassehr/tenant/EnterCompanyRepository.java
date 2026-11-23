package com.glassehr.tenant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 법인 마스터 조회. enter_company 는 전체 공통 테이블이라 법인 조건을 걸지 않는다(tenant-design.md 3.1). */
public interface EnterCompanyRepository extends JpaRepository<EnterCompany, String> {

    /** 사용 중인 법인만, 정렬 순서·법인 코드 순. */
    List<EnterCompany> findByActiveTrueOrderBySortOrderAscEnterCdAsc();

    /** 있고 사용 중인 법인인가. */
    boolean existsByEnterCdAndActiveTrue(String enterCd);
}
