package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.attendance.AttendancePunch.Check;
import com.glassehr.attendance.AttendancePunch.Type;
import com.glassehr.attendance.PunchDtos.PunchRequest;
import com.glassehr.attendance.PunchDtos.PunchView;
import com.glassehr.attendance.PunchDtos.TodayView;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** 출퇴근 기록 서비스. 사원·시각은 가짜 값(2099년). */
class PunchServiceTest {

    /** 2099-03-02 09:00:30 KST = 00:00:30 UTC */
    private static final Instant NOW = Instant.parse("2099-03-02T00:00:30Z");
    private static final LocalDate TODAY = LocalDate.of(2099, 3, 2);

    private AttendancePunchRepository punches;
    private EmployeeRepository employees;
    private Employee emp;
    private final List<AttendancePunch> stored = new ArrayList<>();
    private PunchService service;

    @BeforeEach
    void setUp() {
        punches = mock(AttendancePunchRepository.class);
        employees = mock(EmployeeRepository.class);
        emp = mock(Employee.class);
        when(emp.getHireDate()).thenReturn(LocalDate.of(2099, 1, 1));
        when(employees.findForUpdate(5L)).thenReturn(Optional.of(emp));
        when(punches.findByEmpIdAndWorkDateOrderByPunchedAtAscPunchIdAsc(any(), any()))
                .thenAnswer(inv -> stored.stream()
                        .filter(p -> p.getEmpId().equals(inv.getArgument(0)) && p.getWorkDate().equals(inv.getArgument(1)))
                        .toList());
        when(punches.save(any())).thenAnswer(inv -> {
            AttendancePunch p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "punchId", (long) stored.size() + 1);
            stored.add(p);
            return p;
        });
        service = new PunchService(punches, employees, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 서버_시각으로_기록하고_단말_시각은_참고로만_남긴다() {
        // 단말 시계가 하루 늦게 맞춰져 있어도 기록 시각·근무일은 서버 기준(Asia/Seoul)이다.
        OffsetDateTime client = OffsetDateTime.parse("2099-03-01T08:00:00+09:00");
        PunchView v = service.punch(5L, "emp1", new PunchRequest(Type.IN, client));
        assertThat(v.punchedAt()).isEqualTo(OffsetDateTime.parse("2099-03-02T09:00:30+09:00"));
        assertThat(v.workDate()).isEqualTo(TODAY);
        assertThat(v.check()).isNull();
        AttendancePunch saved = stored.get(0);
        assertThat(saved.getPunchedAt()).isEqualTo(LocalDateTime.of(2099, 3, 2, 9, 0, 30));
        assertThat(saved.getClientTime()).isEqualTo(LocalDateTime.of(2099, 3, 1, 8, 0));
        assertThat(saved.getCreatedBy()).isEqualTo("emp1");
        assertThat(saved.getSource()).isEqualTo(AttendancePunch.Source.WEB);
    }

    @Test
    void 자정_직전_UTC_시각도_서울_날짜로_근무일을_정한다() {
        // 2099-03-01T15:30Z = 2099-03-02 00:30 KST
        service = new PunchService(punches, employees, Clock.fixed(Instant.parse("2099-03-01T15:30:00Z"), ZoneOffset.UTC));
        assertThat(service.punch(5L, "emp1", new PunchRequest(Type.IN, null)).workDate()).isEqualTo(TODAY);
    }

    @Test
    void 중복_출근도_저장하고_표시한다() {
        service.punch(5L, "emp1", new PunchRequest(Type.IN, null));
        PunchView second = service.punch(5L, "emp1", new PunchRequest(Type.IN, null));
        assertThat(second.check()).isEqualTo(Check.DUPLICATE_IN);
        assertThat(second.checkMessage()).contains("관리자 확인");
        assertThat(stored).hasSize(2);
    }

    @Test
    void 출근_없는_퇴근도_저장하고_표시한다() {
        PunchView v = service.punch(5L, "emp1", new PunchRequest(Type.OUT, null));
        assertThat(v.check()).isEqualTo(Check.OUT_WITHOUT_IN);
        assertThat(stored).hasSize(1);
    }

    @Test
    void 사원_행을_잠근_뒤_같은_날_기록을_읽는다() {
        service.punch(5L, "emp1", new PunchRequest(Type.IN, null));
        InOrder o = inOrder(employees, punches);
        o.verify(employees).findForUpdate(5L);
        o.verify(punches).findByEmpIdAndWorkDateOrderByPunchedAtAscPunchIdAsc(5L, TODAY);
        o.verify(punches).save(any());
    }

    @Test
    void 사원과_연결되지_않은_계정은_403() {
        assertThatThrownBy(() -> service.punch(null, "admin", new PunchRequest(Type.IN, null)))
                .isInstanceOfSatisfying(AttendanceException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.today(null)).isInstanceOf(AttendanceException.class);
        verify(punches, never()).save(any());
    }

    @Test
    void 입사_전이나_퇴직일_다음_날부터는_기록하지_않는다() {
        when(emp.getHireDate()).thenReturn(TODAY.plusDays(1));
        assertThatThrownBy(() -> service.punch(5L, "emp1", new PunchRequest(Type.IN, null)))
                .isInstanceOfSatisfying(AttendanceException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        when(emp.getHireDate()).thenReturn(TODAY);
        when(emp.getRetireDate()).thenReturn(TODAY.minusDays(1));
        assertThatThrownBy(() -> service.punch(5L, "emp1", new PunchRequest(Type.IN, null)))
                .isInstanceOf(AttendanceException.class);
        verify(punches, never()).save(any());
        // 퇴직일 당일까지는 재직이다.
        when(emp.getRetireDate()).thenReturn(TODAY);
        assertThat(service.punch(5L, "emp1", new PunchRequest(Type.OUT, null))).isNotNull();
    }

    @Test
    void 없는_사원은_404() {
        assertThatThrownBy(() -> service.punch(9L, "x", new PunchRequest(Type.IN, null)))
                .isInstanceOfSatisfying(AttendanceException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 오늘_요약은_첫_출근_시각과_경과_시간을_준다() {
        stored.add(new AttendancePunch(5L, Type.IN, LocalDateTime.of(2099, 3, 2, 7, 30), null, null,
                AttendancePunch.Source.WEB, "emp1"));
        ReflectionTestUtils.setField(stored.get(0), "punchId", 1L);
        stored.add(new AttendancePunch(5L, Type.IN, LocalDateTime.of(2099, 3, 1, 9, 0), null, null,
                AttendancePunch.Source.WEB, "emp1"));
        TodayView t = service.today(5L);
        assertThat(t.serverNow()).isEqualTo(OffsetDateTime.parse("2099-03-02T09:00:30+09:00"));
        assertThat(t.workDate()).isEqualTo(TODAY);
        assertThat(t.firstInAt()).isEqualTo(OffsetDateTime.parse("2099-03-02T07:30:00+09:00"));
        assertThat(t.working()).isTrue();
        assertThat(t.elapsedMinutes()).isEqualTo(90L);
        assertThat(t.punches()).hasSize(1);
    }

    @Test
    void 원시_기록은_추가만_된다_저장소에_삭제가_없고_엔티티는_고칠_수_없다() {
        assertThat(Arrays.stream(AttendancePunchRepository.class.getMethods()).map(Method::getName))
                .containsExactlyInAnyOrder("save", "findByEmpIdAndWorkDateOrderByPunchedAtAscPunchIdAsc")
                .noneMatch(n -> n.startsWith("delete"));
        assertThat(Arrays.stream(AttendancePunch.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName))
                .noneMatch(n -> n.startsWith("set"));
        assertThat(Arrays.stream(AttendancePunch.class.getDeclaredFields())
                .map(f -> f.getAnnotation(jakarta.persistence.Column.class)))
                .allSatisfy(c -> assertThat(c.updatable()).isFalse());
    }
}
