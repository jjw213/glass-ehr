package com.glassehr.employee;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findAllByOrderByEmpNoAsc();

    List<Employee> findByRetireDateIsNullOrderByEmpNoAsc();

    /**
     * 사원 행 잠금(SELECT ... FOR UPDATE). 수정·퇴직 처리는 이것으로 읽어 같은 사원의 쓰기를 직렬화한다.
     * 잠그지 않으면 퇴직 처리 전에 읽은 수정 요청이 나중에 전체 행을 덮어써 퇴직일·사유·보존기한을 지울 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.empId = :empId")
    Optional<Employee> findForUpdate(@Param("empId") long empId);

    /**
     * {@code date} 당일이나 그 뒤에 이 부서 소속인 사원 수(입사 예정자 포함, {@code date} 전에 퇴직한 사람 제외).
     * 퇴직일 당일까지는 재직으로 본다.
     */
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.currentDeptId = :deptId"
            + " AND (e.retireDate IS NULL OR e.retireDate >= :date)")
    long countInDeptOnOrAfter(@Param("deptId") long deptId, @Param("date") LocalDate date);
}
