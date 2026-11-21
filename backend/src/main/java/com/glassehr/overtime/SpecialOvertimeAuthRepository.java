package com.glassehr.overtime;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialOvertimeAuthRepository extends JpaRepository<SpecialOvertimeAuth, Long> {

    boolean existsByAuthNo(String authNo);

    List<SpecialOvertimeAuth> findAllByOrderByPeriodFromDesc();
}
