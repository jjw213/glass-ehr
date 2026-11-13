package com.glassehr.attendance;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkPatternRepository extends JpaRepository<WorkPattern, Long> {

    List<WorkPattern> findAllByOrderByPatternCodeAsc();

    boolean existsByPatternCode(String patternCode);

    /**
     * 근무유형 행 잠금. 수정(시각 변경)과 배정이 이것으로 읽어 서로 직렬화한다.
     * 잠그지 않으면 "배정 없음"을 확인한 수정과 동시에 들어온 배정이 바뀌기 전 값으로 검사돼 저장될 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM WorkPattern p WHERE p.patternId = :patternId")
    Optional<WorkPattern> findForUpdate(@Param("patternId") long patternId);
}
