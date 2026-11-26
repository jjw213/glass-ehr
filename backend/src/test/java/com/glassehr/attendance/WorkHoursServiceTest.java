package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.DailyWorkSummaryMapper.PunchRecord;
import com.glassehr.attendance.WorkHoursCalculator.DayKind;
import com.glassehr.attendance.WorkHoursService.Shift;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.legalrate.LegalRateException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 원자료 모으기·근무 짝짓기·재계산 저장. 기준값은 테스트 입력(법령 정답 아님). 날짜는 가짜 값(2099-03-02 월요일). */
class WorkHoursServiceTest {

    private static final String ENTER = "C001";
    private static final long EMP = 7L;
    private static final long PATTERN = 3L;
    private static final LocalDate MON = LocalDate.of(2099, 3, 2);

    private DailyWorkSummaryMapper mapper;
    private EmpWorkPatternRepository assignments;
    private WorkPatternRepository patterns;
    private HolidayService holidays;
    private ApprovedLeaveProvider leaves;
    private ApprovedOvertimeProvider overtimes;
    private WorkHoursLegalRules legalRules;
    private EmployeeRepository employees;
    private WorkPolicyService policies;
    private WorkHoursService service;

    /** 근무유형 PATTERN 에 정책을 재정의한다(#159). */
    private void policyIs(WorkPolicyRules.Policy p) {
        when(policies.resolved(ENTER)).thenReturn(
                new WorkPolicyService.Resolved(WorkPolicyRules.SYSTEM_DEFAULT, Map.of(PATTERN, p)));
    }

    private static WorkPolicyRules.Policy policy(int unit, WorkPolicyRules.Rounding rounding,
            WorkPolicyRules.MissingOut missingOut) {
        WorkPolicyRules.Policy d = WorkPolicyRules.SYSTEM_DEFAULT;
        return new WorkPolicyRules.Policy(d.overtime(), d.night(), d.holiday(), true, unit, 0, rounding, 0, 0, true,
                missingOut, true);
    }

    @Test
    void 정책_단위_15분_올림이면_7분_연장을_15분으로_인정한다() {
        policyIs(policy(15, WorkPolicyRules.Rounding.UP, WorkPolicyRules.MissingOut.IN_ONLY));
        LocalDate wed = MON.plusDays(2);
        punchesAre(punch(AttendancePunch.Type.IN, wed.atTime(9, 0)), punch(AttendancePunch.Type.OUT, wed.atTime(18, 7)));

        DailyWorkSummaryRow w = service.recalculate(ENTER, EMP, wed, wed, "hr1").get(2);
        assertThat(w.overtimeMinutes()).isEqualTo(15);
        assertThat(w.workedMinutes()).isEqualTo(487);
        assertThat(w.workEnd()).isEqualTo(wed.atTime(18, 7));
    }

    @Test
    void 퇴근_누락_정시_퇴근_처리면_종업_시각까지_인정한다() {
        policyIs(policy(1, WorkPolicyRules.Rounding.UP, WorkPolicyRules.MissingOut.SCHEDULED_END));
        LocalDate wed = MON.plusDays(2);
        punchesAre(punch(AttendancePunch.Type.IN, wed.atTime(9, 0)));

        DailyWorkSummaryRow w = service.recalculate(ENTER, EMP, wed, wed, "hr1").get(2);
        assertThat(w.workedMinutes()).isEqualTo(480);
        assertThat(w.workEnd()).isNull();
        assertThat(w.issues()).isEqualTo("MISSING_OUT,OUT_SET_TO_SCHEDULE");
    }

    @Test
    void 퇴근_누락_출근만_남김이면_실근로_0분() {
        policyIs(policy(1, WorkPolicyRules.Rounding.UP, WorkPolicyRules.MissingOut.IN_ONLY));
        LocalDate wed = MON.plusDays(2);
        punchesAre(punch(AttendancePunch.Type.IN, wed.atTime(9, 0)));

        DailyWorkSummaryRow w = service.recalculate(ENTER, EMP, wed, wed, "hr1").get(2);
        assertThat(w.workedMinutes()).isZero();
        assertThat(w.issues()).isEqualTo("MISSING_OUT");
    }

