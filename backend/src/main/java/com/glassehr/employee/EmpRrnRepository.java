package com.glassehr.employee;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpRrnRepository extends JpaRepository<EmpRrn, Long> {

    boolean existsByRrnHash(byte[] rrnHash);

    boolean existsByRrnHashAndEmpIdNot(byte[] rrnHash, Long empId);
}
