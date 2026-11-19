package com.glassehr.overtime;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OvertimeRequest r where r.requestId = :id")
    Optional<OvertimeRequest> findForUpdate(@Param("id") long id);

    List<OvertimeRequest> findByEmpIdOrderByWorkDateDescStartAtDesc(long empId, Pageable page);

    /** [from, to] 근로일의 해당 상태 신청. */
    List<OvertimeRequest> findByEmpIdAndWorkDateBetweenAndStatusIn(long empId, LocalDate from, LocalDate to,
            Collection<OvertimeRequest.Status> statuses);

    /** 시간이 겹치는 결재 중·승인 신청(같은 시간대 중복 신청 막기). */
    @Query("select r from OvertimeRequest r where r.empId = :empId and r.status in :statuses"
            + " and r.startAt < :endAt and r.endAt > :startAt")
    List<OvertimeRequest> findOverlapping(@Param("empId") long empId, @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt, @Param("statuses") Collection<OvertimeRequest.Status> statuses);
}
