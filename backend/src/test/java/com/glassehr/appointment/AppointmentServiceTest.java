package com.glassehr.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.appointment.AppointmentDtos.AppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.code.CodeGroups;
import com.glassehr.code.CommonCode;
import com.glassehr.code.CommonCodeId;
import com.glassehr.code.CommonCodeRepository;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.org.OrgDept;
import com.glassehr.org.OrgDeptRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** 인사발령 등록·수정·확정·삭제 규칙. 저장소는 메모리 가짜. 법정 수치 계산은 없다. */
class AppointmentServiceTest {

    /** 오늘(서울) = 2026-11-13. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate HIRE = LocalDate.of(2024, 1, 2);
    private static final LocalDate D = LocalDate.of(2026, 12, 1);
    private static final long DEPT_A = 10;
    private static final long DEPT_B = 20;
    private static final long DEPT_CLOSING = 30;
    private static final long DEPT_FUTURE = 40;

    private final Map<Long, HrAppointment> store = new HashMap<>();
    private final Map<CommonCodeId, CommonCode> codeStore = new HashMap<>();
    private final Map<Long, OrgDept> deptStore = new HashMap<>();
    private final Map<Long, Employee> emps = new HashMap<>();
    private long nextId = 1;
    private long nextNo = 1;

    private HrAppointmentRepository appointments;
    private EmployeeRepository employees;
    private AppointmentNoGenerator apptNos;
    private AppointmentService service;

    private Employee emp(long id, LocalDate retire) {
        Employee e = mock(Employee.class);
        when(e.getEmpId()).thenReturn(id);
        when(e.getHireDate()).thenReturn(HIRE);
        when(e.getRetireDate()).thenReturn(retire);
        when(e.getCurrentDeptId()).thenReturn(DEPT_A);
        when(e.getJobGradeCode()).thenReturn("STAFF");
        when(e.getJobTitleCode()).thenReturn("MEMBER");
        emps.put(id, e);
        return e;
    }

    private void code(String group, String code, boolean active) {
        CommonCode c = mock(CommonCode.class);
        when(c.isActive()).thenReturn(active);
        codeStore.put(new CommonCodeId(group, code), c);
    }

    private void dept(long id, LocalDate from, LocalDate to) {
        OrgDept d = mock(OrgDept.class);
        when(d.getDeptCode()).thenReturn("D" + id);
        when(d.getValidFrom()).thenReturn(from);
        when(d.getValidTo()).thenReturn(to);
        deptStore.put(id, d);
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
            store.put(a.getApptId(), a);
            return a;
        });
        doAnswer(inv -> store.remove(inv.<HrAppointment>getArgument(0).getApptId())).when(appointments).delete(any());
        Comparator<HrAppointment> byDate = Comparator.comparing(HrAppointment::getEffectiveDate)
                .thenComparing(HrAppointment::getApptId);
        when(appointments.findByEmpIdOrderByEffectiveDateAscApptIdAsc(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            List<HrAppointment> all = new ArrayList<>(store.values());
            all.sort(byDate);
            return all.stream().filter(a -> a.getEmpId() == id).toList();
        });
        when(appointments.findAllByOrderByEffectiveDateAscApptIdAsc()).thenAnswer(inv -> {
            List<HrAppointment> all = new ArrayList<>(store.values());
            all.sort(byDate);
            return all;
        });

        employees = mock(EmployeeRepository.class);
        when(employees.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(emps.get(inv.<Long>getArgument(0))));
        when(employees.findForUpdate(anyLong())).thenAnswer(inv -> Optional.ofNullable(emps.get(inv.<Long>getArgument(0))));

        CommonCodeRepository codes = mock(CommonCodeRepository.class);
        when(codes.findById(any())).thenAnswer(inv -> Optional.ofNullable(codeStore.get(inv.<CommonCodeId>getArgument(0))));
        for (AppointmentType t : AppointmentType.values()) {
            code(CodeGroups.APPOINTMENT_TYPE, t.code(), true);
        }
        code(CodeGroups.JOB_GRADE, "STAFF", true);
        code(CodeGroups.JOB_GRADE, "MANAGER", true);
        code(CodeGroups.JOB_GRADE, "OLD_GRADE", false);
        code(CodeGroups.JOB_TITLE, "MEMBER", true);
        code(CodeGroups.JOB_TITLE, "TEAM_LEADER", true);

        OrgDeptRepository depts = mock(OrgDeptRepository.class);
        when(depts.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(deptStore.get(inv.<Long>getArgument(0))));
        dept(DEPT_A, LocalDate.of(2000, 1, 1), null);
        dept(DEPT_B, LocalDate.of(2000, 1, 1), null);
        dept(DEPT_CLOSING, LocalDate.of(2000, 1, 1), LocalDate.of(2027, 1, 1));
        dept(DEPT_FUTURE, LocalDate.of(2027, 1, 1), null);

        apptNos = mock(AppointmentNoGenerator.class);
        when(apptNos.next(anyInt())).thenAnswer(inv ->
                AppointmentNoGenerator.format(inv.<Integer>getArgument(0), nextNo++));

        service = new AppointmentService(appointments, employees, codes, depts, apptNos, CLOCK);
        emp(1, null);
    }

    private static AppointmentRequest req(String type, LocalDate date, Long dept, String grade, String title) {
        return new AppointmentRequest(type, date, dept, grade, title, "조직 개편에 따른 발령", null, null);
    }

    private static AppointmentRequest leave(LocalDate date, LocalDate start, LocalDate end) {
        return new AppointmentRequest("LEAVE", date, null, null, null, "육아휴직 신청(남녀고용평등법 제19조)", start, end);
    }

    private static void assertStatus(Runnable r, HttpStatus status, String messagePart) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(AppointmentException.class, ex -> {
            assertThat(ex.status()).isEqualTo(status);
            assertThat(ex.getMessage()).contains(messagePart);
        });
    }

    // ---------------------------------------------------------------- 등록

    @Test
    void 전보_등록은_미확정이고_이전값을_사원값으로_채우고_바꾸지_않는_항목은_옮긴다() {
        AppointmentView v = service.create(1, req("TRANSFER", D, DEPT_B, null, null));
        assertThat(v.apptNo()).isEqualTo("AP-2026-000001");
        assertThat(v.confirmed()).isFalse();
        assertThat(v.confirmedAt()).isNull();
        assertThat(v.before()).isEqualTo(new HrAppointment.Snapshot(DEPT_A, "STAFF", "MEMBER", EmpStatus.ACTIVE));
        assertThat(v.after()).isEqualTo(new HrAppointment.Snapshot(DEPT_B, "STAFF", "MEMBER", EmpStatus.ACTIVE));
        assertThat(v.reason()).isEqualTo("조직 개편에 따른 발령");
        assertThat(v.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 13, 10, 0));
    }

    @Test
    void 쓰기는_사원_잠금을_먼저_잡는다() {
        AppointmentView v = service.create(1, req("TRANSFER", D, DEPT_B, null, null));
        clearInvocations(employees, appointments);
        InOrder order = inOrder(employees, appointments);
        service.confirm(v.apptId());
        order.verify(employees).findForUpdate(1L);
        order.verify(appointments).findForUpdate(v.apptId());
    }

    @Test
    void 사유가_공백이면_거부한다() {
        AppointmentRequest r = new AppointmentRequest("TRANSFER", D, DEPT_B, null, null, "   ", null, null);
        assertStatus(() -> service.create(1, r), HttpStatus.BAD_REQUEST, "근로기준법 제23조");
        verify(apptNos, never()).next(anyInt());
    }

    @Test
    void 없는_유형이나_사용중지_유형은_거부한다() {
        assertStatus(() -> service.create(1, req("DISPATCH", D, null, null, null)), HttpStatus.BAD_REQUEST, "없는 발령 유형");
        code(CodeGroups.APPOINTMENT_TYPE, "TRANSFER", false);
        assertStatus(() -> service.create(1, req("TRANSFER", D, DEPT_B, null, null)), HttpStatus.BAD_REQUEST,
                "사용하지 않는 발령 유형");
    }

    @Test
    void 없는_사원은_404() {
        assertStatus(() -> service.create(99, req("TRANSFER", D, DEPT_B, null, null)), HttpStatus.NOT_FOUND, "사원");
    }

    @Test
    void 전보는_부서가_필요하고_지금_소속과_달라야_한다() {
        assertStatus(() -> service.create(1, req("TRANSFER", D, null, null, null)), HttpStatus.BAD_REQUEST, "옮길 부서");
        assertStatus(() -> service.create(1, req("TRANSFER", D, DEPT_A, null, null)), HttpStatus.BAD_REQUEST, "같아요");
    }

    @Test
    void 전보_부서는_발령일에_존속하고_폐지예정이_아니어야_한다() {
        assertStatus(() -> service.create(1, req("TRANSFER", D, 999L, null, null)), HttpStatus.BAD_REQUEST, "부서가 없어요");
        assertStatus(() -> service.create(1, req("TRANSFER", D, DEPT_FUTURE, null, null)), HttpStatus.BAD_REQUEST,
                "존속하지 않아요");
        assertStatus(() -> service.create(1, req("TRANSFER", D, DEPT_CLOSING, null, null)), HttpStatus.BAD_REQUEST,
                "폐지");
    }

    @Test
    void 유형이_바꾸지_않는_항목을_다른_값으로_보내면_거부하고_같은_값은_허용한다() {
        assertStatus(() -> service.create(1, req("TRANSFER", D, DEPT_B, "MANAGER", null)), HttpStatus.BAD_REQUEST,
                "전보 발령에서는 직급");
        assertStatus(() -> service.create(1, req("PROMOTION", D, DEPT_B, "MANAGER", null)), HttpStatus.BAD_REQUEST,
                "승진 발령에서는 소속");
        AppointmentView v = service.create(1, req("TRANSFER", D, DEPT_B, "STAFF", "MEMBER"));
        assertThat(v.after().deptId()).isEqualTo(DEPT_B);
    }

    @Test
    void 승진은_사용중인_다른_직급이어야_한다() {
        assertStatus(() -> service.create(1, req("PROMOTION", D, null, null, null)), HttpStatus.BAD_REQUEST, "직급을");
        assertStatus(() -> service.create(1, req("PROMOTION", D, null, "STAFF", null)), HttpStatus.BAD_REQUEST, "같아요");
        assertStatus(() -> service.create(1, req("PROMOTION", D, null, "NONE", null)), HttpStatus.BAD_REQUEST, "없는 직급");
        assertStatus(() -> service.create(1, req("PROMOTION", D, null, "OLD_GRADE", null)), HttpStatus.BAD_REQUEST,
                "사용하지 않는 직급");
        AppointmentView v = service.create(1, req("PROMOTION", D, null, "MANAGER", null));
        assertThat(v.after()).isEqualTo(new HrAppointment.Snapshot(DEPT_A, "MANAGER", "MEMBER", EmpStatus.ACTIVE));
    }

    @Test
    void 직책_발령은_변경과_해제를_받는다() {
        AppointmentView set = service.create(1, req("TITLE_CHANGE", D, null, null, "TEAM_LEADER"));
        assertThat(set.after().jobTitleCode()).isEqualTo("TEAM_LEADER");
        AppointmentView clear = service.create(1, req("TITLE_CHANGE", D, null, null, null));
        assertThat(clear.after().jobTitleCode()).isNull();
        assertStatus(() -> service.create(1, req("TITLE_CHANGE", D, null, null, "MEMBER")), HttpStatus.BAD_REQUEST,
                "같아요");
    }

    @Test
    void 입사_발령은_입사일에_부서와_직급이_필요하고_이전값이_없으며_한_번만_된다() {
        assertStatus(() -> service.create(1, req("HIRE", D, DEPT_A, "STAFF", null)), HttpStatus.BAD_REQUEST, "입사일");
        assertStatus(() -> service.create(1, req("HIRE", HIRE, null, "STAFF", null)), HttpStatus.BAD_REQUEST, "부서");
        assertStatus(() -> service.create(1, req("HIRE", HIRE, DEPT_A, null, null)), HttpStatus.BAD_REQUEST, "직급");
        AppointmentView v = service.create(1, req("HIRE", HIRE, DEPT_A, "STAFF", null));
        assertThat(v.before()).isEqualTo(new HrAppointment.Snapshot(null, null, null, null));
        assertThat(v.after()).isEqualTo(new HrAppointment.Snapshot(DEPT_A, "STAFF", null, EmpStatus.ACTIVE));
        assertStatus(() -> service.create(1, req("HIRE", HIRE, DEPT_A, "STAFF", null)), HttpStatus.CONFLICT, "이미 입사");
        // 자기 자신은 중복으로 보지 않는다
        assertThat(service.update(v.apptId(), req("HIRE", HIRE, DEPT_B, "STAFF", null)).after().deptId()).isEqualTo(DEPT_B);
    }

    @Test
    void 발령일은_입사일_이후이고_퇴직일_뒤일_수_없다() {
        assertStatus(() -> service.create(1, req("TRANSFER", HIRE.minusDays(1), DEPT_B, null, null)),
                HttpStatus.BAD_REQUEST, "입사일");
        emp(2, LocalDate.of(2026, 6, 30));
        assertStatus(() -> service.create(2, req("TRANSFER", LocalDate.of(2026, 7, 1), DEPT_B, null, null)),
                HttpStatus.BAD_REQUEST, "퇴직일");
        // 퇴직일 당일은 재직
        AppointmentView v = service.create(2, req("RETIREMENT", LocalDate.of(2026, 6, 30), null, null, null));
        assertThat(v.before().status()).isEqualTo(EmpStatus.ACTIVE);
        assertThat(v.after().status()).isEqualTo(EmpStatus.RETIRED);
    }

    // ---------------------------------------------------------------- 휴직·복직

    @Test
    void 휴직_발령은_시작_종료_예정일이_필요하다() {
        assertStatus(() -> service.create(1, leave(D, null, D.plusMonths(6))), HttpStatus.BAD_REQUEST, "종료 예정일을");
        assertStatus(() -> service.create(1, leave(D, D.minusDays(1), D.plusMonths(6))), HttpStatus.BAD_REQUEST,
                "발령일");
        assertStatus(() -> service.create(1, leave(D, D, D.minusDays(1))), HttpStatus.BAD_REQUEST, "휴직 시작일 이후");
        AppointmentView v = service.create(1, leave(D, D, D));
        assertThat(v.leaveStartDate()).isEqualTo(D);
        assertThat(v.leaveEndDate()).isEqualTo(D);
        assertThat(v.after().status()).isEqualTo(EmpStatus.ON_LEAVE);
    }

    @Test
    void 휴직이_아닌_발령에_휴직일을_넣으면_거부한다() {
        AppointmentRequest r = new AppointmentRequest("TRANSFER", D, DEPT_B, null, null, "사유", D, D.plusDays(1));
        assertStatus(() -> service.create(1, r), HttpStatus.BAD_REQUEST, "휴직 발령에만");
    }

    @Test
    void 복직은_확정된_휴직_뒤에만_되고_휴직_중_재휴직은_안_된다() {
        assertStatus(() -> service.create(1, req("RETURN", D, null, null, null)), HttpStatus.CONFLICT, "휴직 중인 사원만");
        AppointmentView l = service.create(1, leave(D, D, D.plusMonths(6)));
        // 미확정 휴직은 재직상태를 바꾸지 않는다
        assertStatus(() -> service.create(1, req("RETURN", D.plusMonths(6), null, null, null)), HttpStatus.CONFLICT,
                "휴직 중인 사원만");
        service.confirm(l.apptId());
        // 휴직 발령일 전 날짜의 복직은 안 된다
        assertStatus(() -> service.create(1, req("RETURN", D.minusDays(1), null, null, null)), HttpStatus.CONFLICT,
                "지금 재직");
        assertStatus(() -> service.create(1, leave(D.plusDays(10), D.plusDays(10), D.plusMonths(7))),
                HttpStatus.CONFLICT, "재직 중인 사원만");
        AppointmentView r = service.create(1, req("RETURN", D.plusMonths(6).plusDays(1), null, null, null));
        assertThat(r.before().status()).isEqualTo(EmpStatus.ON_LEAVE);
        assertThat(r.after().status()).isEqualTo(EmpStatus.ACTIVE);
    }

    @Test
    void 확정된_퇴직_뒤로는_발령할_수_없다() {
        AppointmentView ret = service.create(1, req("RETIREMENT", D, null, null, null));
        service.confirm(ret.apptId());
        assertStatus(() -> service.create(1, req("TRANSFER", D.plusDays(1), DEPT_B, null, null)), HttpStatus.CONFLICT,
                "퇴직한");
        assertStatus(() -> service.create(1, req("RETIREMENT", D.plusDays(1), null, null, null)), HttpStatus.CONFLICT,
                "퇴직한");
        // 퇴직일 전 날짜 발령은 된다
        assertThat(service.create(1, req("TRANSFER", D.minusDays(1), DEPT_B, null, null)).after().deptId())
                .isEqualTo(DEPT_B);
    }

    // ---------------------------------------------------------------- 수정·확정·삭제

    @Test
    void 미확정_발령은_고칠_수_있고_발령번호는_그대로다() {
        AppointmentView v = service.create(1, req("TRANSFER", D, DEPT_B, null, null));
        AppointmentView u = service.update(v.apptId(), req("PROMOTION", D.plusDays(1), null, "MANAGER", null));
        assertThat(u.apptNo()).isEqualTo(v.apptNo());
        assertThat(u.apptTypeCode()).isEqualTo("PROMOTION");
        assertThat(u.after()).isEqualTo(new HrAppointment.Snapshot(DEPT_A, "MANAGER", "MEMBER", EmpStatus.ACTIVE));
    }

    @Test
    void 확정하면_확정일시를_남기고_다시_고치거나_지우거나_확정할_수_없다() {
        AppointmentView v = service.create(1, req("TRANSFER", D, DEPT_B, null, null));
        AppointmentView c = service.confirm(v.apptId());
        assertThat(c.confirmed()).isTrue();
        assertThat(c.confirmedAt()).isEqualTo(LocalDateTime.of(2026, 11, 13, 10, 0));
        assertStatus(() -> service.update(v.apptId(), req("TRANSFER", D, DEPT_B, null, null)), HttpStatus.CONFLICT,
                "고칠 수 없어요");
        assertStatus(() -> service.delete(v.apptId()), HttpStatus.CONFLICT, "지울 수 없어요");
        assertStatus(() -> service.confirm(v.apptId()), HttpStatus.CONFLICT, "확정된 발령");
        assertThat(store).containsKey(v.apptId());
    }

    @Test
    void 확정할_때_이전값을_다시_채우고_규칙을_다시_검사한다() {
        AppointmentView v = service.create(1, req("PROMOTION", D, null, "MANAGER", null));
        Employee e = emps.get(1L);
        when(e.getCurrentDeptId()).thenReturn(DEPT_B);
        AppointmentView c = service.confirm(v.apptId());
        assertThat(c.before().deptId()).isEqualTo(DEPT_B);
        assertThat(c.after()).isEqualTo(new HrAppointment.Snapshot(DEPT_B, "MANAGER", "MEMBER", EmpStatus.ACTIVE));

        AppointmentView t = service.create(1, req("TRANSFER", D, DEPT_A, null, null));
        when(e.getCurrentDeptId()).thenReturn(DEPT_A);
        assertStatus(() -> service.confirm(t.apptId()), HttpStatus.BAD_REQUEST, "같아요");
        assertThat(store.get(t.apptId()).isConfirmed()).isFalse();
    }

    @Test
    void 미확정_발령은_지울_수_있다() {
        AppointmentView v = service.create(1, req("TRANSFER", D, DEPT_B, null, null));
        service.delete(v.apptId());
        assertThat(store).doesNotContainKey(v.apptId());
        assertStatus(() -> service.get(v.apptId()), HttpStatus.NOT_FOUND, "발령이 없어요");
        assertStatus(() -> service.delete(v.apptId()), HttpStatus.NOT_FOUND, "발령이 없어요");
    }

    // ---------------------------------------------------------------- 조회

    @Test
    void 목록은_확정여부_유형_기간으로_거른다() {
        emp(2, null);
        AppointmentView a = service.create(1, req("TRANSFER", D, DEPT_B, null, null));
        AppointmentView b = service.create(2, req("PROMOTION", D.plusDays(5), null, "MANAGER", null));
        service.confirm(b.apptId());
        assertThat(service.search(null, null, null, null)).extracting(AppointmentView::apptId)
                .containsExactly(a.apptId(), b.apptId());
        assertThat(service.search(true, null, null, null)).extracting(AppointmentView::apptId)
                .containsExactly(b.apptId());
        assertThat(service.search(null, "TRANSFER", null, null)).extracting(AppointmentView::apptId)
                .containsExactly(a.apptId());
        assertThat(service.search(null, null, D.plusDays(1), D.plusDays(5))).extracting(AppointmentView::apptId)
                .containsExactly(b.apptId());
        assertThat(service.search(null, null, D, D)).extracting(AppointmentView::apptId).containsExactly(a.apptId());
        assertThat(service.listByEmployee(2)).extracting(AppointmentView::apptId).containsExactly(b.apptId());
        assertStatus(() -> service.search(null, null, D, D.minusDays(1)), HttpStatus.BAD_REQUEST, "종료일");
        assertStatus(() -> service.listByEmployee(99), HttpStatus.NOT_FOUND, "사원");
    }
}
