package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.WorkPatternDtos.AssignRequest;
import com.glassehr.attendance.WorkPatternDtos.AssignmentView;
import com.glassehr.attendance.WorkPatternDtos.WorkPatternRequest;
import com.glassehr.attendance.WorkPatternDtos.WorkPatternView;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.legalrate.LegalRateException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 근무유형 서비스. 검사 기준값은 {@link WorkPatternLegalLimits} 목으로 넣는 <b>임의 값이며 법령 수치가 아니다</b>.
 */
class WorkPatternServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    /** 임의 기준값(법령 수치 아님): 1일 7시간, 1주 30시간, 3시간→20분, 6시간→45분, 주휴일 1일. */
    private static final WorkPatternRules.Limits FAKE = new WorkPatternRules.Limits(420, 1800, 180, 20, 360, 45, 1);

    private WorkPatternRepository patterns;
    private EmpWorkPatternRepository assignments;
    private EmployeeRepository employees;
    private WorkPatternLegalLimits limits;
    private WorkPatternService service;
    private final List<EmpWorkPattern> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        patterns = mock(WorkPatternRepository.class);
        assignments = mock(EmpWorkPatternRepository.class);
        employees = mock(EmployeeRepository.class);
        limits = mock(WorkPatternLegalLimits.class);
        when(limits.asOf(any())).thenReturn(FAKE);
        when(patterns.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), "patternId", 10L));
        when(assignments.save(any())).thenAnswer(inv -> {
            EmpWorkPattern a = inv.getArgument(0);
            saved.add(a);
            return a;
        });
        Clock clock = Clock.fixed(TODAY.atTime(9, 0).atZone(SEOUL).toInstant(), SEOUL);
        service = new WorkPatternService(patterns, assignments, employees, limits, clock);
    }

    private static <T> T withId(T entity, String field, Object id) {
        try {
            Field f = entity.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 09:00~16:45, 휴게 12:00부터 45분, 월~금 근무·토 휴무·일 주휴 → 1일 7시간, 1주 35시간(임의 한도 30시간 초과). */
    private static WorkPatternRequest req(String code, String days) {
        return new WorkPatternRequest(code, "주간", 540, 1005, 720, 45, days, null, null);
    }

    /** 1일 6시간(09:00~15:45, 휴게 45분) x 5일 = 30시간. */
    private static WorkPatternRequest okReq(String code) {
        return new WorkPatternRequest(code, "주간", 540, 945, 720, 45, "WWWWWOH", true, " 비고 ");
    }

    private static WorkPattern pattern(long id, boolean active) {
        WorkPatternRequest r = okReq("DAY");
        WorkPattern p = new WorkPattern("DAY", "주간", r.spec(), new WorkPatternRules.Result(360, 1800, List.of()),
                active, null, LocalDateTime.of(2026, 1, 1, 0, 0));
        return withId(p, "patternId", id);
    }

    private static Employee employee(LocalDate hire, LocalDate retire) {
        Employee e = mock(Employee.class);
        when(e.getHireDate()).thenReturn(hire);
        when(e.getRetireDate()).thenReturn(retire);
        return e;
    }

    /** 사원 5번. 목 생성과 스터빙을 따로 해야 한다(thenReturn 안에서 스터빙하면 Mockito 오류). */
    private void givenEmployee(LocalDate hire, LocalDate retire) {
        Employee e = employee(hire, retire);
        when(employees.findForUpdate(5)).thenReturn(Optional.of(e));
    }

    @Test
    void 기준_안이면_소정근로시간을_계산해_저장한다() {
        WorkPatternView v = service.create(okReq("DAY"));
        assertThat(v.patternId()).isEqualTo(10L);
        assertThat(v.dailyContractMinutes()).isEqualTo(360);
        assertThat(v.weeklyContractMinutes()).isEqualTo(1800);
        assertThat(v.note()).isEqualTo("비고");
        assertThat(v.active()).isTrue();
        verify(limits).asOf(TODAY);
    }

    @Test
    void 주_한도를_넘으면_422로_거부하고_저장하지_않는다() {
        assertThatThrownBy(() -> service.create(req("DAY", "WWWWWOH")))
                .isInstanceOfSatisfying(AttendanceException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.violations()).extracting(WorkPatternRules.Violation::code)
                            .contains(WorkPatternRules.Code.WEEKLY_LIMIT_EXCEEDED);
                });
        verify(patterns, never()).save(any());
    }

    @Test
    void 주휴일이_없으면_거부() {
        WorkPatternRequest r = new WorkPatternRequest("DAY", "주간", 540, 945, 720, 45, "WWWWWOO", null, null);
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOfSatisfying(AttendanceException.class, e -> assertThat(e.violations())
                        .extracting(WorkPatternRules.Violation::code)
                        .contains(WorkPatternRules.Code.WEEKLY_HOLIDAY_REQUIRED));
    }

    @Test
    void 기준값이_없으면_저장하지_않는다() {
        LegalRateException missing = mock(LegalRateException.class);
        when(limits.asOf(any())).thenThrow(missing);
        assertThatThrownBy(() -> service.create(okReq("DAY"))).isSameAs(missing);
        verify(patterns, never()).save(any());
    }

    @Test
    void 코드가_겹치면_409() {
        when(patterns.existsByPatternCode("DAY")).thenReturn(true);
        assertThatThrownBy(() -> service.create(okReq("DAY")))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 배정된_유형은_시각을_바꿀_수_없고_이름은_바꿀_수_있다() {
        WorkPattern p = pattern(3, true);
        when(patterns.findForUpdate(3)).thenReturn(Optional.of(p));
        when(assignments.existsByPatternId(3)).thenReturn(true);

        WorkPatternRequest changed = new WorkPatternRequest("DAY", "주간", 600, 1005, 780, 45, "WWWWWOH", true, null);
        assertThatThrownBy(() -> service.update(3, changed))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));

        WorkPatternRequest renamed = new WorkPatternRequest("DAY", "주간 A", 540, 945, 720, 45, "WWWWWOH", false, null);
        WorkPatternView v = service.update(3, renamed);
        assertThat(v.patternName()).isEqualTo("주간 A");
        assertThat(v.active()).isFalse();
        assertThat(v.assigned()).isTrue();
    }

    @Test
    void 배정_안_된_유형은_시각을_바꾸면_다시_검사한다() {
        WorkPattern p = pattern(3, true);
        when(patterns.findForUpdate(3)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.update(3, req("DAY", "WWWWWOH")))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        WorkPatternRequest shorter = new WorkPatternRequest("DAY", "주간", 540, 885, 720, 45, "WWWWWOH", true, null);
        WorkPatternView v = service.update(3, shorter);
        assertThat(v.dailyContractMinutes()).isEqualTo(300);
        assertThat(v.weeklyContractMinutes()).isEqualTo(1500);
    }

    @Test
    void 코드는_바꿀_수_없다() {
        when(patterns.findForUpdate(3)).thenReturn(Optional.of(pattern(3, true)));
        assertThatThrownBy(() -> service.update(3, okReq("NIGHT")))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 배정은_적용_시작일_기준값으로_검사하고_이전_배정을_닫는다() {
        Employee e = employee(LocalDate.of(2026, 1, 2), null);
        when(employees.findForUpdate(5)).thenReturn(Optional.of(e));
        when(patterns.findForUpdate(3)).thenReturn(Optional.of(pattern(3, true)));
        EmpWorkPattern prev = new EmpWorkPattern(5, 3, LocalDate.of(2026, 1, 2), null, "hr", LocalDateTime.now());
        when(assignments.findByEmpIdOrderByValidFromAsc(5)).thenReturn(List.of(prev));

        LocalDate from = LocalDate.of(2027, 1, 1);
        AssignmentView v = service.assign(5, new AssignRequest(3L, from, "부서 이동"), "hr1");

        assertThat(prev.getValidTo()).isEqualTo(from);
        assertThat(v.validFrom()).isEqualTo(from);
        assertThat(v.validTo()).isNull();
        assertThat(v.patternCode()).isEqualTo("DAY");
        assertThat(saved).singleElement().satisfies(a -> {
            assertThat(a.getCreatedBy()).isEqualTo("hr1");
            assertThat(a.getAssignReason()).isEqualTo("부서 이동");
        });
        verify(limits).asOf(from);
    }

    @Test
    void 마지막_배정_시작일_이전이나_같은_날에는_배정할_수_없다() {
        givenEmployee(LocalDate.of(2026, 1, 2), null);
        when(patterns.findForUpdate(3)).thenReturn(Optional.of(pattern(3, true)));
        EmpWorkPattern prev = new EmpWorkPattern(5, 3, LocalDate.of(2026, 6, 1), null, "hr", LocalDateTime.now());
        when(assignments.findByEmpIdOrderByValidFromAsc(5)).thenReturn(List.of(prev));

        for (LocalDate d : List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 31))) {
            assertThatThrownBy(() -> service.assign(5, new AssignRequest(3L, d, null), "hr"))
                    .as(d.toString()).isInstanceOfSatisfying(AttendanceException.class,
                            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThat(prev.getValidTo()).isNull();
        assertThat(saved).isEmpty();
    }

    @Test
    void 입사일_전이나_퇴직일_뒤에는_배정할_수_없다() {
        givenEmployee(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 9, 30));
        when(patterns.findForUpdate(3)).thenReturn(Optional.of(pattern(3, true)));

        assertThatThrownBy(() -> service.assign(5, new AssignRequest(3L, LocalDate.of(2026, 3, 1), null), "hr"))
                .isInstanceOf(AttendanceException.class);
        assertThatThrownBy(() -> service.assign(5, new AssignRequest(3L, LocalDate.of(2026, 10, 1), null), "hr"))
                .isInstanceOf(AttendanceException.class);
        // 입사일·퇴직일 당일은 된다
        assertThat(service.assign(5, new AssignRequest(3L, LocalDate.of(2026, 3, 2), null), "hr").validFrom())
                .isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    void 사용_중지된_유형이나_없는_사원은_배정할_수_없다() {
        givenEmployee(LocalDate.of(2026, 1, 2), null);
        when(patterns.findForUpdate(3)).thenReturn(Optional.of(pattern(3, false)));
        assertThatThrownBy(() -> service.assign(5, new AssignRequest(3L, TODAY, null), "hr"))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));

        when(employees.findForUpdate(anyLong())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(9, new AssignRequest(3L, TODAY, null), "hr"))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 배정_시점_기준값으로_위반이면_배정하지_않는다() {
        givenEmployee(LocalDate.of(2026, 1, 2), null);
        when(patterns.findForUpdate(3)).thenReturn(Optional.of(pattern(3, true)));
        LocalDate from = LocalDate.of(2027, 1, 1);
        // 임의로 더 엄격한 한도(1주 25시간)가 적용되는 날
        when(limits.asOf(from)).thenReturn(new WorkPatternRules.Limits(420, 1500, 180, 20, 360, 45, 1));
        assertThatThrownBy(() -> service.assign(5, new AssignRequest(3L, from, null), "hr"))
                .isInstanceOfSatisfying(AttendanceException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(saved).isEmpty();
    }

    @Test
    void 기준일_배정을_찾는다() {
        when(employees.existsById(5L)).thenReturn(true);
        when(patterns.findById(3L)).thenReturn(Optional.of(pattern(3, true)));
        EmpWorkPattern a = new EmpWorkPattern(5, 3, LocalDate.of(2026, 1, 2), null, "hr", LocalDateTime.now());
        a.closeAt(LocalDate.of(2026, 7, 1));
        EmpWorkPattern b = new EmpWorkPattern(5, 3, LocalDate.of(2026, 7, 1), null, "hr", LocalDateTime.now());
        when(assignments.findByEmpIdOrderByValidFromAsc(5)).thenReturn(List.of(a, b));

        assertThat(service.effective(5, LocalDate.of(2026, 6, 30))).get()
                .extracting(AssignmentView::validTo).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(service.effective(5, LocalDate.of(2026, 7, 1))).get()
                .extracting(AssignmentView::validFrom).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(service.effective(5, LocalDate.of(2026, 1, 1))).isEmpty();
        assertThat(service.history(5)).hasSize(2);
    }

    @Test
    void 오늘은_서울_기준() {
        Clock utcLate = Clock.fixed(Instant.parse("2026-11-12T15:30:00Z"), ZoneId.of("UTC"));
        WorkPatternService s = new WorkPatternService(patterns, assignments, employees, limits, utcLate);
        assertThat(s.today()).isEqualTo(LocalDate.of(2026, 11, 13));
    }
}
