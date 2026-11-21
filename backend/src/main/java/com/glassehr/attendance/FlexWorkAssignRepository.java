package com.glassehr.attendance;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlexWorkAssignRepository extends JpaRepository<FlexWorkAssign, Long> {

    /** 같은 배정의 근무계획 전체 교체를 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FlexWorkAssign a WHERE a.flexId = :id")
    Optional<FlexWorkAssign> findForUpdate(@Param("id") long id);

    List<FlexWorkAssign> findByEmpIdOrderByPeriodStartAsc(long empId);

    List<FlexWorkAssign> findAllByOrderByPeriodStartDesc();
}
