package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.FlexibleWorkCalculator.Check;
import com.glassehr.attendance.FlexibleWorkCalculator.Limits;
import com.glassehr.attendance.FlexibleWorkCalculator.WorkSystem;
import com.glassehr.attendance.FlexibleWorkService.AssignRequest;
import com.glassehr.attendance.FlexibleWorkService.PlanInput;
import com.glassehr.attendance.FlexibleWorkService.Settlement;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 유연근무 서비스 흐름(서면합의 필수 검사·권한·계획 저장·정산 연결).
 * 한도는 {@link FlexibleWorkLegalRules} 목으로 넣는 <b>임의 값이며 법령 수치가 아니다</b>.
 * 법령 정답 대조는 {@link FlexibleWorkFixtureTest}(tests/legal-fixtures)가 맡는다.
 */
class FlexibleWorkServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 임의 기준값(법령 수치 아님): 단위기간 1개월, 특정 주 3000분, 특정 날 600분, 1일 420분, 1주 1800분. */
    private static final Limits FAKE_FLEX = new Limits(0, 1, 3000, 600, 0, 420, 1800);
    /** 임의 기준값(법령 수치 아님): 정산기간 1개월, 휴식 검사 600분(1개월 초과 정산기간에서만). */
    private static final Limits FAKE_SEL = new Limits(0, 1, 0, 0, 600, 420, 1800);
    private static final long EMP = 5L;

    private FlexWorkAssignRepository assigns;
    private FlexWorkPlanRepository plans;
    private EmployeeRepository employees;
    private LaborAgreementGuard guard;
    private FlexibleWorkLegalRules rules;
    private WorkHoursService workHours;
    private FlexibleWorkService service;
    private final List<FlexWorkAssign> saved = new ArrayList<>();

    private static final AuthUser HR = new AuthUser("hr1", 100L, Set.of("HR"),
            Set.of(Permission.EMP_WRITE, Permission.EMP_READ_ALL), Set.of(), "127.0.0.1");
    private static final AuthUser SELF = new AuthUser("emp1", EMP, Set.of("EMPLOYEE"), Set.of(), Set.of(),
            "127.0.0.1");
    private static final AuthUser OTHER = new AuthUser("emp2", 6L, Set.of("EMPLOYEE"), Set.of(), Set.of(),
            "127.0.0.1");

    @BeforeEach
    void setUp() {
        assigns = mock(FlexWorkAssignRepository.class);
        plans = mock(FlexWorkPlanRepository.class);
        employees = mock(EmployeeRepository.class);
        guard = mock(LaborAgreementGuard.class);
        rules = mock(FlexibleWorkLegalRules.class);
        workHours = mock(WorkHoursService.class);
        when(rules.flexibleAsOf(any(), any())).thenReturn(FAKE_FLEX);
        when(rules.selectiveAsOf(any(), any())).thenReturn(FAKE_SEL);
        Employee e = mock(Employee.class);
        when(e.getEmpId()).thenReturn(EMP);
        when(e.getSiteCode()).thenReturn("HQ");
        when(e.getHireDate()).thenReturn(LocalDate.of(2020, 1, 1));
        when(employees.findById(EMP)).thenReturn(Optional.of(e));
        when(employees.findForUpdate(EMP)).thenReturn(Optional.of(e));
        when(assigns.findByEmpIdOrderByPeriodStartAsc(anyLong())).thenReturn(List.of());
        when(assigns.save(any())).thenAnswer(inv -> {
            FlexWorkAssign a = inv.getArgument(0);
            setField(a, "flexId", 1L);
            saved.add(a);
            return a;
        });
        Clock clock = Clock.fixed(LocalDate.of(2026, 2, 20).atTime(9, 0).atZone(SEOUL).toInstant(), SEOUL);
        service = new FlexibleWorkService(assigns, plans, employees, guard, rules, workHours, clock);
    }

    private static void setField(Object o, String name, Object v) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(o, v);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static AssignRequest req(WorkSystem sys, LocalDate from, LocalDate to, String job, String cs, String ce,
            Integer deemed) {
        return new AssignRequest(EMP, sys, from, to, job, cs, ce, deemed, null);
    }

    private static LaborAgreement agreement(long id) {
        LaborAgreement a = mock(LaborAgreement.class);
        when(a.getAgreementId()).thenReturn(id);
        return a;
    }

    private FlexWorkAssign stored(WorkSystem sys, String job, Integer cs, Integer ce, Integer deemed) {
        FlexWorkAssign a = new FlexWorkAssign(EMP, sys, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), job, cs,
                ce, deemed, sys.requiredAgreement() == null ? null : 7L, null, "hr1", LocalDateTime.now());
        setField(a, "flexId", 1L);
        when(assigns.findById(1L)).thenReturn(Optional.of(a));
        when(assigns.findForUpdate(1L)).thenReturn(Optional.of(a));
        return a;
    }

    // ---------------------------------------------------------------- 서면합의 필수

    @Test
    void 유효한_서면합의가_없으면_배정_거부() {
        when(guard.requireValid(eq(AgreementType.SELECTIVE), any(), any(), eq("HQ")))
                .thenThrow(new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "합의서 없음", List.of()));
        assertThatThrownBy(() -> service.assign(req(WorkSystem.SELECTIVE, LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), "GENERAL", null, null, null), "hr1"))
                .isInstanceOf(AttendanceException.class)
                .satisfies(ex -> assertThat(((AttendanceException) ex).status())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(assigns, never()).save(any());
    }

    @Test
    void 서면합의가_있으면_합의서_ID를_함께_저장() {
        LaborAgreement a = agreement(7L);
        when(guard.requireValid(AgreementType.FLEXIBLE_WRITTEN, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                "HQ")).thenReturn(a);
        var v = service.assign(req(WorkSystem.FLEXIBLE_WRITTEN, LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), null, null, null, null), "hr1");
        assertThat(v.agreementId()).isEqualTo(7L);
        assertThat(v.article()).isEqualTo("근로기준법 제51조 제2항");
        assertThat(saved.get(0).getCreatedBy()).isEqualTo("hr1");
    }

    @Test
    void 재량근로도_서면합의를_검사() {
        LaborAgreement a = agreement(8L);
        when(guard.requireValid(eq(AgreementType.DISCRETIONARY), any(), any(), any())).thenReturn(a);
        var v = service.assign(req(WorkSystem.DISCRETIONARY, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 31),
                "INFO_SYSTEM", null, null, 540), "hr1");
        assertThat(v.agreementId()).isEqualTo(8L);
        assertThat(v.deemedMinutes()).isEqualTo(540);
    }

    @Test
    void 취업규칙형_2주_탄력적은_합의서를_요구하지_않는다() {
        when(rules.flexibleAsOf(eq(WorkSystem.FLEXIBLE_SHORT), any()))
                .thenReturn(new Limits(2, 0, 3000, 0, 0, 420, 1800));
        var v = service.assign(req(WorkSystem.FLEXIBLE_SHORT, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 15),
                null, null, null, null), "hr1");
        assertThat(v.agreementId()).isNull();
        verify(guard, never()).requireValid(any(), any(), any(), any());
    }

    @Test
    void 단위기간_한도를_넘으면_합의서_검사_전에_거부() {
        assertThatThrownBy(() -> service.assign(req(WorkSystem.FLEXIBLE_WRITTEN, LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1), null, null, null, null), "hr1"))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("제51조 제2항");
        verify(guard, never()).requireValid(any(), any(), any(), any());
    }

    @Test
    void 재량근로_대상_업무가_아니면_거부() {
        assertThatThrownBy(() -> service.assign(req(WorkSystem.DISCRETIONARY, LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), "SALES", null, null, 480), "hr1"))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("시행령 제31조");
        verify(assigns, never()).save(any());
    }

    @Test
    void 기간이_겹치는_배정은_409() {
        FlexWorkAssign old = stored(WorkSystem.SELECTIVE, "GENERAL", null, null, null);
        when(assigns.findByEmpIdOrderByPeriodStartAsc(EMP)).thenReturn(List.of(old));
        assertThatThrownBy(() -> service.assign(req(WorkSystem.SELECTIVE, LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 20), "GENERAL", null, null, null), "hr1"))
                .isInstanceOf(AttendanceException.class)
                .satisfies(ex -> assertThat(((AttendanceException) ex).status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 코어타임은_선택적에서만() {
        assertThatThrownBy(() -> service.assign(req(WorkSystem.FLEXIBLE_WRITTEN, LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), null, "10:00", "15:00", null), "hr1"))
                .isInstanceOf(AttendanceException.class);
    }

    // ---------------------------------------------------------------- 근무계획

    private static PlanInput day(int d, int sh, int eh, int minutes) {
        LocalDate date = LocalDate.of(2026, 3, d);
        return new PlanInput(date, date.atTime(sh, 0), date.atTime(eh, 0), minutes);
    }

    @Test
    void 선택적_계획은_본인이_저장한다() {
        stored(WorkSystem.SELECTIVE, "GENERAL", 600, 900, null);
        service.savePlan(1L, List.of(day(2, 9, 18, 480), day(3, 10, 15, 300)), SELF);
        verify(plans).deleteAllOfAssign(1L);
        verify(plans).saveAll(any());
    }

    @Test
    void 연결된_합의서가_무효면_계획_저장과_정산을_막는다() {
        FlexWorkAssign assign = stored(WorkSystem.SELECTIVE, "GENERAL", null, null, null);
        doThrow(new AttendanceException(HttpStatus.UNPROCESSABLE_ENTITY, "유효한 서면합의서가 없어요", List.of()))
                .when(guard).requireLinked(assign.getAgreementId(), AgreementType.SELECTIVE,
                        assign.getPeriodStart(), assign.getPeriodEnd(), "HQ");

        assertThatThrownBy(() -> service.savePlan(1L, List.of(day(2, 9, 18, 480)), SELF))
                .hasMessageContaining("서면합의서");
        assertThatThrownBy(() -> service.settle(1L, SELF)).hasMessageContaining("서면합의서");
        verify(plans, never()).deleteAllOfAssign(1L);
        verify(workHours, never()).weekInputs(any());
    }

    @Test
    void 다른_사원의_선택적_계획은_403() {
        stored(WorkSystem.SELECTIVE, "GENERAL", null, null, null);
        assertThatThrownBy(() -> service.savePlan(1L, List.of(day(2, 9, 18, 480)), OTHER))
                .isInstanceOf(AttendanceException.class)
                .satisfies(ex -> assertThat(((AttendanceException) ex).status()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(plans, never()).saveAll(any());
    }

    @Test
    void 코어타임을_덮지_않는_계획은_거부() {
        stored(WorkSystem.SELECTIVE, "GENERAL", 600, 900, null);
        assertThatThrownBy(() -> service.savePlan(1L, List.of(day(2, 11, 18, 360)), SELF))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("코어타임");
        verify(plans, never()).saveAll(any());
    }

    @Test
    void 탄력적_계획은_인사만_정한다() {
        stored(WorkSystem.FLEXIBLE_WRITTEN, null, null, null, null);
        assertThatThrownBy(() -> service.savePlan(1L, List.of(day(2, 9, 18, 480)), SELF))
                .isInstanceOf(AttendanceException.class)
                .satisfies(ex -> assertThat(((AttendanceException) ex).status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 탄력적_특정일_상한_초과_계획은_422() {
        stored(WorkSystem.FLEXIBLE_WRITTEN, null, null, null, null);
        assertThatThrownBy(() -> service.savePlan(1L, List.of(day(2, 8, 20, 601)), HR))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("특정한 날")
                .satisfies(ex -> assertThat(((AttendanceException) ex).status())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        verify(plans, never()).saveAll(any());
    }

    @Test
    void 기간_밖_날짜와_휴게_포함_초과_분은_400() {
        stored(WorkSystem.FLEXIBLE_WRITTEN, null, null, null, null);
        LocalDate out = LocalDate.of(2026, 4, 1);
        assertThatThrownBy(() -> service.savePlan(1L,
                List.of(new PlanInput(out, out.atTime(9, 0), out.atTime(18, 0), 480)), HR))
                .hasMessageContaining("기간 밖");
        assertThatThrownBy(() -> service.savePlan(1L, List.of(day(2, 9, 10, 61)), HR))
                .hasMessageContaining("근로분");
    }

    @Test
    void 재량근로는_계획이_없다() {
        stored(WorkSystem.DISCRETIONARY, "RND", null, null, 480);
        assertThatThrownBy(() -> service.savePlan(1L, List.of(), HR)).hasMessageContaining("제58조 제3항");
    }

    // ---------------------------------------------------------------- 정산(집계 엔진 연결)

    private void actualEveryDay(int minutes) {
        when(workHours.weekInputs(any())).thenAnswer(inv -> {
            LocalDate d = inv.getArgument(0);
            return new WorkHoursService.WeekInputs(d.with(DayOfWeek.MONDAY), Set.of(), Map.of());
        });
        when(workHours.computeWeek(eq(EMP), any())).thenAnswer(inv -> {
            WorkHoursService.WeekInputs w = inv.getArgument(1);
            List<DailyWorkSummaryRow> rows = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate d = w.monday().plusDays(i);
                rows.add(new DailyWorkSummaryRow(EMP, d, "WORKDAY", 1L, 480, null, 0, d.atTime(9, 0),
                        d.atTime(9, 0).plusMinutes(minutes), 0, minutes, minutes, 0, 0, 0, 0, 0, null, null, null,
                        null, null, "PREVIEW", null));
            }
            return rows;
        });
    }

    @Test
    void 선택적_정산은_집계_엔진_실근로로_총량_초과분을_연장으로() {
        stored(WorkSystem.SELECTIVE, "GENERAL", null, null, null);
        actualEveryDay(300);
        Settlement s = service.settle(1L, SELF);
        int limit = FlexibleWorkCalculator.totalLimit(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 1800);
        assertThat(s.days()).hasSize(31);
        assertThat(s.actualMinutes()).isEqualTo(31 * 300);
        assertThat(s.totalLimitMinutes()).isEqualTo(limit);
        assertThat(s.overtimeMinutes()).isEqualTo(31 * 300 - limit);
        assertThat(s.check()).isEqualTo(Check.OK);
    }

    @Test
    void 탄력적과_선택적_정산에_휴일근로_기준_초과분을_포함한다() {
        actualEveryDay(0);
        LocalDate sunday = LocalDate.of(2026, 3, 1);
        var holiday = new DailyWorkSummaryRow(EMP, sunday, "HOLIDAY", 1L, 0, null, 0, sunday.atTime(9, 0),
                sunday.atTime(17, 20), 0, 500, 0, 0, 0, 0, 420, 80, null, null, null, null, null, "PREVIEW", null);
        when(workHours.computeWeek(eq(EMP), any())).thenAnswer(inv -> {
            WorkHoursService.WeekInputs week = inv.getArgument(1);
            return week.monday().equals(sunday.minusDays(6)) ? List.of(holiday) : List.of();
        });

        stored(WorkSystem.SELECTIVE, "GENERAL", null, null, null);
        assertThat(service.settle(1L, SELF).holidayMinutes()).isEqualTo(500);

        stored(WorkSystem.FLEXIBLE_WRITTEN, null, null, null, null);
        assertThat(service.settle(1L, HR).holidayMinutes()).isEqualTo(500);
    }

    @Test
    void 다른_사원의_정산은_403() {
        stored(WorkSystem.SELECTIVE, "GENERAL", null, null, null);
        assertThatThrownBy(() -> service.settle(1L, OTHER))
                .isInstanceOf(AttendanceException.class)
                .satisfies(ex -> assertThat(((AttendanceException) ex).status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 탄력적_정산은_계획을_넘는_분을_연장으로() {
        stored(WorkSystem.FLEXIBLE_WRITTEN, null, null, null, null);
        actualEveryDay(300);
        when(plans.findByFlexIdOrderByWorkDateAsc(1L)).thenReturn(List.of());
        Settlement s = service.settle(1L, HR);
        assertThat(s.days()).hasSize(31);
        assertThat(s.workFrom()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(s.workTo()).isEqualTo(LocalDate.of(2026, 3, 31));
        // 계산 규칙 자체는 FlexibleWorkFixtureTest 가 정답과 대조한다. 여기서는 같은 계산기 결과를 그대로 돌려주는지만 본다.
        var expected = FlexibleWorkCalculator.settleFlexible(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                s.days().stream().map(d -> new FlexibleWorkCalculator.ActualDay(d.date(), 0, 300)).toList(),
                FAKE_FLEX);
        assertThat(s.overtimeMinutes()).isEqualTo(expected.periodOvertime());
    }
}
