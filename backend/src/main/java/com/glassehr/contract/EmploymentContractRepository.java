package com.glassehr.contract;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Long> {

    List<EmploymentContract> findByEmpIdOrderByStartDateAsc(long empId);

    /** 서면 교부일이 없는 계약(근로기준법 제17조 제2항, 기간제법 제17조). */
    List<EmploymentContract> findByWrittenDeliveredDateIsNullOrderByContractDateAscContractIdAsc();

    List<EmploymentContract> findAllByOrderByEmpIdAscStartDateAsc();
}
