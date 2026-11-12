package com.glassehr.employee;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findAllByOrderByEmpNoAsc();

    List<Employee> findByRetireDateIsNullOrderByEmpNoAsc();

    /**
     * {@code date} 당일이나 그 뒤에 이 부서 소속인 사원 수(입사 예정자 포함, {@code date} 전에 퇴직한 사람 제외).
     * 퇴직일 당일까지는 재직으로 본다.
     */
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.currentDeptId = :deptId"
            + " AND (e.retireDate IS NULL OR e.retireDate >= :date)")
    long countInDeptOnOrAfter(@Param("deptId") long deptId, @Param("date") LocalDate date);
}
