package com.glassehr.attendance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpWorkPatternRepository extends JpaRepository<EmpWorkPattern, Long> {

    List<EmpWorkPattern> findByEmpIdOrderByValidFromAsc(long empId);

    boolean existsByPatternId(long patternId);
}
