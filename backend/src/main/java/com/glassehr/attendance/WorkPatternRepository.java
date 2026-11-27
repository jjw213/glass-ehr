package com.glassehr.attendance;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 근무유형은 법인마다 따로 둔다. 법인 조건이 있는 메서드만 쓴다(#125·#129, docs/tenant-design.md 5.2).
 * 상속한 findById 등은 쓰지 않는다(TenantQueryScanTest 가 검사).
 */
public interface WorkPatternRepository extends JpaRepository<WorkPattern, Long> {

    List<WorkPattern> findByEnterCdOrderByPatternCodeAsc(String enterCd);

    boolean existsByEnterCdAndPatternCode(String enterCd, String patternCode);

    /** 요청으로 받은 근무유형 ID 는 이것으로 찾는다. 다른 법인 근무유형은 빈 값. */
    Optional<WorkPattern> findByEnterCdAndPatternId(String enterCd, Long patternId);

    /**
     * 근무유형 행 잠금. 수정(시각 변경)과 배정이 이것으로 읽어 서로 직렬화한다.
     * 잠그지 않으면 "배정 없음"을 확인한 수정과 동시에 들어온 배정이 바뀌기 전 값으로 검사돼 저장될 수 있다.
     * 다른 법인 근무유형은 빈 값(→ 404).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM WorkPattern p WHERE p.enterCd = :enterCd AND p.patternId = :patternId")
    Optional<WorkPattern> findForUpdate(@Param("enterCd") String enterCd, @Param("patternId") long patternId);
}
