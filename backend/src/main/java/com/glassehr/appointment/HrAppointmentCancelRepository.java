package com.glassehr.appointment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrAppointmentCancelRepository extends JpaRepository<HrAppointmentCancel, Long> {

    Optional<HrAppointmentCancel> findByOriginalApptId(long originalApptId);
}
