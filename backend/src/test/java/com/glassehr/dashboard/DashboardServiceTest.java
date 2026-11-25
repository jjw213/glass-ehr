package com.glassehr.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.AttendancePunch;
import com.glassehr.auth.TestAuthUsers;
import com.glassehr.employee.EmployeeDtos.EmployeeSummary;
import com.glassehr.employee.EmployeeService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 홈 대시보드 요약 집계. 사원·날짜는 가짜 값(2099년). */
class DashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2099, 3, 2);
    /** 서울 2099-03-02 00:30 = UTC 2099-03-01 15:30 (UTC 날짜로 계산하면 2월로 틀린다). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2099-03-01T15:30:00Z"), DashboardService.SEOUL);

    private final EmployeeService employees = mock(EmployeeService.class);
    private final DashboardPunchQueries punches = mock(DashboardPunchQueries.class);
    private final DashboardService service = new DashboardService(employees, punches, CLOCK);

    private static EmployeeSummary emp(long id, Long dept, LocalDate hire, LocalDate retire) {
        return new EmployeeSummary(id, "E" + id, "가짜" + id, dept, "G1", "T1", "REG", hire, retire);
    }

    @BeforeEach
    void setUp() {
        when(employees.list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(true))).thenReturn(List.of(
                emp(1, 10L, LocalDate.of(2099, 3, 1), null),          // 이번 달 입사, 부서장 범위
                emp(2, 11L, LocalDate.of(2090, 1, 1), null),          // 하위부서, 오늘 출근
                emp(3, 20L, LocalDate.of(2098, 4, 30), null),         // 다른 부서, 12개월 전 달(4월)
                emp(4, 20L, LocalDate.of(2080, 1, 1), LocalDate.of(2099, 2, 28)), // 지난달 퇴직
                emp(5, 10L, LocalDate.of(2098, 3, 31), null)));       // 12개월 범위 밖(13개월 전)
        // 퇴직자 4번의 출근 기록은 재직 인원 기준 오늘 출근에 넣지 않는다
        when(punches.findEmpIdsByWorkDateAndType(TODAY, AttendancePunch.Type.IN)).thenReturn(List.of(2L, 3L, 4L));
    }

    @Test
    void 전체_조회_권한은_회사_전체를_센다() {
        DashboardService.Summary s = service.summary(TestAuthUsers.find("hr1", null).orElseThrow());
        assertThat(s.today()).isEqualTo(TODAY);
        assertThat(s.headcount()).isEqualTo(4);
        assertThat(s.hiredThisMonth()).isEqualTo(1);
        assertThat(s.presentToday()).isEqualTo(2);
        assertThat(s.trend()).hasSize(12);
        assertThat(s.trend().get(0)).isEqualTo(new DashboardService.TrendPoint("2098-04", 1, 0));
        assertThat(s.trend().get(10)).isEqualTo(new DashboardService.TrendPoint("2099-02", 0, 1));
        assertThat(s.trend().get(11)).isEqualTo(new DashboardService.TrendPoint("2099-03", 1, 0));
        assertThat(s.trend().stream().mapToLong(DashboardService.TrendPoint::hire).sum()).isEqualTo(2);
    }

    @Test
    void 부서장은_맡은_부서와_하위부서만_센다() {
        DashboardService.Summary s = service.summary(TestAuthUsers.find("head1", null).orElseThrow());
        assertThat(s.headcount()).isEqualTo(3); // 1, 2, 5 (부서 10·11)
        assertThat(s.hiredThisMonth()).isEqualTo(1);
        assertThat(s.presentToday()).isEqualTo(1); // 2번만, 3번은 범위 밖
        assertThat(s.trend().get(10).leave()).isZero(); // 다른 부서 퇴직자는 세지 않는다
    }
}
