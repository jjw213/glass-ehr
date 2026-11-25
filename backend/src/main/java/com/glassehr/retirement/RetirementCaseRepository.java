package com.glassehr.retirement;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 법인 조건(#124): 첫 인자 enterCd 로 그 법인 퇴직 처리만 본다. */
public interface RetirementCaseRepository extends JpaRepository<RetirementCase, Long> {

    Optional<RetirementCase> findByEnterCdAndEmpId(String enterCd, long empId);

    boolean existsByEnterCdAndEmpId(String enterCd, long empId);
}
