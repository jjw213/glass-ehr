package com.glassehr.contract;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Long> {

    List<EmploymentContract> findByEmpIdOrderByStartDateAsc(long empId);

    /** 서면 교부일이 없는 계약(근로기준법 제17조 제2항, 기간제법 제17조). */
    List<EmploymentContract> findByWrittenDeliveredDateIsNullOrderByContractDateAscContractIdAsc();

    List<EmploymentContract> findAllByOrderByEmpIdAscStartDateAsc();

    /**
     * 계약의 사원 ID만 읽는다. 엔티티를 영속성 컨텍스트에 올리지 않아야 사원 잠금 뒤 {@link #findForUpdate}가
     * 잠금 대기 중 다른 트랜잭션이 커밋한 최신 상태(교부일 등)를 읽는다.
     */
    @Query("SELECT c.empId FROM EmploymentContract c WHERE c.contractId = :contractId")
    Optional<Long> findEmpIdByContractId(@Param("contractId") long contractId);

    /** 계약 행 잠금(SELECT ... FOR UPDATE). 사원 잠금을 잡은 뒤 상태 검사 직전에 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM EmploymentContract c WHERE c.contractId = :contractId")
    Optional<EmploymentContract> findForUpdate(@Param("contractId") long contractId);
}
