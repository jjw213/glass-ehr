package com.glassehr.attendance;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /** [from, to) 기간의 휴일, 날짜·ID 순. */
    @Query("SELECT h FROM Holiday h WHERE h.holidayDate >= :from AND h.holidayDate < :to"
            + " ORDER BY h.holidayDate, h.holidayId")
    List<Holiday> findInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Holiday> findByHolidayDate(LocalDate date);

    Optional<Holiday> findByReplacesHolidayId(long replacesHolidayId);

    List<Holiday> findByReplacesHolidayIdIn(Collection<Long> ids);

    /** 원래 공휴일 행 잠금. 같은 공휴일을 동시에 두 번 대체하지 않게 직렬화한다(DB UNIQUE 도 막는다). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Holiday h WHERE h.holidayId = :id")
    Optional<Holiday> findForUpdate(@Param("id") long id);
}
