package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaborAgreementRepository extends JpaRepository<LaborAgreement, Long> {

    List<LaborAgreement> findAllByOrderByValidFromDescAgreementIdDesc();

    List<LaborAgreement> findByAgreementType(AgreementType type);

    /** 오늘 이후 [today, until] 안에 마지막 유효일이 오는 합의서(만료 예정). */
    @Query("SELECT a FROM LaborAgreement a WHERE a.validUntil BETWEEN :today AND :until "
            + "ORDER BY a.validUntil, a.agreementId")
    List<LaborAgreement> findExpiring(@Param("today") LocalDate today, @Param("until") LocalDate until);
}
