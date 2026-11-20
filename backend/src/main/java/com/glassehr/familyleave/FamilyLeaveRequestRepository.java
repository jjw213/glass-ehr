package com.glassehr.familyleave;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyLeaveRequestRepository extends JpaRepository<FamilyLeaveRequest, Long> {

    List<FamilyLeaveRequest> findByEmpIdOrderByStartDateAscReqIdAsc(long empId);

    List<FamilyLeaveRequest> findByStatusOrderByRequestedAtAscReqIdAsc(FamilyLeaveRequest.Status status);

    List<FamilyLeaveRequest> findAllByOrderByRequestedAtAscReqIdAsc();

    /** 잠그기 전에 엔티티를 캐시하지 않도록 사원 ID만 스칼라로 읽는다. */
    @Query("SELECT r.empId FROM FamilyLeaveRequest r WHERE r.reqId = :reqId")
    Optional<Long> findEmpIdByReqId(@Param("reqId") long reqId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FamilyLeaveRequest r WHERE r.reqId = :reqId")
    Optional<FamilyLeaveRequest> findForUpdate(@Param("reqId") long reqId);
}
