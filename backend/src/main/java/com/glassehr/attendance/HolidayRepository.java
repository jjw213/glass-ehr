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

/** 휴일 달력은 법인마다 따로 둔다. 모든 조회는 법인 조건을 건다(#125, docs/tenant-design.md 5.2). */
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /** [from, to) 기간의 그 법인 휴일, 날짜·ID 순. */
    @Query("SELECT h FROM Holiday h WHERE h.enterCd = :enterCd AND h.holidayDate >= :from AND h.holidayDate < :to"
            + " ORDER BY h.holidayDate, h.holidayId")
    List<Holiday> findInRange(@Param("enterCd") String enterCd, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    Optional<Holiday> findByEnterCdAndHolidayId(String enterCd, Long holidayId);

    List<Holiday> findByEnterCdAndHolidayIdIn(String enterCd, Collection<Long> ids);

    List<Holiday> findByEnterCdAndAgreementId(String enterCd, Long agreementId);

    List<Holiday> findByEnterCdAndHolidayDate(String enterCd, LocalDate date);

    Optional<Holiday> findByEnterCdAndReplacesHolidayId(String enterCd, Long replacesHolidayId);

    List<Holiday> findByEnterCdAndReplacesHolidayIdIn(String enterCd, Collection<Long> ids);

    /** 원래 공휴일 행 잠금. 같은 공휴일을 동시에 두 번 대체하지 않게 직렬화한다(DB UNIQUE 도 막는다). 다른 법인 행은 빈 값. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Holiday h WHERE h.enterCd = :enterCd AND h.holidayId = :id")
    Optional<Holiday> findForUpdate(@Param("enterCd") String enterCd, @Param("id") long id);
}
