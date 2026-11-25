package com.glassehr.attendance;

import com.glassehr.attendance.WeeklyHoursMonitor.Limits;
import com.glassehr.attendance.WeeklyHoursMonitor.Status;
import com.glassehr.attendance.WeeklyHoursMonitor.Week;
import com.glassehr.auth.AuthUser;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.legalrate.LegalRateItem;
import com.glassehr.legalrate.LegalRateRow;
import com.glassehr.legalrate.LegalRateService;
import com.glassehr.org.OrgDeptService;
import com.glassehr.org.OrgDtos.DeptNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주 52시간 모니터링(#31). 이번 주(서울 기준 오늘이 속한 월~일)를 저장 없이 계산해 본인 게이지와 부서별 표를 만든다.
 * 한도는 legal_rate(근로기준법 제50조 제1항 WEEKLY_STANDARD_WORK_HOURS, 제53조 제1항 WEEKLY_OVERTIME_LIMIT_HOURS)를
 * 그 주 월요일 기준으로 읽고, 경고 기준(남은 분)은 회사 설정 {@code glassehr.attendance.weekly-hours-warn-remain-minutes}.
 * 판정 규칙: {@link WeeklyHoursMonitor}. 설계: docs/weekly-hours-monitor-design.md
 */
@Service
public class WeeklyHoursService {

    /** 시간 → 분 환산(시계 단위, 법정 수치 아님). */
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final WorkHoursService workHours;
    private final LegalRateService legalRates;
    private final EmployeeRepository employees;
    private final OrgDeptService org;
    private final int warnRemainMinutes;
    private final Clock clock;

    @Autowired
    public WeeklyHoursService(WorkHoursService workHours, LegalRateService legalRates, EmployeeRepository employees,
            OrgDeptService org, @Value("${glassehr.attendance.weekly-hours-warn-remain-minutes}") int warnRemainMinutes) {
        this(workHours, legalRates, employees, org, warnRemainMinutes, Clock.system(WorkHoursService.SEOUL));
    }

    WeeklyHoursService(WorkHoursService workHours, LegalRateService legalRates, EmployeeRepository employees,
            OrgDeptService org, int warnRemainMinutes, Clock clock) {
        if (warnRemainMinutes < 0) {
            throw new IllegalArgumentException("glassehr.attendance.weekly-hours-warn-remain-minutes 는 0 이상이어야 해요");
        }
        this.workHours = workHours;
        this.legalRates = legalRates;
        this.employees = employees;
        this.org = org;
        this.warnRemainMinutes = warnRemainMinutes;
        this.clock = clock.withZone(WorkHoursService.SEOUL);
    }

    public record EmpWeek(long empId, String empNo, String name, Long deptId, Week week) {
    }

    public record MyWeek(LocalDate weekStart, LocalDate weekEnd, LocalDate today, Limits limits, EmpWeek me) {
    }

    public record DeptWeek(Long deptId, String deptName, int nearCount, int overCount, List<EmpWeek> employees) {
    }

    public record TeamWeek(LocalDate weekStart, LocalDate weekEnd, LocalDate today, Limits limits, int nearCount,
            int overCount, List<DeptWeek> departments) {
    }

    /** 본인 이번 주. 사원과 연결되지 않은 계정은 403. */
    @Transactional(readOnly = true)
    public MyWeek mine(AuthUser user) {
        if (user.empId() == null) {
            throw new AttendanceException(HttpStatus.FORBIDDEN, "사원과 연결된 계정만 볼 수 있어요", List.of());
        }
        Employee e = employees.findById(user.empId())
                .orElseThrow(() -> AttendanceException.notFound("사원이 없어요: " + user.empId()));
        LocalDate today = LocalDate.now(clock);
        WorkHoursService.WeekInputs week = workHours.weekInputs(e.getEnterCd(), today);
        Limits limits = limits(week.monday());
        return new MyWeek(week.monday(), week.monday().plusDays(6), today, limits, empWeek(e, week, limits));
    }

    /**
     * 볼 수 있는 사원(전체 조회 권한이면 전체, 부서장이면 맡은 부서·하위부서 소속)의 이번 주를 부서별로.
     * 그 주에 재직한 사원만(입사일 ≤ 일요일, 퇴직일 ≥ 월요일 — 퇴직일 당일까지 재직). 부서 안은 초과·근접 먼저, 사번 순.
     */
    @Transactional(readOnly = true)
    public TeamWeek team(AuthUser user) {
        LocalDate today = LocalDate.now(clock);
        WorkHoursService.WeekInputs week = workHours.weekInputs(user.enterCd(), today);
        LocalDate monday = week.monday();
        LocalDate sunday = monday.plusDays(6);
        Limits limits = limits(monday);
        Map<Long, String> deptNames = new HashMap<>();
        collectNames(org.tree(user.enterCd(), today).roots(), deptNames);

        Map<Long, List<EmpWeek>> byDept = new LinkedHashMap<>();
        // 휴일 달력(week)이 현재 법인 것이므로 현재 법인 사원만 계산한다(#125).
        for (Employee e : employees.findByEnterCdOrderByEmpNoAsc(user.enterCd())) {
            boolean employed = !e.getHireDate().isAfter(sunday)
                    && (e.getRetireDate() == null || !e.getRetireDate().isBefore(monday));
            if (!employed || !user.canSeeEmployee(e.getEmpId(), e.getCurrentDeptId())) {
                continue;
            }
            byDept.computeIfAbsent(e.getCurrentDeptId(), k -> new ArrayList<>()).add(empWeek(e, week, limits));
        }
        Comparator<EmpWeek> order = Comparator.comparingInt((EmpWeek w) -> -w.week().status().ordinal())
                .thenComparing(EmpWeek::empNo);
        List<DeptWeek> depts = new ArrayList<>();
        for (Map.Entry<Long, List<EmpWeek>> en : byDept.entrySet()) {
            List<EmpWeek> list = en.getValue().stream().sorted(order).toList();
            String name = en.getKey() == null ? "부서 없음" : deptNames.getOrDefault(en.getKey(), "부서 " + en.getKey());
            depts.add(new DeptWeek(en.getKey(), name, count(list, Status.NEAR), count(list, Status.OVER), list));
        }
        depts.sort(Comparator.comparing(DeptWeek::deptName));
        int near = depts.stream().mapToInt(DeptWeek::nearCount).sum();
        int over = depts.stream().mapToInt(DeptWeek::overCount).sum();
        return new TeamWeek(monday, sunday, today, limits, near, over, depts);
    }

    private EmpWeek empWeek(Employee e, WorkHoursService.WeekInputs week, Limits limits) {
        Week w = WeeklyHoursMonitor.judge(workHours.computeWeek(e.getEmpId(), week), limits);
        return new EmpWeek(e.getEmpId(), e.getEmpNo(), e.getName(), e.getCurrentDeptId(), w);
    }

    /** 근로기준법 제50조 제1항·제53조 제1항 한도를 그 주 월요일 기준으로 읽는다. 값이 없으면 LegalRateException(409). */
    Limits limits(LocalDate monday) {
        return new Limits(hoursAsMinutes(LegalRateItem.WEEKLY_STANDARD_WORK_HOURS, monday),
                hoursAsMinutes(LegalRateItem.WEEKLY_OVERTIME_LIMIT_HOURS, monday), warnRemainMinutes);
    }

    private int hoursAsMinutes(LegalRateItem item, LocalDate date) {
        LegalRateRow row = legalRates.getEffective(item, date);
        try {
            return row.rateValue().multiply(MINUTES_PER_HOUR).intValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("기준값을 분 정수로 바꿀 수 없어요: " + row.itemCode() + " " + row.effectiveFrom());
        }
    }

    private static int count(List<EmpWeek> list, Status s) {
        return (int) list.stream().filter(w -> w.week().status() == s).count();
    }

    private static void collectNames(List<DeptNode> nodes, Map<Long, String> out) {
        for (DeptNode n : nodes) {
            out.put(n.deptId(), n.name());
            collectNames(n.children(), out);
        }
    }
}
