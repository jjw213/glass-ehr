package com.glassehr.employee;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmpNoSeqRepository extends JpaRepository<EmpNoSeq, EmpNoSeq.Key> {

    /** SELECT ... FOR UPDATE. 같은 사업장·연도로 동시에 등록하면 뒤 트랜잭션은 앞 트랜잭션이 끝날 때까지 기다린다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EmpNoSeq s WHERE s.siteCode = :siteCode AND s.hireYear = :hireYear")
    Optional<EmpNoSeq> findForUpdate(@Param("siteCode") String siteCode, @Param("hireYear") int hireYear);
}
