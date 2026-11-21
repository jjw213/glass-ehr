package com.glassehr.attendance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlexWorkAssignRepository extends JpaRepository<FlexWorkAssign, Long> {

    List<FlexWorkAssign> findByEmpIdOrderByPeriodStartAsc(long empId);

    List<FlexWorkAssign> findAllByOrderByPeriodStartDesc();
}
