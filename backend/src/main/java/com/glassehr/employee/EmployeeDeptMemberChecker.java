package com.glassehr.employee;

import com.glassehr.org.DeptMemberChecker;
import com.glassehr.appointment.AppointmentService;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** 부서 폐지 전 확정·미취소 발령 이력(미래 발령 포함) 기준으로 소속 재직자를 확인한다. */
@Component
public class EmployeeDeptMemberChecker implements DeptMemberChecker {

    private final AppointmentService appointments;

    public EmployeeDeptMemberChecker(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @Override
    public long countMembersOnOrAfter(long deptId, LocalDate date) {
        return appointments.countDeptMembersOnOrAfter(deptId, date);
    }
}
