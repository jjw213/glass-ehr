package com.glassehr.appointment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발령번호 채번. 형식 {@code AP-등록연도-6자리 순번}(예: AP-2026-000001). 순번은 Oracle 시퀀스 hr_appointment_no_seq(V014)에서
 * 받아 연도와 상관없이 늘어난다. 동시 등록에도 겹치지 않고, 등록이 롤백되면 빈 번호가 생길 수 있다(번호는 식별용).
 */
@Component
public class AppointmentNoGenerator {

    /** DB CHECK ck_hr_appt_no 와 같은 규칙. */
    public static final String APPT_NO_PATTERN = "^AP-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]$";

    /** 순번 자릿수 상한(형식 정의. 법령 수치 아님). */
    static final long MAX_SEQ = 999_999L;

    @PersistenceContext
    private EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(int year) {
        Number n = (Number) em.createNativeQuery("SELECT hr_appointment_no_seq.NEXTVAL FROM dual").getSingleResult();
        long seq = n.longValue();
        if (seq > MAX_SEQ) {
            throw AppointmentException.conflict("발령번호 순번(" + MAX_SEQ + ")이 모두 쓰였어요. 관리자에게 문의해 주세요");
        }
        return format(year, seq);
    }

    static String format(int year, long seq) {
        return String.format("AP-%04d-%06d", year, seq);
    }
}
