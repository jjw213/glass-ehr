package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaborAgreementRepository extends JpaRepository<LaborAgreement, Long> {

    List<LaborAgreement> findAllByOrderByEffectiveFromDescAgreementIdDesc();

    /** 수정과 휴일 대체 등록이 같은 합의서를 동시에 검증하지 않도록 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LaborAgreement a WHERE a.agreementId = :id")
    Optional<LaborAgreement> findForUpdate(@Param("id") long id);

    List<LaborAgreement> findByAgreementType(AgreementType type);

    /** 마지막 유효일(포함)이 [today, until] 안인 합의서(만료 예정). */
    default List<LaborAgreement> findExpiring(LocalDate today, LocalDate until) {
        return findExpiringRaw(today.plusDays(1), until.plusDays(1));
    }

    /** 저장 값은 배타 종료일(마지막 유효일 + 1)이라 effective_to 로 [toFrom, toUntil] 을 찾는다. {@link #findExpiring}을 쓸 것. */
    @Query("SELECT a FROM LaborAgreement a WHERE a.effectiveTo BETWEEN :toFrom AND :toUntil "
            + "ORDER BY a.effectiveTo, a.agreementId")
    List<LaborAgreement> findExpiringRaw(@Param("toFrom") LocalDate toFrom, @Param("toUntil") LocalDate toUntil);
}
