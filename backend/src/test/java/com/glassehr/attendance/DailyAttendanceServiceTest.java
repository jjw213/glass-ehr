package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.DailyAttendanceService.DayStatus;
import com.glassehr.attendance.DailyAttendanceService.Period;
import com.glassehr.attendance.WorkHoursCalculator.Leave;
import com.glassehr.attendance.WorkHoursCalculator.LeaveKind;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 일별 출근·결근 판정(#114). 법정 수치를 쓰지 않는 분류 규칙 테스트. 날짜는 가짜 값(2099-03-02 월요일). */
class DailyAttendanceServiceTest {

    private static final long EMP = 7L;
    private static final long PATTERN = 3L;
    private static final LocalDate MON = LocalDate.of(2099, 3, 2);

    private DailyWorkSummaryMapper mapper;
    private ApprovedLeaveProvider leaves;
    private DailyAttendanceService service;

    @BeforeEach
    void setUp() {
        mapper = mock(DailyWorkSummaryMapper.class);
        leaves = mock(ApprovedLeaveProvider.class);
        service = new DailyAttendanceService(mapper, leaves);
        when(leaves.approvedLeaves(eq(EMP), any(), any())).thenReturn(Map.of());
    }

    /** 집계 행. start/end 는 그날 출근·퇴근 시각(시), null 이면 기록 없음. */
    private static DailyWorkSummaryRow row(LocalDate d, String dayKind, Long patternId, Integer startHour,
            Integer endHour, String issues) {
        LocalDateTime start = startHour == null ? null : d.atTime(startHour, 0);
        LocalDateTime end = endHour == null ? null : d.atTime(endHour, 0);
        return new DailyWorkSummaryRow(EMP, d, dayKind, patternId, "WORKDAY".equals(dayKind) ? 480 : 0, null, 0,
                start, end, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null, issues, "hr1", d.atTime(23, 0));
    }

    private static DailyWorkSummaryRow workday(LocalDate d, Integer startHour, Integer endHour) {
        return row(d, "WORKDAY", PATTERN, startHour, endHour, null);
    }

    private Period judgeOne(DailyWorkSummaryRow r) {
        when(mapper.findRange(EMP, r.workDate(), r.workDate())).thenReturn(List.of(r));
        return service.judge(EMP, r.workDate(), r.workDate());
    }

    @Test
    void 출근_기록이_있으면_출근_지각_조퇴도_결근이_아니다() {
        Period late = judgeOne(workday(MON, 11, 18));
        assertThat(late.days().get(0).status()).isEqualTo(DayStatus.PRESENT);
        Period early = judgeOne(workday(MON, 9, 13));
        assertThat(early.days().get(0).status()).isEqualTo(DayStatus.PRESENT);
        assertThat(early.absentDays()).isZero();
        assertThat(early.scheduledDays()).isEqualTo(1);
    }

    @Test
    void 출근_기록도_휴가도_없는_소정근로일은_결근() {
        Period p = judgeOne(workday(MON, null, null));
        assertThat(p.days().get(0).status()).isEqualTo(DayStatus.ABSENT);
        assertThat(p.absentDays()).isEqualTo(1);
        assertThat(p.scheduledDays()).isEqualTo(1);
    }

    @Test
    void 퇴근_누락일은_출근() {
        Period p = judgeOne(row(MON, "WORKDAY", PATTERN, 9, null, "MISSING_OUT"));
        assertThat(p.days().get(0).status()).isEqualTo(DayStatus.PRESENT);
        assertThat(p.days().get(0).issues()).isEqualTo("MISSING_OUT");
        assertThat(p.absentDays()).isZero();
    }

    @Test
    void 휴일과_휴무일은_기록이_없어도_결근이_아니고_소정근로일에_세지_않는다() {
        Period holiday = judgeOne(row(MON.plusDays(6), "HOLIDAY", PATTERN, null, null, null));
        assertThat(holiday.days().get(0).status()).isEqualTo(DayStatus.HOLIDAY);
        assertThat(holiday.scheduledDays()).isZero();
        Period off = judgeOne(row(MON.plusDays(5), "OFFDAY", PATTERN, null, null, null));
        assertThat(off.days().get(0).status()).isEqualTo(DayStatus.OFF_DAY);
        assertThat(off.scheduledDays()).isZero();
        assertThat(off.absentDays()).isZero();
    }

    @Test
    void 휴일에_일해도_휴일이다() {
        Period p = judgeOne(row(MON.plusDays(6), "HOLIDAY", PATTERN, 9, 18, null));
        assertThat(p.days().get(0).status()).isEqualTo(DayStatus.HOLIDAY);
        assertThat(p.scheduledDays()).isZero();
    }

    @Test
    void 근무유형_미배정일은_소정근로일이_아니다() {
        // #29 집계는 미배정일을 소정근로일로 계산하고 NO_WORK_PATTERN 을 남긴다. 판정은 결근으로 세지 않는다.
        Period p = judgeOne(row(MON, "WORKDAY", null, null, null, "NO_WORK_PATTERN"));
        assertThat(p.days().get(0).status()).isEqualTo(DayStatus.NOT_SCHEDULED);
        assertThat(p.days().get(0).issues()).isEqualTo("NO_WORK_PATTERN");
        assertThat(p.scheduledDays()).isZero();
        assertThat(p.absentDays()).isZero();
    }

    @Test
    void 승인된_종일_휴가는_유급_무급으로_나누고_결근이_아니다() {
        when(leaves.approvedLeaves(eq(EMP), any(), any())).thenReturn(Map.of(
                MON, new Leave(LeaveKind.FULL_DAY, 480, true),
                MON.plusDays(1), new Leave(LeaveKind.FULL_DAY, 480, false)));
        when(mapper.findRange(EMP, MON, MON.plusDays(1)))
                .thenReturn(List.of(workday(MON, null, null), workday(MON.plusDays(1), null, null)));
        Period p = service.judge(EMP, MON, MON.plusDays(1));
        assertThat(p.days()).extracting(DailyAttendanceService.Day::status)
                .containsExactly(DayStatus.PAID_LEAVE, DayStatus.UNPAID_LEAVE);
        assertThat(p.scheduledDays()).isEqualTo(2);
        assertThat(p.absentDays()).isZero();
    }

    @Test
    void 반차일에_출근_기록이_있으면_출근_없으면_휴가() {
        when(leaves.approvedLeaves(eq(EMP), any(), any())).thenReturn(Map.of(
                MON, new Leave(LeaveKind.HALF_DAY_AM, 240, true),
                MON.plusDays(1), new Leave(LeaveKind.HALF_DAY_PM, 240, true)));
        when(mapper.findRange(EMP, MON, MON.plusDays(1)))
                .thenReturn(List.of(workday(MON, 14, 18), workday(MON.plusDays(1), null, null)));
        Period p = service.judge(EMP, MON, MON.plusDays(1));
        assertThat(p.days()).extracting(DailyAttendanceService.Day::status)
                .containsExactly(DayStatus.PRESENT, DayStatus.PAID_LEAVE);
    }

    @Test
    void 휴일에_걸친_휴가는_휴일() {
        LocalDate sun = MON.plusDays(6);
        when(leaves.approvedLeaves(eq(EMP), any(), any()))
                .thenReturn(Map.of(sun, new Leave(LeaveKind.FULL_DAY, 0, true)));
        Period p = judgeOne(row(sun, "HOLIDAY", PATTERN, null, null, null));
        assertThat(p.days().get(0).status()).isEqualTo(DayStatus.HOLIDAY);
    }

    @Test
    void 한_주를_날짜순으로_판정하고_소정근로일과_결근일을_센다() {
        List<DailyWorkSummaryRow> week = new ArrayList<>();
        week.add(workday(MON, 9, 18));
        week.add(workday(MON.plusDays(1), null, null));
        week.add(workday(MON.plusDays(2), 10, 18));
        week.add(workday(MON.plusDays(3), null, null));
        week.add(row(MON.plusDays(4), "HOLIDAY", PATTERN, null, null, null)); // 평일 공휴일
        week.add(row(MON.plusDays(5), "OFFDAY", PATTERN, null, null, null));
        week.add(row(MON.plusDays(6), "HOLIDAY", PATTERN, null, null, null));
        when(mapper.findRange(EMP, MON, MON.plusDays(6))).thenReturn(week);
        when(leaves.approvedLeaves(eq(EMP), any(), any()))
                .thenReturn(Map.of(MON.plusDays(3), new Leave(LeaveKind.FULL_DAY, 480, true)));

        Period p = service.judge(EMP, MON, MON.plusDays(6));

        assertThat(p.days()).extracting(DailyAttendanceService.Day::status).containsExactly(
                DayStatus.PRESENT, DayStatus.ABSENT, DayStatus.PRESENT, DayStatus.PAID_LEAVE,
                DayStatus.HOLIDAY, DayStatus.OFF_DAY, DayStatus.HOLIDAY);
        assertThat(p.scheduledDays()).isEqualTo(4);
        assertThat(p.absentDays()).isEqualTo(1);
    }

    @Test
    void 집계가_없는_날이_있으면_409() {
        when(mapper.findRange(EMP, MON, MON.plusDays(1))).thenReturn(List.of(workday(MON, 9, 18)));
        assertThatThrownBy(() -> service.judge(EMP, MON, MON.plusDays(1)))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining(MON.plusDays(1).toString());
    }

    @Test
    void 기간_검사_윤년_1년까지_볼_수_있다() {
        LocalDate from = LocalDate.of(2096, 1, 1); // 2096 윤년, 366일
        LocalDate to = LocalDate.of(2096, 12, 31);
        List<DailyWorkSummaryRow> year = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            year.add(workday(d, 9, 18));
        }
        when(mapper.findRange(EMP, from, to)).thenReturn(year);
        assertThat(service.judge(EMP, from, to).days()).hasSize(366);
        assertThatThrownBy(() -> service.judge(EMP, from, to.plusDays(1)))
                .isInstanceOf(AttendanceException.class);
        assertThatThrownBy(() -> service.judge(EMP, MON, MON.minusDays(1)))
                .isInstanceOf(AttendanceException.class);
    }
}
