package com.glassehr.appointment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 법인 조건(#124, docs/tenant-design.md 5.2): 모든 조회는 첫 인자 enterCd 로 그 법인 발령만 본다.
 * 다른 법인 발령 ID 는 빈 값이 되어 404 로 끝난다(존재 여부도 드러내지 않음).
 */
public interface HrAppointmentRepository extends JpaRepository<HrAppointment, Long> {

    List<HrAppointment> findByEnterCdAndEmpIdOrderByEffectiveDateAscApptIdAsc(String enterCd, long empId);

    List<HrAppointment> findByEnterCdOrderByEffectiveDateAscApptIdAsc(String enterCd);

    Optional<HrAppointment> findByEnterCdAndApptId(String enterCd, long apptId);

    /**
     * 발령의 사원 ID만 읽는다. 엔티티를 영속성 컨텍스트에 올리지 않아야 사원 잠금 뒤 {@link #findForUpdate}가
     * 잠금 대기 중 다른 트랜잭션이 커밋한 최신 상태(확정 여부 등)를 읽는다.
     */
    @Query("SELECT a.empId FROM HrAppointment a WHERE a.enterCd = :enterCd AND a.apptId = :apptId")
    Optional<Long> findEmpIdByApptId(@Param("enterCd") String enterCd, @Param("apptId") long apptId);

    /** 발령 행 잠금(SELECT ... FOR UPDATE). 사원 잠금을 잡은 뒤 확정 여부 검사 직전에 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM HrAppointment a WHERE a.enterCd = :enterCd AND a.apptId = :apptId")
    Optional<HrAppointment> findForUpdate(@Param("enterCd") String enterCd, @Param("apptId") long apptId);
}
