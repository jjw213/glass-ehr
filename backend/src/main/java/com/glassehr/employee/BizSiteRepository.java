package com.glassehr.employee;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사업장. 법인별 테이블이므로 상속 메서드(findById·existsById 등)는 쓰지 않고
 * 첫 인자로 enterCd 를 받는 메서드만 쓴다(docs/tenant-design.md 5.2).
 */
public interface BizSiteRepository extends JpaRepository<BizSite, BizSite.Key> {

    Optional<BizSite> findByEnterCdAndSiteCode(String enterCd, String siteCode);

    boolean existsByEnterCdAndSiteCode(String enterCd, String siteCode);

    /**
     * 사업장 행 잠금(SELECT ... FOR UPDATE). 채번 전에 잡아, 그해 첫 입사자를 동시에 등록할 때
     * emp_no_seq 행이 아직 없어 둘 다 001을 만드는 일을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BizSite b WHERE b.enterCd = :enterCd AND b.siteCode = :siteCode")
    Optional<BizSite> findForUpdate(@Param("enterCd") String enterCd, @Param("siteCode") String siteCode);
}
