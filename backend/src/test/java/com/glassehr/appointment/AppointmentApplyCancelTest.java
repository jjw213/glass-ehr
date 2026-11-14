package com.glassehr.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.appointment.AppointmentDtos.AppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.appointment.AppointmentDtos.AssignmentView;
import com.glassehr.appointment.AppointmentDtos.CancelRequest;
import com.glassehr.appointment.AppointmentDtos.LeavePeriodView;
import com.glassehr.code.CodeGroups;
import com.glassehr.code.CommonCode;
import com.glassehr.code.CommonCodeId;
import com.glassehr.code.CommonCodeRepository;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeDtos.EmployeeUpdateRequest;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.org.OrgDept;
import com.glassehr.org.OrgDeptRepository;
import jakarta.persistence.Column;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

/**
 * #59 인사발령 2: 확정 발령의 사원 정보 반영, 기준일 조회(같은 날 여러 발령·미래 발령), 취소 발령, 휴직 기간 조회.
 * 저장소는 메모리 가짜. 법정 수치 계산은 없다.
 */
class AppointmentApplyCancelTest {

    /** 오늘(서울) = 2026-11-13. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate HIRE = LocalDate.of(2024, 1, 2);
    private static final LocalDate PAST = LocalDate.of(2026, 11, 1);
    private static final LocalDate FUTURE = LocalDate.of(2026, 12, 1);
    private static final long DEPT_A = 10;
    private static final long DEPT_B = 20;
    private static final long DEPT_C = 30;

    private final Map<Long, HrAppointment> store = new HashMap<>();
    private final Map<Long, HrAppointmentCancel> links = new HashMap<>();
    private final Map<CommonCodeId, CommonCode> codeStore = new HashMap<>();
    private final Map<Long, OrgDept> deptStore = new HashMap<>();
    private final Map<Long, Employee> emps = new HashMap<>();
    private long nextId = 1;
    private long nextNo = 1;
    /** confirmedAt 이 같은 시각이라 확정 순서를 보이게 하려고 확정할 때마다 1초씩 늘린다. */
    private long tick = 0;

    private HrAppointmentRepository appointments;
    private HrAppointmentCancelRepository cancels;
    private EmployeeRepository employees;
    private CommonCodeRepository codes;
    private OrgDeptRepository depts;
    private AppointmentNoGenerator apptNos;
    private AppointmentService service;

    private Employee emp(long id) {
        Employee e = mock(Employee.class);
        when(e.getEmpId()).thenReturn(id);
        when(e.getHireDate()).thenReturn(HIRE);
        when(e.getCurrentDeptId()).thenReturn(DEPT_A);
        when(e.getJobGradeCode()).thenReturn("STAFF");
        when(e.getJobTitleCode()).thenReturn("MEMBER");
        when(e.getEmpStatus()).thenReturn("ACTIVE");
        emps.put(id, e);
        return e;
    }

    private void code(String group, String code) {
        CommonCode c = mock(CommonCode.class);
        when(c.isActive()).thenReturn(true);
        codeStore.put(new CommonCodeId(group, code), c);
    }

    private void dept(long id) {
        OrgDept d = mock(OrgDept.class);
        when(d.getDeptCode()).thenReturn("D" + id);
        when(d.getValidFrom()).thenReturn(LocalDate.of(2000, 1, 1));
        deptStore.put(id, d);
    }

    private static List<Long> ids(Iterable<Long> it) {
        return StreamSupport.stream(it.spliterator(), false).toList();
    }