    @Test
    void 퇴근_누락_다음_날_확인_요청이면_0분과_확인_요청_표시() {
        policyIs(policy(1, WorkPolicyRules.Rounding.UP, WorkPolicyRules.MissingOut.CONFIRM_NEXT_DAY));
        LocalDate wed = MON.plusDays(2);
        punchesAre(punch(AttendancePunch.Type.IN, wed.atTime(9, 0)));

        DailyWorkSummaryRow w = service.recalculate(ENTER, EMP, wed, wed, "hr1").get(2);
        assertThat(w.workedMinutes()).isZero();
        assertThat(w.issues()).isEqualTo("MISSING_OUT,OUT_CONFIRM_REQUESTED");
    }

    @Test
    void 실시간_기록을_끄면_계획_기준으로_인정하고_연장_허용_안_함은_표시만_한다() {
        WorkPolicyRules.Policy d = WorkPolicyRules.SYSTEM_DEFAULT;
        policyIs(new WorkPolicyRules.Policy(WorkPolicyRules.Allowance.DENY, d.night(), d.holiday(), true, 1, 0,
                WorkPolicyRules.Rounding.UP, 0, 0, true, WorkPolicyRules.MissingOut.IN_ONLY, false));
        LocalDate wed = MON.plusDays(2);
        punchesAre(punch(AttendancePunch.Type.IN, wed.atTime(8, 0)), punch(AttendancePunch.Type.OUT, wed.atTime(20, 0)));
        DailyWorkSummaryRow w = service.recalculate(ENTER, EMP, wed, wed, "hr1").get(2);
        assertThat(w.workedMinutes()).isEqualTo(480);
        assertThat(w.overtimeMinutes()).isZero();

        policyIs(new WorkPolicyRules.Policy(WorkPolicyRules.Allowance.DENY, d.night(), d.holiday(), true, 1, 0,
                WorkPolicyRules.Rounding.UP, 0, 0, true, WorkPolicyRules.MissingOut.IN_ONLY, true));
        w = service.recalculate(ENTER, EMP, wed, wed, "hr1").get(2);
        assertThat(w.overtimeMinutes()).isEqualTo(180);
        assertThat(w.issues()).contains("OVERTIME_POLICY_DENIED");
    }

    private static final WorkHoursLegalRules.Applied APPLIED = new WorkHoursLegalRules.Applied(
            new WorkHoursCalculator.Rules(480, 2400, 480, 22 * 60, 6 * 60),
            new WorkHoursLegalRules.Rates(BigDecimal.valueOf(11), BigDecimal.valueOf(22), BigDecimal.valueOf(33),
                    BigDecimal.valueOf(44)));

    /** 09:00~18:00, 휴게 12:00 60분, 월~금 근무·토 휴무·일 주휴일. */
    private static WorkPattern dayPattern() {
        WorkPatternRules.Spec spec = new WorkPatternRules.Spec(9 * 60, 18 * 60, 12 * 60, 60, "WWWWWOH");
        WorkPattern p = new WorkPattern(ENTER, "DAY", "주간", spec, new WorkPatternRules.Result(480, 2400, List.of()),
                true, null, LocalDateTime.of(2099, 1, 1, 0, 0));
        // 저장된 근무유형처럼 ID 를 둔다(정책 재정의는 근무유형 ID 로 찾는다).
        org.springframework.test.util.ReflectionTestUtils.setField(p, "patternId", PATTERN);
        return p;
    }

    /** 근무일은 기록 시각의 날짜(V021 ck_punch_work_date_same). */
    private static PunchRecord punch(AttendancePunch.Type type, LocalDateTime at) {
        return new PunchRecord(type.name(), at, at.toLocalDate());
    }

    private void punchesAre(PunchRecord... list) {
        when(mapper.findPunches(eq(ENTER), eq(EMP), any(), any())).thenReturn(List.of(list));
    }

