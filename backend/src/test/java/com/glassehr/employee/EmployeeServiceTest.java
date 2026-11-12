package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.code.CodeGroups;
import com.glassehr.code.CommonCode;
import com.glassehr.code.CommonCodeId;
import com.glassehr.code.CommonCodeRepository;
import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeUpdateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeView;
import com.glassehr.employee.EmployeeDtos.RetirementRequest;
import com.glassehr.legalrate.LegalRateException;
import com.glassehr.org.OrgDept;
import com.glassehr.org.OrgDeptRepository;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** 사원 등록·수정·퇴직 규칙. 저장소는 메모리 가짜. */
class EmployeeServiceTest {

    private static final LocalDate HIRE = LocalDate.of(2026, 3, 2);
    private static final RequestActor HR = new RequestActor("hr1", RequestActor.Role.HR, "127.0.0.1");

    private final Map<Long, Employee> store = new HashMap<>();
    private final Map<CommonCodeId, CommonCode> codeStore = new HashMap<>();
    private final Map<Long, OrgDept> deptStore = new HashMap<>();
    private long nextId = 1;

    private EmpNoGenerator empNos;
    private RrnService rrns;
    private RecordRetentionCalculator retention;
    private EmployeeService service;

    private void code(String group, String code, boolean active) {
        CommonCode c = mock(CommonCode.class);
        when(c.isActive()).thenReturn(active);
        codeStore.put(new CommonCodeId(group, code), c);
    }

    private void dept(long id, LocalDate from, LocalDate to) {
        OrgDept d = mock(OrgDept.class);
        when(d.getDeptId()).thenReturn(id);
        when(d.getDeptCode()).thenReturn("D" + id);
        when(d.getValidFrom()).thenReturn(from);
        when(d.getValidTo()).thenReturn(to);
        deptStore.put(id, d);
    }

