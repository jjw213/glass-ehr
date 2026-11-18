package com.glassehr.retirement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentCertificateRepository extends JpaRepository<EmploymentCertificate, Long> {

    List<EmploymentCertificate> findByEmpIdOrderByIssuedAtDescCertIdDesc(long empId);
}
