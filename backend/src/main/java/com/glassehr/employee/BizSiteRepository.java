package com.glassehr.employee;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BizSiteRepository extends JpaRepository<BizSite, String> {

    /**
     * 사업장 행 잠금(SELECT ... FOR UPDATE). 채번 전에 잡아, 그해 첫 입사자를 동시에 등록할 때
     * emp_no_seq 행이 아직 없어 둘 다 001을 만드는 일을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BizSite b WHERE b.siteCode = :siteCode")
    Optional<BizSite> findForUpdate(@Param("siteCode") String siteCode);
}