    @BeforeEach
    void setUp() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        when(employees.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<Long>getArgument(0))));
        when(employees.saveAndFlush(any())).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            if (e.getEmpId() == null) {
                ReflectionTestUtils.setField(e, "empId", nextId++);
            }
            store.put(e.getEmpId(), e);
            return e;
        });
        CommonCodeRepository codes = mock(CommonCodeRepository.class);
        when(codes.findById(any())).thenAnswer(inv -> Optional.ofNullable(codeStore.get(inv.<CommonCodeId>getArgument(0))));
        OrgDeptRepository depts = mock(OrgDeptRepository.class);
        when(depts.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(deptStore.get(inv.<Long>getArgument(0))));

        empNos = mock(EmpNoGenerator.class);
        when(empNos.next(any(), any())).thenReturn("S01-2026-001");
        rrns = mock(RrnService.class);
        when(rrns.masked(anyLong())).thenReturn(Optional.empty());
        when(rrns.save(anyLong(), any(), any())).thenReturn("000000-*******");
        retention = mock(RecordRetentionCalculator.class);

        code(CodeGroups.EMPLOYMENT_TYPE, "REGULAR", true);
        code(CodeGroups.EMPLOYMENT_TYPE, "FIXED_TERM", true);
        code(CodeGroups.EMPLOYMENT_TYPE, "OLD_TYPE", false);
        code(CodeGroups.JOB_FAMILY, "ENGINEERING", true);
        code(CodeGroups.WORK_LOCATION, "HQ", true);
        code(CodeGroups.JOB_GRADE, "STAFF", true);
        code(CodeGroups.JOB_TITLE, "MEMBER", true);
        code(CodeGroups.RETIREMENT_REASON, "VOLUNTARY", true);
        dept(10L, LocalDate.of(2020, 1, 1), null);
        dept(11L, LocalDate.of(2020, 1, 1), HIRE); // 입사일에 폐지됨

        service = new EmployeeService(employees, empNos, codes, depts, rrns, retention,
                Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    private static EmployeeCreateRequest create(String type, LocalDate cStart, LocalDate cEnd, Long deptId, Rrn rrn) {
        return new EmployeeCreateRequest("S01", " 예시사원 ", "F", LocalDate.of(1999, 1, 1), "가상시 예시구 샘플로 1",
                null, "백엔드 개발", "ENGINEERING", HIRE, null, cStart, cEnd, type, 2400, "HQ", null, null,
                deptId, "STAFF", "MEMBER", rrn);
    }

    private static EmployeeCreateRequest regular() {
        return create("REGULAR", null, null, 10L, null);
    }

    private static EmployeeUpdateRequest update(String name, String type) {
        return new EmployeeUpdateRequest(name, "F", LocalDate.of(1999, 1, 1), "가상시 예시구 샘플로 2", "(예시) 경력",
                "프론트엔드 개발", "ENGINEERING", null, null, null, type, 1200, "HQ", null, "메모");
    }

    private static void assertStatus(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(EmployeeException.class,
                e -> assertThat(e.status()).isEqualTo(status));
    }

    @Test
    void 등록하면_사번과_첫_소속_직급_직책이_저장된다() {
        EmployeeView v = service.create(regular(), HR);
        assertThat(v.empNo()).isEqualTo("S01-2026-001");
        assertThat(v.name()).isEqualTo("예시사원");
        assertThat(v.currentDeptId()).isEqualTo(10L);
        assertThat(v.jobGradeCode()).isEqualTo("STAFF");
        assertThat(v.jobTitleCode()).isEqualTo("MEMBER");
        assertThat(v.retireDate()).isNull();
        assertThat(v.recordRetentionUntil()).isNull();
        assertThat(v.rrnMasked()).isNull();
        verify(empNos).next("S01", HIRE);
        verify(rrns, never()).save(anyLong(), any(), any());
    }

    @Test
    void 주민등록번호를_함께_보내면_암호화_저장하고_마스킹_값만_돌려준다() {
        Rrn rrn = Rrn.parse("000000-0000001");
        EmployeeView v = service.create(create("REGULAR", null, null, 10L, rrn), HR);
        verify(rrns).save(v.empId(), rrn, HR);
        assertThat(v.rrnMasked()).isEqualTo("000000-*******");
        assertThat(v.toString()).doesNotContain("0000001");
    }

    @Test
    void 코드와_부서와_날짜를_검사한다() {
        assertStatus(() -> service.create(create("NONE", null, null, 10L, null), HR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(create("OLD_TYPE", null, null, 10L, null), HR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(create("REGULAR", null, null, 99L, null), HR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(create("REGULAR", null, null, 11L, null), HR), HttpStatus.BAD_REQUEST);
        // 기간제는 계약기간 필수, 종료일만 있으면 거부, 종료일이 시작일보다 앞이면 거부
        assertStatus(() -> service.create(create("FIXED_TERM", null, null, 10L, null), HR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(create("REGULAR", null, HIRE, 10L, null), HR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(create("FIXED_TERM", HIRE, HIRE.minusDays(1), 10L, null), HR),
                HttpStatus.BAD_REQUEST);
        assertThat(store).isEmpty();
        verify(empNos, never()).next(any(), any());

        assertThat(service.create(create("FIXED_TERM", HIRE, HIRE.plusYears(1), 10L, null), HR).contractEndDate())
                .isEqualTo(HIRE.plusYears(1));
    }

    @Test
    void 수정은_기본정보만_바꾸고_소속_직급_직책_사번_입사일은_그대로() {
        long id = service.create(regular(), HR).empId();
        EmployeeView v = service.update(id, update("새이름", "REGULAR"));
        assertThat(v.name()).isEqualTo("새이름");
        assertThat(v.jobDescription()).isEqualTo("프론트엔드 개발");
        assertThat(v.weeklyContractMinutes()).isEqualTo(1200);
        assertThat(v.currentDeptId()).isEqualTo(10L);
        assertThat(v.jobGradeCode()).isEqualTo("STAFF");
        assertThat(v.jobTitleCode()).isEqualTo("MEMBER");
        assertThat(v.empNo()).isEqualTo("S01-2026-001");
        assertThat(v.hireDate()).isEqualTo(HIRE);
    }

    @Test
    void 소속_직급_직책을_직접_바꾸는_경로가_없다() {
        Set<String> forbidden = Set.of("currentdeptid", "deptid", "jobgradecode", "jobtitlecode", "empno", "hiredate",
                "sitecode", "retiredate", "recordretentionuntil");
        Set<String> updatable = Arrays.stream(EmployeeUpdateRequest.class.getRecordComponents())
                .map(RecordComponent::getName).map(String::toLowerCase).collect(Collectors.toSet());
        assertThat(updatable).doesNotContainAnyElementsOf(forbidden);
        assertThat(Arrays.stream(Employee.BasicInfo.class.getRecordComponents()).map(c -> c.getName().toLowerCase()))
                .doesNotContainAnyElementsOf(forbidden);
        // 엔티티에 소속·직급·직책 setter/변경 메서드가 없다
        for (Method m : Employee.class.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() > 0) {
                assertThat(m.getName()).as(m.toString()).isIn("updateBasicInfo", "retire", "apply");
            }
        }
    }

    @Test
    void 수정할_사원이_없으면_404() {
        assertStatus(() -> service.update(99L, update("x", "REGULAR")), HttpStatus.NOT_FOUND);
    }

    @Test
    void 퇴직_처리는_사유와_보존기한을_함께_저장한다() {
        long id = service.create(regular(), HR).empId();
        LocalDate retire = LocalDate.of(2026, 10, 31);
        LocalDate until = LocalDate.of(2031, 10, 31); // 계산기 가짜 반환값(법령 값 아님)
        when(retention.retentionUntil(retire)).thenReturn(until);

        EmployeeView v = service.retire(id, new RetirementRequest(retire, "VOLUNTARY", " 개인 사정 "));
        assertThat(v.retireDate()).isEqualTo(retire);
        assertThat(v.retireReasonCode()).isEqualTo("VOLUNTARY");
        assertThat(v.retireReasonDetail()).isEqualTo("개인 사정");
        assertThat(v.recordRetentionUntil()).isEqualTo(until);

        assertStatus(() -> service.retire(id, new RetirementRequest(retire, "VOLUNTARY", null)), HttpStatus.CONFLICT);
    }

    @Test
    void 퇴직일_사유코드를_검사한다() {
        long id = service.create(regular(), HR).empId();
        assertStatus(() -> service.retire(id, new RetirementRequest(HIRE.minusDays(1), "VOLUNTARY", null)),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.retire(id, new RetirementRequest(HIRE, "NOPE", null)), HttpStatus.BAD_REQUEST);
        assertThat(store.get(id).isRetired()).isFalse();
    }

    @Test
    void 보존기간_기준값이_없으면_퇴직_처리를_거부한다() {
        long id = service.create(regular(), HR).empId();
        LegalRateException missing = mock(LegalRateException.class);
        when(missing.itemCode()).thenReturn("EMPLOYEE_RECORD_RETENTION_YEARS");
        when(retention.retentionUntil(any())).thenThrow(missing);
        assertStatus(() -> service.retire(id, new RetirementRequest(HIRE.plusDays(10), "VOLUNTARY", null)),
                HttpStatus.CONFLICT);
        assertThat(store.get(id).isRetired()).isFalse();
    }
}
