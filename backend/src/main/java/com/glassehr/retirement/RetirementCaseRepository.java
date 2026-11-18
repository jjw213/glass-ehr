package com.glassehr.retirement;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetirementCaseRepository extends JpaRepository<RetirementCase, Long> {

    Optional<RetirementCase> findByEmpId(long empId);

    boolean existsByEmpId(long empId);
}
