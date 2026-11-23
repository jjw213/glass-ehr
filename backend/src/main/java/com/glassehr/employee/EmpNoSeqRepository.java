package com.glassehr.employee;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 사번 채번 상태. 법인별 테이블이므로 상속 조회 메서드는 쓰지 않는다(docs/tenant-design.md 5.2). */
public interface EmpNoSeqRepository extends JpaRepository<EmpNoSeq, EmpNoSeq.Key> {

    /** SELECT ... FOR UPDATE. 같은 법인·사업장·연도로 동시에 등록하면 뒤 트랜잭션은 앞 트랜잭션이 끝날 때까지 기다린다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EmpNoSeq s WHERE s.enterCd = :enterCd AND s.siteCode = :siteCode AND s.hireYear = :hireYear")
    Optional<EmpNoSeq> findForUpdate(@Param("enterCd") String enterCd, @Param("siteCode") String siteCode,
            @Param("hireYear") int hireYear);
}
