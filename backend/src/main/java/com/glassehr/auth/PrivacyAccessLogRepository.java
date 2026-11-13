package com.glassehr.auth;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 추가·조회만 한다(수정·삭제 메서드를 노출하지 않음). */
public interface PrivacyAccessLogRepository extends Repository<PrivacyAccessLog, Long> {

    PrivacyAccessLog save(PrivacyAccessLog log);

    /** 기간 [from, to) 안에서 조건(빈 값이면 조건 없음)에 맞는 로그, 최근 순. */
    @Query("""
            select l from PrivacyAccessLog l
            where l.accessedAt >= :from and l.accessedAt < :to
              and (:accessorId is null or l.accessorId = :accessorId)
              and (:targetEmpId is null or l.targetEmpId = :targetEmpId)
            order by l.accessedAt desc, l.logId desc""")
    List<PrivacyAccessLog> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("accessorId") String accessorId, @Param("targetEmpId") Long targetEmpId, Pageable page);
}