    @BeforeEach
    void setUp() {
        appointments = mock(HrAppointmentRepository.class);
        when(appointments.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<Long>getArgument(0))));
        when(appointments.findForUpdate(anyLong())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<Long>getArgument(0))));
        when(appointments.findEmpIdByApptId(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.<Long>getArgument(0))).map(HrAppointment::getEmpId));
        when(appointments.saveAndFlush(any())).thenAnswer(inv -> {
            HrAppointment a = inv.getArgument(0);
            if (a.getApptId() == null) {
                ReflectionTestUtils.setField(a, "apptId", nextId++);
            }
            if (a.isConfirmed()) {
                // 확정 순서가 드러나게 확정일시를 1초씩 벌린다
                ReflectionTestUtils.setField(a, "confirmedAt", a.getConfirmedAt().plusSeconds(tick++));
            }
            store.put(a.getApptId(), a);
            return a;
        });
        doAnswer(inv -> store.remove(inv.<HrAppointment>getArgument(0).getApptId())).when(appointments).delete(any());
        when(appointments.findByEmpIdOrderByEffectiveDateAscApptIdAsc(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            return sorted().stream().filter(a -> a.getEmpId() == id).toList();
        });
        when(appointments.findAllByOrderByEffectiveDateAscApptIdAsc()).thenAnswer(inv -> sorted());

        cancels = mock(HrAppointmentCancelRepository.class);
        when(cancels.existsById(anyLong())).thenAnswer(inv -> links.containsKey(inv.<Long>getArgument(0)));
        when(cancels.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(links.get(inv.<Long>getArgument(0))));
        when(cancels.findByOriginalApptId(anyLong())).thenAnswer(inv -> links.values().stream()
                .filter(l -> l.getOriginalApptId() == inv.<Long>getArgument(0)).findFirst());
        when(cancels.findAllById(any())).thenAnswer(inv -> ids(inv.<Iterable<Long>>getArgument(0)).stream()
                .map(links::get).filter(l -> l != null).toList());
        when(cancels.findAll()).thenAnswer(inv -> new ArrayList<>(links.values()));
        when(cancels.saveAndFlush(any())).thenAnswer(inv -> {
            HrAppointmentCancel l = inv.getArgument(0);
            links.put(l.getCancelApptId(), l);
            return l;
        });
        doAnswer(inv -> links.remove(inv.<Long>getArgument(0))).when(cancels).deleteById(anyLong());

        employees = mock(EmployeeRepository.class);
        when(employees.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(emps.get(inv.<Long>getArgument(0))));
        when(employees.findForUpdate(anyLong())).thenAnswer(inv -> Optional.ofNullable(emps.get(inv.<Long>getArgument(0))));
        when(employees.applyAppointment(anyLong(), any(), any(), any(), any(), any())).thenReturn(1);
        when(employees.findAllById(any())).thenAnswer(inv -> ids(inv.<Iterable<Long>>getArgument(0)).stream()
                .map(emps::get).filter(e -> e != null).toList());

        codes = mock(CommonCodeRepository.class);
        when(codes.findById(any())).thenAnswer(inv -> Optional.ofNullable(codeStore.get(inv.<CommonCodeId>getArgument(0))));
        for (AppointmentType t : AppointmentType.values()) {
            code(CodeGroups.APPOINTMENT_TYPE, t.code());
        }
        code(CodeGroups.JOB_GRADE, "STAFF");
        code(CodeGroups.JOB_GRADE, "MANAGER");
        code(CodeGroups.JOB_TITLE, "MEMBER");
        code(CodeGroups.JOB_TITLE, "TEAM_LEADER");

        depts = mock(OrgDeptRepository.class);
        when(depts.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(deptStore.get(inv.<Long>getArgument(0))));
        dept(DEPT_A);
        dept(DEPT_B);
        dept(DEPT_C);

        apptNos = mock(AppointmentNoGenerator.class);
        when(apptNos.next(anyInt())).thenAnswer(inv -> AppointmentNoGenerator.format(inv.<Integer>getArgument(0), nextNo++));

        service = serviceOn(CLOCK);
        emp(1);
    }

    private AppointmentService serviceOn(Clock clock) {
        return new AppointmentService(appointments, cancels, employees, codes, depts, apptNos, clock);
    }

    private List<HrAppointment> sorted() {
        List<HrAppointment> all = new ArrayList<>(store.values());
        all.sort(java.util.Comparator.comparing(HrAppointment::getEffectiveDate).thenComparing(HrAppointment::getApptId));
        return all;
    }

    private static AppointmentRequest req(String type, LocalDate date, Long dept, String grade, String title) {
        return new AppointmentRequest(type, date, dept, grade, title, "조직 개편에 따른 발령", null, null);
    }

    private static AppointmentRequest leave(LocalDate date, LocalDate start, LocalDate end) {
        return new AppointmentRequest("LEAVE", date, null, null, null, "육아휴직 신청(남녀고용평등법 제19조)", start, end);
    }

    private long confirmed(AppointmentRequest r) {
        return service.confirm(service.create(1, r).apptId()).apptId();
    }

    private AssignmentView on(LocalDate d) {
        return service.assignmentOn(1, d);
    }

    private static void assertStatus(Runnable r, HttpStatus status, String messagePart) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(AppointmentException.class, ex -> {
            assertThat(ex.status()).isEqualTo(status);
            assertThat(ex.getMessage()).contains(messagePart);
        });
    }

    private static final CancelRequest CANCEL = new CancelRequest("발령 착오로 취소");

    // ---------------------------------------------------------------- 기준일 조회·반영

    @Test
    void 발령이_없으면_사원_행_값이고_입사일_전은_404() {
        assertThat(on(HIRE)).isEqualTo(new AssignmentView(1, HIRE, DEPT_A, "STAFF", "MEMBER", EmpStatus.ACTIVE));
        assertStatus(() -> on(HIRE.minusDays(1)), HttpStatus.NOT_FOUND, "입사일");
    }

    @Test
    void 같은_날_여러_발령은_확정_순서대로_쌓이고_오늘_값이_사원에_반영된다() {
        long t = confirmed(req("TRANSFER", PAST, DEPT_B, null, null));
        AppointmentView p = service.confirm(service.create(1, req("PROMOTION", PAST, null, "MANAGER", null)).apptId());
        // 승진의 이전 값은 같은 날 먼저 확정된 전보의 이후 값
        assertThat(p.before().deptId()).isEqualTo(DEPT_B);
        AppointmentView ti = service.confirm(service.create(1, req("TITLE_CHANGE", PAST, null, null, "TEAM_LEADER")).apptId());
        assertThat(ti.before()).isEqualTo(new HrAppointment.Snapshot(DEPT_B, "MANAGER", "MEMBER", EmpStatus.ACTIVE));

        assertThat(on(PAST.minusDays(1))).isEqualTo(
                new AssignmentView(1, PAST.minusDays(1), DEPT_A, "STAFF", "MEMBER", EmpStatus.ACTIVE));
        assertThat(on(PAST)).isEqualTo(new AssignmentView(1, PAST, DEPT_B, "MANAGER", "TEAM_LEADER", EmpStatus.ACTIVE));

        // 사원 현재 값은 확정할 때마다 오늘 기준 이력 값으로 바뀐다(저장소 반영 쿼리로만)
        verify(employees).applyAppointment(eq(1L), eq(DEPT_B), eq("STAFF"), eq("MEMBER"), eq("ACTIVE"), any());
        verify(employees).applyAppointment(eq(1L), eq(DEPT_B), eq("MANAGER"), eq("MEMBER"), eq("ACTIVE"), any());
        verify(employees).applyAppointment(eq(1L), eq(DEPT_B), eq("MANAGER"), eq("TEAM_LEADER"), eq("ACTIVE"), any());
        assertThat(t).isPositive();
    }

    @Test
    void 미확정_발령은_기준일_값에_들어가지_않는다() {
        service.create(1, req("TRANSFER", PAST, DEPT_B, null, null));
        assertThat(on(PAST).deptId()).isEqualTo(DEPT_A);
        verify(employees, never()).applyAppointment(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void 미래_발령은_그날부터_반영되고_사원_값은_그날_매일_반영에서_바뀐다() {
        confirmed(req("TRANSFER", FUTURE, DEPT_B, null, null));
        // 오늘 기준 값은 사원 행 값과 같아 쓰지 않는다
        verify(employees, never()).applyAppointment(anyLong(), any(), any(), any(), any(), any());
        assertThat(on(FUTURE.minusDays(1)).deptId()).isEqualTo(DEPT_A);
        assertThat(on(FUTURE).deptId()).isEqualTo(DEPT_B);

        // 하루 전 반영: 그대로
        assertThat(serviceOn(Clock.fixed(Instant.parse("2026-11-30T01:00:00Z"), ZoneId.of("Asia/Seoul")))
                .syncEmployee(1)).isFalse();
        verify(employees, never()).applyAppointment(anyLong(), any(), any(), any(), any(), any());
        // 발령일(서울 00:05) 반영: 바뀜
        assertThat(serviceOn(Clock.fixed(Instant.parse("2026-11-30T15:05:00Z"), ZoneId.of("Asia/Seoul")))
                .syncEmployee(1)).isTrue();
        verify(employees).applyAppointment(eq(1L), eq(DEPT_B), eq("STAFF"), eq("MEMBER"), eq("ACTIVE"), any());
    }

    @Test
    void 미래_휴직은_그날부터_휴직_상태다() {
        LocalDate start = LocalDate.of(2027, 1, 1);
        confirmed(leave(start, start, LocalDate.of(2027, 12, 31)));
        assertThat(on(start.minusDays(1)).empStatus()).isEqualTo(EmpStatus.ACTIVE);
        assertThat(on(start).empStatus()).isEqualTo(EmpStatus.ON_LEAVE);
        // 휴직 중 전보는 재직상태를 그대로 옮긴다
        AppointmentView t = service.create(1, req("TRANSFER", LocalDate.of(2027, 3, 1), DEPT_B, null, null));
        assertThat(t.after().status()).isEqualTo(EmpStatus.ON_LEAVE);
    }

    @Test
    void 확정된_발령보다_앞선_날짜의_발령은_확정할_수_없다() {
        confirmed(req("TRANSFER", FUTURE, DEPT_B, null, null));
        AppointmentView p = service.create(1, req("PROMOTION", PAST, null, "MANAGER", null));
        assertStatus(() -> service.confirm(p.apptId()), HttpStatus.CONFLICT, "먼저 취소");
        assertThat(store.get(p.apptId()).isConfirmed()).isFalse();
        // 같은 날짜는 된다
        AppointmentView same = service.create(1, req("PROMOTION", FUTURE, null, "MANAGER", null));
        assertThat(service.confirm(same.apptId()).confirmed()).isTrue();
    }

    // ---------------------------------------------------------------- 취소 발령

    @Test
    void 취소_발령은_원_발령과_연결되고_확정하면_되돌린다() {
        long t = confirmed(req("TRANSFER", PAST, DEPT_B, null, null));
        // 반영 뒤 사원 행 값
        when(emps.get(1L).getCurrentDeptId()).thenReturn(DEPT_B);
        clearInvocations(employees);

        AppointmentView c = service.cancel(t, CANCEL);
        assertThat(c.confirmed()).isFalse();
        assertThat(c.cancelOfApptId()).isEqualTo(t);
        assertThat(c.apptTypeCode()).isEqualTo("TRANSFER");
        assertThat(c.effectiveDate()).isEqualTo(PAST);
        assertThat(c.before().deptId()).isEqualTo(DEPT_B);
        assertThat(c.after().deptId()).isEqualTo(DEPT_A);
        assertThat(c.reason()).isEqualTo("발령 착오로 취소");
        // 미확정 취소 발령은 아직 효력이 없다
        assertThat(on(PAST).deptId()).isEqualTo(DEPT_B);
        assertThat(service.get(t).cancelledByApptId()).isEqualTo(c.apptId());

        AppointmentView cc = service.confirm(c.apptId());
        assertThat(cc.confirmed()).isTrue();
        assertThat(cc.cancelOfApptId()).isEqualTo(t);
        assertThat(on(PAST).deptId()).isEqualTo(DEPT_A);
        verify(employees).applyAppointment(eq(1L), eq(DEPT_A), eq("STAFF"), eq("MEMBER"), eq("ACTIVE"), any());

        List<AppointmentView> list = service.listByEmployee(1);
        assertThat(list).extracting(AppointmentView::apptId).containsExactly(t, c.apptId());
        assertThat(list.get(0).cancelledByApptId()).isEqualTo(c.apptId());
        assertThat(list.get(1).cancelOfApptId()).isEqualTo(t);

        // 원 발령·취소 발령은 그대로 남는다(고치거나 지울 수 없음)
        assertStatus(() -> service.delete(c.apptId()), HttpStatus.CONFLICT, "지울 수 없어요");
        assertStatus(() -> service.cancel(t, CANCEL), HttpStatus.CONFLICT, "이미 취소 발령");
        assertStatus(() -> service.cancel(c.apptId(), CANCEL), HttpStatus.CONFLICT, "다시 취소할 수 없어요");
    }

    @Test
    void 취소된_발령_뒤에는_다시_발령하고_앞선_날짜도_확정할_수_있다() {
        long t = confirmed(req("TRANSFER", FUTURE, DEPT_B, null, null));
        service.confirm(service.cancel(t, CANCEL).apptId());
        // 취소된 미래 전보는 순서 검사에서 빠진다
        AppointmentView p = service.confirm(service.create(1, req("TRANSFER", PAST, DEPT_C, null, null)).apptId());
        assertThat(p.before().deptId()).isEqualTo(DEPT_A);
        assertThat(on(FUTURE).deptId()).isEqualTo(DEPT_C);
    }

    @Test
    void 마지막_발령부터_차례로_취소한다() {
        long t1 = confirmed(req("TRANSFER", LocalDate.of(2026, 3, 1), DEPT_B, null, null));
        long t2 = confirmed(req("TRANSFER", PAST, DEPT_C, null, null));
        assertStatus(() -> service.cancel(t1, CANCEL), HttpStatus.CONFLICT, "뒤 발령부터");

        service.confirm(service.cancel(t2, CANCEL).apptId());
        assertThat(on(PAST).deptId()).isEqualTo(DEPT_B);
        service.confirm(service.cancel(t1, CANCEL).apptId());
        // 모두 취소되면 첫 발령의 이전 값(사원 행이 이미 반영돼 바뀌었어도 이력 기준)
        when(emps.get(1L).getCurrentDeptId()).thenReturn(DEPT_C);
        assertThat(on(PAST).deptId()).isEqualTo(DEPT_A);
    }

    @Test
    void 미확정_취소_발령_뒤에_새_발령이_확정되면_취소_발령은_확정할_수_없다() {
        long t = confirmed(req("TRANSFER", PAST, DEPT_B, null, null));
        AppointmentView c = service.cancel(t, CANCEL);
        confirmed(req("PROMOTION", PAST, null, "MANAGER", null));
        assertStatus(() -> service.confirm(c.apptId()), HttpStatus.CONFLICT, "뒤 발령부터");
    }

    @Test
    void 취소할_수_없는_발령() {
        AppointmentView draft = service.create(1, req("TRANSFER", PAST, DEPT_B, null, null));
        assertStatus(() -> service.cancel(draft.apptId(), CANCEL), HttpStatus.CONFLICT, "확정 전 발령");

        long hire = service.confirm(service.create(1,
                new AppointmentRequest("HIRE", HIRE, DEPT_A, "STAFF", "MEMBER", "신규 입사", null, null)).apptId()).apptId();
        assertStatus(() -> service.cancel(hire, CANCEL), HttpStatus.CONFLICT, "입사 발령");

        long t = confirmed(req("TRANSFER", PAST, DEPT_B, null, null));
        assertStatus(() -> service.cancel(t, new CancelRequest("  ")), HttpStatus.BAD_REQUEST, "근로기준법 제23조");
        assertThat(links).isEmpty();
    }

    @Test
    void 미확정_취소_발령은_고칠_수_없고_지우면_연결도_지워진다() {
        long t = confirmed(req("TRANSFER", PAST, DEPT_B, null, null));
        AppointmentView c = service.cancel(t, CANCEL);
        assertStatus(() -> service.update(c.apptId(), req("TRANSFER", PAST, DEPT_C, null, null)), HttpStatus.CONFLICT,
                "취소 발령");
        service.delete(c.apptId());
        assertThat(links).isEmpty();
        assertThat(store).doesNotContainKey(c.apptId());
        assertThat(service.get(t).cancelledByApptId()).isNull();
        assertThat(service.cancel(t, CANCEL).cancelOfApptId()).isEqualTo(t);
    }

    @Test
    void 휴직_취소는_휴직일을_그대로_두고_재직으로_되돌린다() {
        LocalDate s = LocalDate.of(2026, 6, 1);
        long l = confirmed(leave(s, s, LocalDate.of(2027, 5, 31)));
        AppointmentView c = service.confirm(service.cancel(l, CANCEL).apptId());
        assertThat(c.leaveStartDate()).isEqualTo(s);
        assertThat(c.after().status()).isEqualTo(EmpStatus.ACTIVE);
        assertThat(on(s).empStatus()).isEqualTo(EmpStatus.ACTIVE);
        assertThat(service.leavePeriods(1, s, s.plusYears(1))).isEmpty();
    }

    // ---------------------------------------------------------------- 휴직 기간

    @Test
    void 휴직_기간은_복직_발령_전날에_끝나고_기간과_겹치는_것만_돌려준다() {
        LocalDate s = LocalDate.of(2026, 3, 1);
        long l = confirmed(leave(s, s, LocalDate.of(2027, 2, 28)));
        long r = confirmed(new AppointmentRequest("RETURN", LocalDate.of(2026, 9, 1), null, null, null, "조기 복직", null,
                null));

        List<LeavePeriodView> ps = service.leavePeriods(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(ps).containsExactly(new LeavePeriodView(1, l, s, LocalDate.of(2027, 2, 28),
                LocalDate.of(2026, 8, 31), r, "육아휴직 신청(남녀고용평등법 제19조)"));
        // 경계: 마지막 휴직일 하루만 겹쳐도 들어가고, 복직일부터는 빠진다
        assertThat(service.leavePeriods(1, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 31))).hasSize(1);
        assertThat(service.leavePeriods(1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31))).isEmpty();
        assertThat(service.leavePeriods(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28))).isEmpty();
    }

    @Test
    void 복직_발령이_없으면_종료_예정일까지이고_발령일보다_늦게_시작할_수_있다() {
        LocalDate start = LocalDate.of(2026, 12, 7);
        long l = confirmed(leave(FUTURE, start, LocalDate.of(2027, 6, 6)));
        List<LeavePeriodView> ps = service.leavePeriods(1, LocalDate.of(2027, 6, 6), LocalDate.of(2027, 6, 30));
        assertThat(ps).singleElement().satisfies(p -> {
            assertThat(p.leaveApptId()).isEqualTo(l);
            assertThat(p.startDate()).isEqualTo(start);
            assertThat(p.endDate()).isEqualTo(LocalDate.of(2027, 6, 6));
            assertThat(p.endedByApptId()).isNull();
        });
    }

    @Test
    void 휴직_시작_전에_복직하면_휴직_기간이_없다() {
        confirmed(leave(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31)));
        confirmed(new AppointmentRequest("RETURN", LocalDate.of(2026, 6, 20), null, null, null, "휴직 철회", null, null));
        assertThat(service.leavePeriods(1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).isEmpty();
    }

    @Test
    void 전체_사원_휴직_기간과_기간_검사() {
        emp(2);
        LocalDate s = LocalDate.of(2026, 4, 1);
        confirmed(leave(s, s, LocalDate.of(2026, 9, 30)));
        service.confirm(service.create(2, leave(s, s.plusDays(10), LocalDate.of(2026, 10, 31))).apptId());
        // 미확정 휴직은 빠진다
        emp(3);
        service.create(3, leave(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));
        List<LeavePeriodView> all = service.leavePeriods(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30));
        assertThat(all).extracting(LeavePeriodView::empId).containsExactly(1L, 2L);
        assertStatus(() -> service.leavePeriods(1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)),
                HttpStatus.BAD_REQUEST, "종료일");
        assertStatus(() -> service.leavePeriods(null, LocalDate.of(2026, 1, 1)), HttpStatus.BAD_REQUEST, "입력");
    }

    // ---------------------------------------------------------------- 직접 수정 경로 없음

    @Test
    void 사원_수정_API에는_소속_직급_직책_재직상태가_없다() {
        Set<String> fields = new HashSet<>();
        for (RecordComponent c : EmployeeUpdateRequest.class.getRecordComponents()) {
            fields.add(c.getName().toLowerCase());
        }
        assertThat(fields).noneMatch(f -> f.contains("dept") || f.contains("grade") || f.contains("title")
                || f.contains("status") || f.contains("retire"));
    }

    @Test
    void 사원_엔티티로는_현재_값_컬럼을_바꿀_수_없다() {
        // JPA 변경 감지로 바뀌지 않고, EmployeeRepository.applyAppointment(발령 반영)만 바꾼다
        for (String name : List.of("currentDeptId", "jobGradeCode", "jobTitleCode", "empStatus")) {
            Column c = ReflectionUtils.findField(Employee.class, name).getAnnotation(Column.class);
            assertThat(c.updatable()).as(name).isFalse();
        }
        assertThat(Arrays.stream(Employee.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && m.getParameterCount() > 0)
                .map(Method::getName)).isEmpty();
    }

    @Test
    void 입사_전_사원은_반영하지_않는다() {
        Employee e = emp(3);
        when(e.getHireDate()).thenReturn(LocalDate.of(2027, 1, 1));
        assertThat(service.syncEmployee(3)).isFalse();
        verify(employees, never()).applyAppointment(anyLong(), any(), any(), any(), any(), any());
    }
}
