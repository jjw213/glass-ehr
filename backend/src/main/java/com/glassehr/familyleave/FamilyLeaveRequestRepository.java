package com.glassehr.familyleave;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 육아휴직·단축·가족돌봄휴직 신청은 법인 안에서만 찾는다(#127). 다른 법인 신청 ID 는 empty. */
public interface FamilyLeaveRequestRepository extends JpaRepository<FamilyLeaveRequest, Long> {

    Optional<FamilyLeaveRequest> findByEnterCdAndReqId(String enterCd, long reqId);

    List<FamilyLeaveRequest> findByEnterCdAndEmpIdOrderByStartDateAscReqIdAsc(String enterCd, long empId);

    List<FamilyLeaveRequest> findByEnterCdAndStatusOrderByRequestedAtAscReqIdAsc(String enterCd,
            FamilyLeaveRequest.Status status);

    List<FamilyLeaveRequest> findByEnterCdOrderByRequestedAtAscReqIdAsc(String enterCd);

    /** 잠그기 전에 엔티티를 캐시하지 않도록 사원 ID만 스칼라로 읽는다. */
    @Query("SELECT r.empId FROM FamilyLeaveRequest r WHERE r.enterCd = :enterCd AND r.reqId = :reqId")
    Optional<Long> findEmpIdByReqId(@Param("enterCd") String enterCd, @Param("reqId") long reqId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM FamilyLeaveRequest r WHERE r.enterCd = :enterCd AND r.reqId = :reqId")
    Optional<FamilyLeaveRequest> findForUpdate(@Param("enterCd") String enterCd, @Param("reqId") long reqId);
}
