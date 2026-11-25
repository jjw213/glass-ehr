package com.glassehr.appointment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 법인 조건(#124, docs/tenant-design.md 5.2): 모든 조회는 첫 인자 enterCd 로 그 법인 연결만 본다. */
public interface HrAppointmentCancelRepository extends JpaRepository<HrAppointmentCancel, Long> {

    Optional<HrAppointmentCancel> findByEnterCdAndCancelApptId(String enterCd, long cancelApptId);

    boolean existsByEnterCdAndCancelApptId(String enterCd, long cancelApptId);

    Optional<HrAppointmentCancel> findByEnterCdAndOriginalApptId(String enterCd, long originalApptId);

    List<HrAppointmentCancel> findByEnterCdAndCancelApptIdIn(String enterCd, Collection<Long> cancelApptIds);

    List<HrAppointmentCancel> findByEnterCd(String enterCd);
}
