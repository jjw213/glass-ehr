package com.glassehr.attendance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사원 근무유형 배정. 법인 조건을 건다(#125). */
public interface EmpWorkPatternRepository extends JpaRepository<EmpWorkPattern, Long> {

    List<EmpWorkPattern> findByEnterCdAndEmpIdOrderByValidFromAsc(String enterCd, Long empId);

    boolean existsByEnterCdAndPatternId(String enterCd, Long patternId);
}
