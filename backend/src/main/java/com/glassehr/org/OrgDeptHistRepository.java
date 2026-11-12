package com.glassehr.org;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgDeptHistRepository extends JpaRepository<OrgDeptHist, Long> {

    /** 기준일 d 에 유효한 행: valid_from <= d AND (valid_to IS NULL OR d < valid_to). */
    @Query("select h from OrgDeptHist h where h.validFrom <= :d and (h.validTo is null or h.validTo > :d)")
    List<OrgDeptHist> findEffectiveOn(@Param("d") LocalDate d);

    /** d 당일 또는 그 뒤에 유효한 기간이 있는 행(현재·미래 행). 순환·하위부서 검사용. */
    @Query("select h from OrgDeptHist h where h.validTo is null or h.validTo > :d")
    List<OrgDeptHist> findEffectiveOnOrAfter(@Param("d") LocalDate d);

    /** 부서의 마지막(가장 늦게 시작하는) 행. */
    Optional<OrgDeptHist> findFirstByDeptIdOrderByValidFromDesc(Long deptId);
}
