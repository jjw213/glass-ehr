package com.glassehr.dashboard;

import com.glassehr.attendance.AttendancePunch;
import com.glassehr.auth.AuthUser;
import com.glassehr.employee.EmployeeDtos.EmployeeSummary;
import com.glassehr.employee.EmployeeService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 대시보드 요약(#77). 법령 계산이 아닌 단순 집계이며, 요청자의 사원 조회 범위(전체·부서장 맡은 부서) 안의 사원만 센다.
 * <ul>
 *   <li>재직 인원: 퇴직일이 없는 사원(사원 목록 기본 조회와 같은 기준)</li>
 *   <li>이번 달 입사: 재직 인원 중 입사일이 이번 달(서울)인 사원</li>
 *   <li>오늘 출근: 재직 인원 중 오늘(근무일 기준) 출근(IN) 기록이 하나라도 있는 사원(#28 원시 기록)</li>
 *   <li>입사·퇴사 추이: 이번 달을 포함한 최근 12개월, 입사일·퇴직일이 든 달로 센다</li>
 * </ul>
 * 휴가·일정·공지는 Spring 에 아직 기능이 없어 주지 않는다(화면이 숨긴다).
 */
@Service
public class DashboardService {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final int TREND_MONTHS = 12;

    public record TrendPoint(String month, long hire, long leave) {
    }

    public record Summary(LocalDate today, long headcount, long hiredThisMonth, long presentToday, List<TrendPoint> trend) {
    }

    private final EmployeeService employees;
    private final DashboardPunchQueries punches;
    private final Clock clock;

    @Autowired
    public DashboardService(EmployeeService employees, DashboardPunchQueries punches) {
        this(employees, punches, Clock.system(SEOUL));
    }

    DashboardService(EmployeeService employees, DashboardPunchQueries punches, Clock clock) {
        this.employees = employees;
        this.punches = punches;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary(AuthUser user) {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        YearMonth thisMonth = YearMonth.from(today);
        List<EmployeeSummary> visible = employees.list(user.enterCd(), true).stream()
                .filter(e -> user.canSeeEmployee(e.empId(), e.currentDeptId()))
                .toList();
        List<EmployeeSummary> active = visible.stream().filter(e -> e.retireDate() == null).toList();
        long hired = active.stream()
                .filter(e -> e.hireDate() != null && YearMonth.from(e.hireDate()).equals(thisMonth))
                .count();
        Set<Long> punchedIn = new HashSet<>(punches.findEmpIdsByWorkDateAndType(today, AttendancePunch.Type.IN));
        long present = active.stream().filter(e -> punchedIn.contains(e.empId())).count();

        List<TrendPoint> trend = new ArrayList<>(TREND_MONTHS);
        for (int i = TREND_MONTHS - 1; i >= 0; i--) {
            YearMonth m = thisMonth.minusMonths(i);
            long hire = visible.stream().filter(e -> e.hireDate() != null && YearMonth.from(e.hireDate()).equals(m)).count();
            long leave = visible.stream().filter(e -> e.retireDate() != null && YearMonth.from(e.retireDate()).equals(m)).count();
            trend.add(new TrendPoint(m.toString(), hire, leave));
        }
        return new Summary(today, active.size(), hired, present, List.copyOf(trend));
    }
}