    @BeforeEach
    void setUp() {
        mapper = mock(DailyWorkSummaryMapper.class);
        assignments = mock(EmpWorkPatternRepository.class);
        patterns = mock(WorkPatternRepository.class);
        holidays = mock(HolidayService.class);
        leaves = mock(ApprovedLeaveProvider.class);
        overtimes = mock(ApprovedOvertimeProvider.class);
        legalRules = mock(WorkHoursLegalRules.class);
        employees = mock(EmployeeRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2099-03-10T01:00:00Z"), WorkHoursService.SEOUL);
        policies = mock(WorkPolicyService.class);
        when(policies.resolved(ENTER)).thenReturn(WorkPolicyService.Resolved.NONE);
        service = new WorkHoursService(mapper, assignments, patterns, holidays, leaves, overtimes, legalRules,
                employees, policies, clock);
        when(employees.findByEnterCdAndEmpId(ENTER, EMP)).thenReturn(Optional.of(mock(Employee.class)));
        when(assignments.findByEnterCdAndEmpIdOrderByValidFromAsc(ENTER, EMP)).thenReturn(List.of(
                new EmpWorkPattern(ENTER, EMP, PATTERN, LocalDate.of(2099, 1, 1), null, "hr1", LocalDateTime.now())));
        when(patterns.findByEnterCdAndPatternId(ENTER, PATTERN)).thenReturn(Optional.of(dayPattern()));
        when(holidays.holidayDates(eq(ENTER), any(), any())).thenReturn(Set.of());
        when(leaves.approvedLeaves(anyLong(), any(), any())).thenReturn(Map.of());
        when(legalRules.asOf(any())).thenReturn(APPLIED);
    }

    @Test
    void 기간을_월요일부터_일요일까지_넓혀_지우고_다시_넣는다() {
        LocalDate wed = MON.plusDays(2);
        when(mapper.findPunches(ENTER, EMP, MON, MON.plusDays(8)))
                .thenReturn(List.of(punch(AttendancePunch.Type.IN, wed.atTime(9, 0)),
                        punch(AttendancePunch.Type.OUT, wed.atTime(20, 0))));

        List<DailyWorkSummaryRow> rows = service.recalculate(ENTER,EMP, wed, wed, "hr1");

        verify(mapper).deleteRange(ENTER, EMP, MON, MON.plusDays(6));
        verify(mapper, times(7)).insert(eq(ENTER), any());
        assertThat(rows).hasSize(7);
        DailyWorkSummaryRow w = rows.get(2);
        assertThat(w.workDate()).isEqualTo(wed);
        assertThat(w.dayKind()).isEqualTo("WORKDAY");
        assertThat(w.contractMinutes()).isEqualTo(480);
        assertThat(w.breakMinutes()).isEqualTo(60);
        assertThat(w.workedMinutes()).isEqualTo(600);
        assertThat(w.overtimeMinutes()).isEqualTo(120);
        assertThat(w.overtimeRate()).isEqualByComparingTo("11");
        assertThat(w.holidayExcessRate()).isEqualByComparingTo("44");
        assertThat(w.calculatedBy()).isEqualTo("hr1");
        assertThat(w.calculatedAt()).isEqualTo(LocalDateTime.of(2099, 3, 10, 10, 0));
        assertThat(rows.get(5).dayKind()).isEqualTo("OFFDAY");
        assertThat(rows.get(6).dayKind()).isEqualTo("HOLIDAY");
        assertThat(rows.get(0).workedMinutes()).isZero();
    }

    @Test
    void 연장근로_실적에_승인된_사전_신청이_없으면_확인_표시를_남긴다() {
        LocalDate wed = MON.plusDays(2);
        punchesAre(punch(AttendancePunch.Type.IN, wed.atTime(9, 0)), punch(AttendancePunch.Type.OUT, wed.atTime(20, 0)));

        List<DailyWorkSummaryRow> rows = service.recalculate(ENTER,EMP, wed, wed, "hr1");
        assertThat(rows.get(2).overtimeMinutes()).isPositive();
        assertThat(rows.get(2).issues()).isEqualTo("OVERTIME_NOT_APPROVED");
        assertThat(rows.get(1).issues()).isNull();

        when(overtimes.approvedDates(EMP, MON, MON.plusDays(6))).thenReturn(Set.of(wed));
        assertThat(service.recalculate(ENTER,EMP, wed, wed, "hr1").get(2).issues()).isNull();
    }

    @Test
    void 휴일_달력에_있는_소정근로일은_휴일이고_소정근로시간은_0() {
        when(holidays.holidayDates(eq(ENTER), any(), any())).thenReturn(Set.of(MON));
        punchesAre(punch(AttendancePunch.Type.IN, MON.atTime(9, 0)), punch(AttendancePunch.Type.OUT, MON.atTime(18, 0)));
        DailyWorkSummaryRow r = service.recalculate(ENTER,EMP, MON, MON, "hr1").get(0);
        assertThat(r.dayKind()).isEqualTo("HOLIDAY");
        assertThat(r.contractMinutes()).isZero();
        assertThat(r.holidayMinutes()).isEqualTo(480);
    }

    @Test
    void 승인된_반차를_입력으로_넣는다() {
        when(leaves.approvedLeaves(anyLong(), any(), any())).thenReturn(
                Map.of(MON, new WorkHoursCalculator.Leave(WorkHoursCalculator.LeaveKind.HALF_DAY_AM, 240)));
        DailyWorkSummaryRow r = service.recalculate(ENTER,EMP, MON, MON, "hr1").get(0);
        assertThat(r.leaveKind()).isEqualTo("HALF_DAY_AM");
        assertThat(r.leaveMinutes()).isEqualTo(240);
    }

    @Test
    void 퇴근이_없거나_근무유형이_없으면_사유를_남긴다() {
        when(assignments.findByEnterCdAndEmpIdOrderByValidFromAsc(ENTER, EMP)).thenReturn(List.of(
                new EmpWorkPattern(ENTER, EMP, PATTERN, MON.plusDays(1), null, "hr1", LocalDateTime.now())));
        punchesAre(punch(AttendancePunch.Type.IN, MON.atTime(9, 0)));
        List<DailyWorkSummaryRow> rows = service.recalculate(ENTER,EMP, MON, MON, "hr1");
        assertThat(rows.get(0).issues()).isEqualTo("NO_WORK_PATTERN,MISSING_OUT");
        assertThat(rows.get(0).workStart()).isEqualTo(MON.atTime(9, 0));
        assertThat(rows.get(0).workEnd()).isNull();
        assertThat(rows.get(0).patternId()).isNull();
        assertThat(rows.get(1).issues()).isNull();
    }

    @Test
    void 기준값이_없으면_지우지_않는다() {
        when(legalRules.asOf(any())).thenThrow(mock(LegalRateException.class));
        assertThatThrownBy(() -> service.recalculate(ENTER,EMP, MON, MON, "hr1")).isInstanceOf(LegalRateException.class);
        verify(mapper, never()).deleteRange(any(), anyLong(), any(), any());
        verify(mapper, never()).insert(any(), any());
    }

    @Test
    void 없는_사원이나_잘못된_기간은_거부() {
        assertThatThrownBy(() -> service.recalculate(ENTER,99L, MON, MON, "hr1"))
                .isInstanceOf(AttendanceException.class).hasMessageContaining("사원이 없어요");
        assertThatThrownBy(() -> service.recalculate(ENTER,EMP, MON, MON.minusDays(1), "hr1"))
                .isInstanceOf(AttendanceException.class);
        assertThatThrownBy(() -> service.find(ENTER, EMP, MON, MON.plusDays(WorkHoursService.MAX_RANGE_DAYS)))
                .isInstanceOf(AttendanceException.class);
    }

    @Test
    void 다른_법인_사원의_집계_조회는_404이고_집계_행을_읽지_않는다() {
        // C002 세션에는 EMP 사원이 없다(#126). 존재 여부를 드러내지 않는다.
        assertThatThrownBy(() -> service.find("C002", EMP, MON, MON))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));
        verify(mapper, never()).findRange(any(), anyLong(), any(), any());
        service.find(ENTER, EMP, MON, MON);
        verify(mapper).findRange(ENTER, EMP, MON, MON);
    }

    @Test
    void 재계산을_두_번_해도_같은_결과() {
        punchesAre(punch(AttendancePunch.Type.IN, MON.atTime(8, 0)), punch(AttendancePunch.Type.OUT, MON.atTime(23, 0)));
        List<DailyWorkSummaryRow> a = service.recalculate(ENTER,EMP, MON, MON, "hr1");
        List<DailyWorkSummaryRow> b = service.recalculate(ENTER,EMP, MON.plusDays(3), MON.plusDays(3), "hr1");
        assertThat(b).isEqualTo(a);
        ArgumentCaptor<DailyWorkSummaryRow> rows = ArgumentCaptor.forClass(DailyWorkSummaryRow.class);
        verify(mapper, times(14)).insert(eq(ENTER), rows.capture());
        verify(mapper, times(2)).deleteRange(ENTER, EMP, MON, MON.plusDays(6));
    }

    // --- 근무 짝짓기 ---

    @Test
    void 자정을_넘는_퇴근은_전날_근무의_끝() {
        List<PunchRecord> list = List.of(punch(AttendancePunch.Type.IN, MON.atTime(22, 0)),
                punch(AttendancePunch.Type.OUT, MON.plusDays(1).atTime(7, 0)));
        assertThat(WorkHoursService.pairShift(list, MON)).isEqualTo(new Shift(MON.atTime(22, 0),
                MON.plusDays(1).atTime(7, 0)));
        assertThat(WorkHoursService.pairShift(list, MON.plusDays(1))).isEqualTo(new Shift(null, null));
    }

    @Test
    void 첫_출근과_마지막_퇴근을_쓰고_다음_근무일_출근_뒤의_퇴근은_쓰지_않는다() {
        List<PunchRecord> list = List.of(
                punch(AttendancePunch.Type.IN, MON.atTime(9, 0)),
                punch(AttendancePunch.Type.IN, MON.atTime(9, 5)),
                punch(AttendancePunch.Type.OUT, MON.atTime(18, 0)),
                punch(AttendancePunch.Type.OUT, MON.atTime(18, 10)),
                punch(AttendancePunch.Type.IN, MON.plusDays(1).atTime(9, 0)),
                punch(AttendancePunch.Type.OUT, MON.plusDays(1).atTime(18, 0)));
        assertThat(WorkHoursService.pairShift(list, MON)).isEqualTo(new Shift(MON.atTime(9, 0), MON.atTime(18, 10)));
        assertThat(WorkHoursService.pairShift(list, MON.plusDays(1)).end()).isEqualTo(MON.plusDays(1).atTime(18, 0));
    }

    @Test
    void 출근_뒤_24시간을_넘은_퇴근은_짝짓지_않는다() {
        List<PunchRecord> list = List.of(punch(AttendancePunch.Type.IN, MON.atTime(9, 0)),
                punch(AttendancePunch.Type.OUT, MON.plusDays(1).atTime(9, 1)));
        assertThat(WorkHoursService.pairShift(list, MON).end()).isNull();
    }

    @Test
    void 야간조_휴게는_시업보다_이르면_다음_날() {
        WorkPatternRules.Spec night = new WorkPatternRules.Spec(22 * 60, 7 * 60, 2 * 60, 60, "WWWWWOH");
        assertThat(WorkHoursService.scheduledBreaks(night, MON)).containsExactly(
                new WorkHoursCalculator.Interval(MON.plusDays(1).atTime(2, 0), MON.plusDays(1).atTime(3, 0)));
        WorkPatternRules.Spec none = new WorkPatternRules.Spec(9 * 60, 13 * 60, null, 0, "WWWWWOH");
        assertThat(WorkHoursService.scheduledBreaks(none, MON)).isEmpty();
    }

    @Test
    void 요일_구분과_휴일_달력으로_날의_성격을_정한다() {
        Optional<WorkPattern> p = Optional.of(dayPattern());
        assertThat(WorkHoursService.dayKind(p, Set.of(), MON)).isEqualTo(DayKind.WORKDAY);
        assertThat(WorkHoursService.dayKind(p, Set.of(), MON.plusDays(5))).isEqualTo(DayKind.OFFDAY);
        assertThat(WorkHoursService.dayKind(p, Set.of(), MON.plusDays(6))).isEqualTo(DayKind.HOLIDAY);
        assertThat(WorkHoursService.dayKind(p, Set.of(MON.plusDays(5)), MON.plusDays(5))).isEqualTo(DayKind.HOLIDAY);
        assertThat(WorkHoursService.dayKind(Optional.empty(), Set.of(), MON)).isEqualTo(DayKind.WORKDAY);
    }
}
