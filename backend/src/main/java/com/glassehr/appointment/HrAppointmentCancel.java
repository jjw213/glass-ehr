package com.glassehr.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 취소 발령과 원 발령의 연결. 테이블: V019__hr_appointment_apply_cancel.sql. 설계: docs/hr-appointment-design.md 6장.
 * 원 발령 하나에 취소 발령은 하나만 있다(uq_hr_appt_cancel_original).
 */
@Entity
@Table(name = "hr_appointment_cancel")
public class HrAppointmentCancel {

    @Id
    @Column(name = "cancel_appt_id", nullable = false, updatable = false)
    private Long cancelApptId;

    @Column(name = "original_appt_id", nullable = false, updatable = false)
    private Long originalApptId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected HrAppointmentCancel() {
    }

    HrAppointmentCancel(long cancelApptId, long originalApptId, LocalDateTime now) {
        this.cancelApptId = cancelApptId;
        this.originalApptId = originalApptId;
        this.createdAt = now;
    }

    public Long getCancelApptId() {
        return cancelApptId;
    }

    public Long getOriginalApptId() {
        return originalApptId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
