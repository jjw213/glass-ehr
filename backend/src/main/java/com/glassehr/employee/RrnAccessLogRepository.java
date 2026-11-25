package com.glassehr.employee;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 추가·조회만 한다. 수정·삭제 메서드를 노출하지 않으려고 JpaRepository 대신 Repository 를 쓴다. */
public interface RrnAccessLogRepository extends Repository<RrnAccessLog, Long> {

    RrnAccessLog save(RrnAccessLog log);

    List<RrnAccessLog> findByEnterCdAndEmpIdOrderByAccessedAtAsc(String enterCd, Long empId);

    /**
     * 대상 법인 enterCd 의 기간 [from, to) 로그 중 조건(빈 값이면 조건 없음)에 맞는 것, 최근 순. 개인정보 조회 로그 화면(#19)용.
     * 다른 법인 관리자가 이 법인 데이터를 본 기록도 대상 법인 기준이라 보인다(docs/tenant-design.md 4.4).
     */
    @Query("""
            select l from RrnAccessLog l
            where l.enterCd = :enterCd
              and l.accessedAt >= :from and l.accessedAt < :to
              and (:accessorId is null or l.accessorId = :accessorId)
              and (:empId is null or l.empId = :empId)
            order by l.accessedAt desc""")
    List<RrnAccessLog> search(@Param("enterCd") String enterCd, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("accessorId") String accessorId, @Param("empId") Long empId,
            Pageable page);
}
