package com.glassehr.employee;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * 인사발령 반영(#59): 현재 소속·직급·직책·재직상태를 바꾸는 유일한 경로. com.glassehr.appointment.AppointmentService 만
     * 사원 행을 잠그고 값이 다를 때만 부른다. 끝나면 영속성 컨텍스트를 비워 이후 읽기가 새 값을 보게 한다.
     *
     * @return 바뀐 행 수(0 또는 1)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Employee e SET e.currentDeptId = :deptId, e.jobGradeCode = :grade, e.jobTitleCode = :title, "
            + "e.empStatus = :status, e.updatedAt = :now WHERE e.enterCd = :enterCd AND e.empId = :empId")
    int applyAppointment(@Param("enterCd") String enterCd, @Param("empId") long empId, @Param("deptId") Long deptId,
            @Param("grade") String grade, @Param("title") String title, @Param("status") String status,
            @Param("now") LocalDateTime now);

    /** 사원 법인·ID(발령 매일 반영용). 엔티티를 올리지 않는다. */
    interface EmpKey {
        String getEnterCd();

        Long getEmpId();
    }

    /** 모든 법인의 사원 키(발령 매일 반영을 법인별로 돌리려고, docs/tenant-design.md 8장). */
    List<EmpKey> findAllByOrderByEnterCdAscEmpIdAsc();

    /** 그 법인 사원 중 ids 에 든 것. 다른 법인 ID 는 빠진다(#124). */
    List<Employee> findByEnterCdAndEmpIdIn(String enterCd, java.util.Collection<Long> empIds);

    /** 그 법인에서 보존기한(근로기준법 제42조)이 {@code date}보다 앞선 퇴직자. 보존기한 당일까지는 보존 대상이다(#18, #124). */
    List<Employee> findByEnterCdAndRecordRetentionUntilBeforeOrderByRecordRetentionUntilAscEmpNoAsc(String enterCd,
            LocalDate date);

    // ---- 법인 조건(#123·#129, docs/tenant-design.md 5.2). 법인 조건 없는 조회는 위 발령 매일 반영용 키 목록뿐이다.

    Optional<Employee> findByEnterCdAndEmpId(String enterCd, Long empId);

    List<Employee> findByEnterCdOrderByEmpNoAsc(String enterCd);

    List<Employee> findByEnterCdAndRetireDateIsNullOrderByEmpNoAsc(String enterCd);

    /**
     * 사원 행 잠금(SELECT ... FOR UPDATE). 수정·퇴직 처리는 이것으로 읽어 같은 사원의 쓰기를 직렬화한다.
     * 잠그지 않으면 퇴직 처리 전에 읽은 수정 요청이 나중에 전체 행을 덮어써 퇴직일·사유·보존기한을 지울 수 있다.
     * 그 법인 사원만 잠근다. 다른 법인 사원은 빈 값(→ 404).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.enterCd = :enterCd AND e.empId = :empId")
    Optional<Employee> findForUpdate(@Param("enterCd") String enterCd, @Param("empId") long empId);

    /** 그 법인에서 현재 그 직책을 가진 재직자(결재자 찾기, #30·#127). */
    @Query("SELECT e.empId FROM Employee e WHERE e.enterCd = :enterCd AND e.jobTitleCode = :code"
            + " AND e.empStatus = 'ACTIVE'")
    List<Long> findActiveEmpIdsWithTitle(@Param("enterCd") String enterCd, @Param("code") String titleCode);
}
