package com.glassehr.approval;

import com.glassehr.appointment.AppointmentException;
import com.glassehr.appointment.AppointmentService;
import com.glassehr.appointment.EmpStatus;
import com.glassehr.auth.AppUserRepository;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.org.OrgDeptHistRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * 결재자 조회 구현. 소속·재직상태는 인사발령 이력(#59 {@link AppointmentService#assignmentOn}),
 * 부서장·상위 부서는 조직 이력(#8 org_dept_hist)에서 신청일 기준으로 읽는다.
 * 직책 보유자는 사원의 현재 직책(매일 발령 반영으로 오늘 값)에서 찾는다. 신청일은 항상 오늘이다.
 */
@Component
class JpaApproverDirectory implements ApproverResolver.Directory {

    private final AppointmentService appointments;
    private final OrgDeptHistRepository deptHist;
    private final EmployeeRepository employees;
    private final AppUserRepository users;

    JpaApproverDirectory(AppointmentService appointments, OrgDeptHistRepository deptHist,
            EmployeeRepository employees, AppUserRepository users) {
        this.appointments = appointments;
        this.deptHist = deptHist;
        this.employees = employees;
        this.users = users;
    }

    @Override
    public Optional<Long> deptOf(long empId, LocalDate date) {
        try {
            return Optional.ofNullable(appointments.assignmentOn(empId, date).deptId());
        } catch (AppointmentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> enterOf(long empId) {
        return employees.findById(empId).map(Employee::getEnterCd);
    }

    @Override
    public Optional<ApproverResolver.DeptInfo> dept(String enterCd, long deptId, LocalDate date) {
        return deptHist.findEffectiveOn(enterCd, date).stream()
                .filter(h -> h.getDeptId() == deptId)
                .findFirst()
                .map(h -> new ApproverResolver.DeptInfo(h.getHeadEmpId(), h.getParentDeptId()));
    }

    @Override
    public Set<Long> activeWithTitle(String titleCode) {
        return new TreeSet<>(employees.findActiveEmpIdsWithTitle(titleCode));
    }

    @Override
    public Set<Long> activeWithRole(String roleCode) {
        return new TreeSet<>(users.findActiveEmpIdsWithRole(roleCode));
    }

    @Override
    public boolean isActive(long empId, LocalDate date) {
        try {
            return appointments.assignmentOn(empId, date).empStatus() == EmpStatus.ACTIVE;
        } catch (AppointmentException e) {
            return false;
        }
    }
}
