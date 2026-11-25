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

    /**
     * 발령 법인(#124). PK 는 DB 에서 (enter_cd, cancel_appt_id), 두 발령 FK 는 (enter_cd, 발령) 복합이라
     * 다른 법인 발령과 연결할 수 없다. appt_id 는 전체에서 유일하므로 JPA 식별자는 그대로 둔다.
     */
    @Column(name = "enter_cd", nullable = false, updatable = false)
    private String enterCd;

    @Column(name = "original_appt_id", nullable = false, updatable = false)
    private Long originalApptId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected HrAppointmentCancel() {
    }

    HrAppointmentCancel(String enterCd, long cancelApptId, long originalApptId, LocalDateTime now) {
        this.enterCd = enterCd;
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
