package com.glassehr.attendance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 근무 정책(#159). 모든 조회에 법인 조건을 건다(다른 법인 정책은 보이지 않는다). */
public interface WorkPolicyRepository extends JpaRepository<WorkPolicy, Long> {

    List<WorkPolicy> findByEnterCdOrderByPolicyIdAsc(String enterCd);

    /** use_yn 은 YnConverter 를 거치도록 값으로 받는다. */
    List<WorkPolicy> findByEnterCdAndActive(String enterCd, Boolean active);

    Optional<WorkPolicy> findByEnterCdAndPolicyId(String enterCd, Long policyId);

    /** 정책이 쓰였는지: 기본 정책(patternId null)은 그 법인 집계 행, 재정의는 그 근무유형 집계 행이 있으면 쓰인 것. */
    @Query(value = "SELECT COUNT(*) FROM attendance_daily_summary WHERE enter_cd = :enterCd"
            + " AND (:patternId IS NULL OR pattern_id = :patternId)", nativeQuery = true)
    long countSummaries(@Param("enterCd") String enterCd, @Param("patternId") Long patternId);
}
