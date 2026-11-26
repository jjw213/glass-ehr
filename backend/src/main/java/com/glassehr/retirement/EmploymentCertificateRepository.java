package com.glassehr.retirement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 법인 조건(#124): 첫 인자 enterCd 로 그 법인 사용증명서만 본다. */
public interface EmploymentCertificateRepository extends JpaRepository<EmploymentCertificate, Long> {

    List<EmploymentCertificate> findByEnterCdAndEmpIdOrderByIssuedAtDescCertIdDesc(String enterCd, long empId);

    Optional<EmploymentCertificate> findByEnterCdAndCertId(String enterCd, long certId);
}
