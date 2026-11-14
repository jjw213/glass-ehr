package com.glassehr.appointment;

import com.glassehr.appointment.AppointmentDtos.AppointmentRequest;
import com.glassehr.appointment.AppointmentDtos.AppointmentView;
import com.glassehr.appointment.AppointmentType.Field;
import com.glassehr.appointment.HrAppointment.Content;
import com.glassehr.appointment.HrAppointment.Snapshot;
import com.glassehr.code.CodeGroups;
import com.glassehr.code.CommonCode;
import com.glassehr.code.CommonCodeId;
import com.glassehr.code.CommonCodeRepository;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.org.OrgDept;
import com.glassehr.org.OrgDeptRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인사발령 등록·수정(미확정만)·확정·삭제(미확정만)·조회. 설계: docs/hr-appointment-design.md.
 *
 * <ul>
 *   <li>근로기준법 제23조 제1항: 사용자는 정당한 이유 없이 해고, 휴직, 정직, 전직, 감봉, 그 밖의 징벌을 하지 못한다.
 *       그래서 모든 발령에 사유를 반드시 적게 한다(요청 검사 + DB NOT NULL·공백 금지).</li>
 *   <li>남녀고용평등과 일·가정 양립 지원에 관한 법률 제19조(육아휴직): 휴직(LEAVE) 발령으로 등록하고 휴직 시작·종료 예정일을 받는다.
 *       휴직 기간 상한 같은 법정 수치 검사는 이 이슈 범위가 아니다(근태·휴직 이슈에서 legal_rate로 검사).</li>
 *   <li>이전 값은 등록·수정·확정 때마다 다시 채운다. 소속·직급·직책은 사원 현재 값, 재직상태는 발령일까지 확정된 발령 중
 *       마지막 것의 이후 재직상태(없으면 사원 퇴직일 기준).</li>
 *   <li>확정된 발령은 고치거나 지울 수 없다. 사원 정보 반영은 다음 이슈(#16 나눔 2번)에서 한다.</li>
 * </ul>
 * 쓰기 권한(인사담당) 검사는 백엔드 인증·권한 이슈에서 붙인다(사원·계약 API와 같음).
 */
@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final HrAppointmentRepository appointments;
    private final EmployeeRepository employees;
    private final CommonCodeRepository codes;
    private final OrgDeptRepository depts;
    private final AppointmentNoGenerator apptNos;
    private final Clock clock;

    @Autowired
    public AppointmentService(HrAppointmentRepository appointments, EmployeeRepository employees,
            CommonCodeRepository codes, OrgDeptRepository depts, AppointmentNoGenerator apptNos) {
        this(appointments, employees, codes, depts, apptNos, Clock.system(SEOUL));
    }

    AppointmentService(HrAppointmentRepository appointments, EmployeeRepository employees, CommonCodeRepository codes,
            OrgDeptRepository depts, AppointmentNoGenerator apptNos, Clock clock) {
        this.appointments = appointments;
        this.employees = employees;
        this.codes = codes;
        this.depts = depts;
        this.apptNos = apptNos;
        this.clock = clock.withZone(SEOUL);
    }

    /** 요청 또는 저장된 발령에서 온, 이전 값을 채우기 전의 내용. */
    private record Draft(String typeCode, LocalDate effectiveDate, Long afterDeptId, String afterJobGradeCode,
            String afterJobTitleCode, String reason, LocalDate leaveStartDate, LocalDate leaveEndDate) {
    }

    // ---------------------------------------------------------------- 조회

    public List<AppointmentView> listByEmployee(long empId) {
        loadEmployee(empId);
        return appointments.findByEmpIdOrderByEffectiveDateAscApptIdAsc(empId).stream()
                .map(AppointmentView::of).toList();
    }

    /** 전체 발령. 조건은 모두 선택(확정 여부, 유형, 발령일 from~to 양끝 포함). 발령일 순. */
    public List<AppointmentView> search(Boolean confirmed, String typeCode, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw AppointmentException.badRequest("조회 종료일은 시작일 이후여야 해요");
        }
        String type = blankToNull(typeCode);
        return appointments.findAllByOrderByEffectiveDateAscApptIdAsc().stream()
                .filter(a -> confirmed == null || a.isConfirmed() == confirmed)
                .filter(a -> type == null || a.getApptTypeCode().equals(type))
                .filter(a -> from == null || !a.getEffectiveDate().isBefore(from))
                .filter(a -> to == null || !a.getEffectiveDate().isAfter(to))
                .map(AppointmentView::of).toList();
    }

    public AppointmentView get(long apptId) {
        return AppointmentView.of(load(apptId));
    }

    // ---------------------------------------------------------------- 등록·수정·확정·삭제

    @Transactional
    public AppointmentView create(long empId, AppointmentRequest req) {
        Employee e = lockEmployee(empId);
        Content c = resolve(e, draftOf(req), null);
        String apptNo = apptNos.next(today().getYear());
        HrAppointment a = appointments.saveAndFlush(new HrAppointment(apptNo, empId, c, now()));
        return AppointmentView.of(a);
    }

    /** 미확정 발령만 고친다. 대상 사원은 바꾸지 않는다(다른 사원이면 지우고 새로 등록). */
    @Transactional
    public AppointmentView update(long apptId, AppointmentRequest req) {
        Employee e = lockEmployee(empIdOf(apptId));
        HrAppointment a = loadForUpdate(apptId);
        requireUnconfirmed(a, "고칠");
        Content c = resolve(e, draftOf(req), apptId);
        a.update(c, now());
        appointments.saveAndFlush(a);
        return AppointmentView.of(a);
    }

    /** 확정. 이전 값을 지금 사원 값으로 다시 채우고 규칙을 다시 검사한 뒤 확정 여부·확정일시를 기록한다. */
    @Transactional
    public AppointmentView confirm(long apptId) {
        Employee e = lockEmployee(empIdOf(apptId));
        HrAppointment a = loadForUpdate(apptId);
        requireUnconfirmed(a, "다시 확정할");
        Content c = resolve(e, draftOf(a), apptId);
        a.confirm(c, now());
        appointments.saveAndFlush(a);
        return AppointmentView.of(a);
    }

    /** 미확정 발령만 지운다(잘못 등록한 경우). 확정된 발령은 이력으로 남긴다. */
    @Transactional
    public void delete(long apptId) {
        lockEmployee(empIdOf(apptId));
        HrAppointment a = loadForUpdate(apptId);
        requireUnconfirmed(a, "지울");
        appointments.delete(a);
        appointments.flush();
    }

    // ---------------------------------------------------------------- 규칙

    /** 유형별 규칙을 검사하고 이전·이후 값을 채운다. selfId 는 수정·확정 중인 발령(자기 자신은 비교에서 뺀다). */
    private Content resolve(Employee e, Draft d, Long selfId) {
        AppointmentType type = requireType(d.typeCode());
        LocalDate date = d.effectiveDate();
        List<HrAppointment> others = appointments.findByEmpIdOrderByEffectiveDateAscApptIdAsc(e.getEmpId()).stream()
                .filter(a -> !a.getApptId().equals(selfId)).toList();

        if (type == AppointmentType.HIRE) {
            if (!date.equals(e.getHireDate())) {
                throw AppointmentException.badRequest("입사 발령일은 입사일(" + e.getHireDate() + ")과 같아야 해요");
            }
            if (others.stream().anyMatch(a -> AppointmentType.HIRE.code().equals(a.getApptTypeCode()))) {
                throw AppointmentException.conflict("이 사원에게는 이미 입사 발령이 있어요");
            }
        } else if (date.isBefore(e.getHireDate())) {
            throw AppointmentException.badRequest("발령일은 입사일(" + e.getHireDate() + ") 이후여야 해요");
        }
        if (e.getRetireDate() != null && date.isAfter(e.getRetireDate())) {
            throw AppointmentException.badRequest("퇴직일(" + e.getRetireDate() + ") 뒤로는 발령할 수 없어요");
        }

        Snapshot before = type == AppointmentType.HIRE ? Snapshot.NONE : current(e, date, others);
        EmpStatus afterStatus = afterStatus(type, before.status());

        Long dept = pick(type, Field.DEPT, d.afterDeptId(), before.deptId());
        String grade = pick(type, Field.GRADE, d.afterJobGradeCode(), before.jobGradeCode());
        String title = pick(type, Field.TITLE, d.afterJobTitleCode(), before.jobTitleCode());
        switch (type) {
            case HIRE -> {
                requirePresent(dept, "입사 발령은 소속 부서를 입력해 주세요");
                requirePresent(grade, "입사 발령은 직급을 입력해 주세요");
            }
            case TRANSFER -> {
                requirePresent(dept, "전보 발령은 옮길 부서를 입력해 주세요");
                requireChanged(dept, before.deptId(), "옮길 부서가 지금 소속과 같아요");
            }
            case PROMOTION -> {
                requirePresent(grade, "승진 발령은 바뀔 직급을 입력해 주세요");
                requireChanged(grade, before.jobGradeCode(), "바뀔 직급이 지금 직급과 같아요");
            }
            // 이후 직책을 비우면 직책 해제
            case TITLE_CHANGE -> requireChanged(title, before.jobTitleCode(), "바뀔 직책이 지금 직책과 같아요");
            default -> {
            }
        }
        if (type.changes(Field.DEPT) && dept != null) {
            requireDeptOpenOn(dept, date);
        }
        if (type.changes(Field.GRADE)) {
            requireCode(CodeGroups.JOB_GRADE, grade, "직급");
        }
        if (type.changes(Field.TITLE)) {
            requireCode(CodeGroups.JOB_TITLE, title, "직책");
        }

        if (type == AppointmentType.LEAVE) {
            if (d.leaveStartDate() == null || d.leaveEndDate() == null) {
                throw AppointmentException.badRequest("휴직 발령은 휴직 시작일과 종료 예정일을 입력해 주세요");
            }
            if (d.leaveStartDate().isBefore(date)) {
                throw AppointmentException.badRequest("휴직 시작일은 발령일(" + date + ") 이후여야 해요");
            }
            if (d.leaveEndDate().isBefore(d.leaveStartDate())) {
                throw AppointmentException.badRequest("휴직 종료 예정일은 휴직 시작일 이후여야 해요");
            }
        } else if (d.leaveStartDate() != null || d.leaveEndDate() != null) {
            throw AppointmentException.badRequest("휴직 시작·종료 예정일은 휴직 발령에만 입력해요");
        }

        String reason = d.reason() == null ? "" : d.reason().strip();
        if (reason.isEmpty()) {
            // 근로기준법 제23조 제1항: 전직·휴직 등은 정당한 이유가 있어야 한다 → 사유 필수
            throw AppointmentException.badRequest("발령 사유를 입력해 주세요(근로기준법 제23조)");
        }
        return new Content(type, date, before, new Snapshot(dept, grade, title, afterStatus), reason,
                d.leaveStartDate(), d.leaveEndDate());
    }

    /**
     * 발령일 기준 사원 현재 값. 소속·직급·직책은 사원 행 값(사원 반영은 다음 이슈), 재직상태는 발령일까지 확정된
     * 다른 발령 중 마지막 것(발령일 → 확정일시 → ID 순)의 이후 재직상태. 그런 발령이 없으면 발령일 전에 퇴직했으면 퇴직, 아니면 재직.
     */
    private static Snapshot current(Employee e, LocalDate date, List<HrAppointment> others) {
        EmpStatus status = others.stream()
                .filter(HrAppointment::isConfirmed)
                .filter(a -> !a.getEffectiveDate().isAfter(date))
                .max(Comparator.comparing(HrAppointment::getEffectiveDate)
                        .thenComparing(HrAppointment::getConfirmedAt)
                        .thenComparing(HrAppointment::getApptId))
                .map(HrAppointment::getAfterEmpStatus)
                .orElse(e.getRetireDate() != null && e.getRetireDate().isBefore(date)
                        ? EmpStatus.RETIRED : EmpStatus.ACTIVE);
        return new Snapshot(e.getCurrentDeptId(), e.getJobGradeCode(), e.getJobTitleCode(), status);
    }

    private static EmpStatus afterStatus(AppointmentType type, EmpStatus before) {
        return switch (type) {
            case HIRE -> EmpStatus.ACTIVE;
            case LEAVE -> {
                if (before != EmpStatus.ACTIVE) {
                    throw AppointmentException.conflict("재직 중인 사원만 휴직 발령을 할 수 있어요(지금 " + label(before) + ")");
                }
                yield EmpStatus.ON_LEAVE;
            }
            case RETURN -> {
                if (before != EmpStatus.ON_LEAVE) {
                    throw AppointmentException.conflict("휴직 중인 사원만 복직 발령을 할 수 있어요(지금 " + label(before) + ")");
                }
                yield EmpStatus.ACTIVE;
            }
            case RETIREMENT -> {
                requireNotRetired(before);
                yield EmpStatus.RETIRED;
            }
            case TRANSFER, PROMOTION, TITLE_CHANGE -> {
                requireNotRetired(before);
                yield before;
            }
        };
    }

    private static void requireNotRetired(EmpStatus s) {
        if (s == EmpStatus.RETIRED) {
            throw AppointmentException.conflict("퇴직한 사원에게는 발령할 수 없어요");
        }
    }

    private static String label(EmpStatus s) {
        return switch (s) {
            case ACTIVE -> "재직";
            case ON_LEAVE -> "휴직";
            case RETIRED -> "퇴직";
        };
    }

    /** 유형이 바꾸는 항목이면 보낸 값, 아니면 이전 값. 바꾸지 않는 항목을 다른 값으로 보내면 거부한다. */
    private static <T> T pick(AppointmentType type, Field f, T sent, T before) {
        if (type.changes(f)) {
            return sent;
        }
        if (sent != null && !sent.equals(before)) {
            throw AppointmentException.badRequest(type.label() + " 발령에서는 " + f.label() + "을(를) 바꿀 수 없어요");
        }
        return before;
    }

    private static void requirePresent(Object v, String message) {
        if (v == null) {
            throw AppointmentException.badRequest(message);
        }
    }

    private static void requireChanged(Object after, Object before, String message) {
        if (Objects.equals(after, before)) {
            throw AppointmentException.badRequest(message);
        }
    }

    private AppointmentType requireType(String code) {
        CommonCode c = codes.findById(new CommonCodeId(CodeGroups.APPOINTMENT_TYPE, code)).orElse(null);
        AppointmentType t = AppointmentType.ofCode(code).orElse(null);
        if (c == null || t == null) {
            throw AppointmentException.badRequest("없는 발령 유형 코드예요: " + code);
        }
        if (!c.isActive()) {
            throw AppointmentException.badRequest("사용하지 않는 발령 유형 코드예요: " + code);
        }
        return t;
    }

    /** null 은 허용(직책 없음). 값이 있으면 사용 중인 공통코드여야 한다. */
    private void requireCode(String group, String code, String label) {
        if (code == null) {
            return;
        }
        CommonCode c = codes.findById(new CommonCodeId(group, code)).orElse(null);
        if (c == null) {
            throw AppointmentException.badRequest("없는 " + label + " 코드예요: " + code);
        }
        if (!c.isActive()) {
            throw AppointmentException.badRequest("사용하지 않는 " + label + " 코드예요: " + code);
        }
    }

    /**
     * 옮길 부서는 발령일에 이미 신설되어 있고 폐지(예정)가 아니어야 한다. 사원 입사 등록 규칙(EmployeeService)과 같다.
     * 폐지 예정 부서로 보내면 폐지일 뒤에도 재직자가 남는다.
     */
    private void requireDeptOpenOn(long deptId, LocalDate d) {
        OrgDept dept = depts.findById(deptId)
                .orElseThrow(() -> AppointmentException.badRequest("부서가 없어요: " + deptId));
        if (dept.getValidFrom().isAfter(d)) {
            throw AppointmentException.badRequest("부서 " + dept.getDeptCode() + "는 발령일(" + d + ")에 존속하지 않아요");
        }
        if (dept.getValidTo() != null) {
            throw AppointmentException.badRequest("부서 " + dept.getDeptCode() + "는 폐지되었거나 폐지가 예정되어 있어요(폐지일 "
                    + dept.getValidTo() + "). 다른 부서를 선택해 주세요");
        }
    }

    private static void requireUnconfirmed(HrAppointment a, String verb) {
        if (a.isConfirmed()) {
            throw AppointmentException.conflict("확정된 발령(" + a.getApptNo() + ")은 " + verb
                    + " 수 없어요. 바뀐 내용은 새 발령으로 등록해 주세요");
        }
    }

    private static Draft draftOf(AppointmentRequest r) {
        return new Draft(r.apptTypeCode().strip(), r.effectiveDate(), r.afterDeptId(),
                blankToNull(r.afterJobGradeCode()), blankToNull(r.afterJobTitleCode()), r.reason(),
                r.leaveStartDate(), r.leaveEndDate());
    }

    /** 확정용: 저장된 발령에서 유형이 바꾸는 이후 값만 가져온다(나머지는 확정 시점의 이전 값으로 다시 채움). */
    private static Draft draftOf(HrAppointment a) {
        AppointmentType t = AppointmentType.ofCode(a.getApptTypeCode())
                .orElseThrow(() -> AppointmentException.conflict("알 수 없는 발령 유형이에요: " + a.getApptTypeCode()));
        Snapshot after = a.after();
        return new Draft(a.getApptTypeCode(), a.getEffectiveDate(),
                t.changes(Field.DEPT) ? after.deptId() : null,
                t.changes(Field.GRADE) ? after.jobGradeCode() : null,
                t.changes(Field.TITLE) ? after.jobTitleCode() : null,
                a.getReason(), a.getLeaveStartDate(), a.getLeaveEndDate());
    }

    // ---------------------------------------------------------------- 읽기·잠금

    private HrAppointment load(long apptId) {
        return appointments.findById(apptId)
                .orElseThrow(() -> AppointmentException.notFound("발령이 없어요: " + apptId));
    }

    /** 쓰기용 1단계: 발령 엔티티를 올리지 않고 사원 ID만 읽는다. */
    private long empIdOf(long apptId) {
        return appointments.findEmpIdByApptId(apptId)
                .orElseThrow(() -> AppointmentException.notFound("발령이 없어요: " + apptId));
    }

    /** 쓰기용 2단계: 사원 잠금 뒤 발령 행을 잠가 읽는다(대기 중 다른 요청이 확정했으면 그 결과를 읽는다). */
    private HrAppointment loadForUpdate(long apptId) {
        return appointments.findForUpdate(apptId)
                .orElseThrow(() -> AppointmentException.notFound("발령이 없어요: " + apptId));
    }

    private Employee loadEmployee(long empId) {
        return employees.findById(empId).orElseThrow(() -> AppointmentException.notFound("사원이 없어요: " + empId));
    }

    /** 같은 사원의 발령 쓰기를 직렬화한다(입사 발령 중복·재직상태 검사가 엇갈리지 않게). */
    private Employee lockEmployee(long empId) {
        return employees.findForUpdate(empId)
                .orElseThrow(() -> AppointmentException.notFound("사원이 없어요: " + empId));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
