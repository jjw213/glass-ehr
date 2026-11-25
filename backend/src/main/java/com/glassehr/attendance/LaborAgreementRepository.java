package com.glassehr.attendance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 근로자대표 서면합의는 법인마다 따로 한다. 모든 조회는 법인 조건을 건다(#125, docs/tenant-design.md 5.2). */
public interface LaborAgreementRepository extends JpaRepository<LaborAgreement, Long> {

    List<LaborAgreement> findByEnterCdOrderByEffectiveFromDescAgreementIdDesc(String enterCd);

    Optional<LaborAgreement> findByEnterCdAndAgreementId(String enterCd, Long agreementId);

    List<LaborAgreement> findByEnterCdAndAgreementIdIn(String enterCd, Collection<Long> ids);

    /** 수정과 휴일 대체 등록이 같은 합의서를 동시에 검증하지 않도록 직렬화한다. 다른 법인 합의서는 빈 값. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LaborAgreement a WHERE a.enterCd = :enterCd AND a.agreementId = :id")
    Optional<LaborAgreement> findForUpdate(@Param("enterCd") String enterCd, @Param("id") long id);

    List<LaborAgreement> findByEnterCdAndAgreementType(String enterCd, AgreementType type);

    /** 마지막 유효일(포함)이 [today, until] 안인 그 법인 합의서(만료 예정). */
    default List<LaborAgreement> findExpiring(String enterCd, LocalDate today, LocalDate until) {
        return findExpiringRaw(enterCd, today.plusDays(1), until.plusDays(1));
    }

    /** 저장 값은 배타 종료일(마지막 유효일 + 1)이라 effective_to 로 [toFrom, toUntil] 을 찾는다. {@link #findExpiring}을 쓸 것. */
    @Query("SELECT a FROM LaborAgreement a WHERE a.enterCd = :enterCd AND a.effectiveTo BETWEEN :toFrom AND :toUntil "
            + "ORDER BY a.effectiveTo, a.agreementId")
    List<LaborAgreement> findExpiringRaw(@Param("enterCd") String enterCd, @Param("toFrom") LocalDate toFrom,
            @Param("toUntil") LocalDate toUntil);
}
