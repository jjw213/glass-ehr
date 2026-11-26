package com.glassehr.attendance;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 유연근무 배정. 모든 조회에 법인 조건을 건다(#126). 다른 법인 배정은 빈 값(→ 404). */
public interface FlexWorkAssignRepository extends JpaRepository<FlexWorkAssign, Long> {

    /** 같은 배정의 근무계획 전체 교체를 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FlexWorkAssign a WHERE a.enterCd = :enterCd AND a.flexId = :id")
    Optional<FlexWorkAssign> findForUpdate(@Param("enterCd") String enterCd, @Param("id") long id);

    Optional<FlexWorkAssign> findByEnterCdAndFlexId(String enterCd, Long flexId);

    List<FlexWorkAssign> findByEnterCdAndEmpIdOrderByPeriodStartAsc(String enterCd, long empId);

    List<FlexWorkAssign> findByEnterCdOrderByPeriodStartDesc(String enterCd);
}
