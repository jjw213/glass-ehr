package com.glassehr.employee;

import com.glassehr.code.CodeGroups;
import com.glassehr.code.CommonCode;
import com.glassehr.code.CommonCodeId;
import com.glassehr.code.CommonCodeRepository;
import com.glassehr.employee.Employee.BasicInfo;
import com.glassehr.employee.Employee.InitialAssignment;
import com.glassehr.employee.EmployeeDtos.EmployeeCreateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeSummary;
import com.glassehr.employee.EmployeeDtos.EmployeeUpdateRequest;
import com.glassehr.employee.EmployeeDtos.EmployeeView;
import com.glassehr.employee.EmployeeDtos.RetirementRequest;
import com.glassehr.legalrate.LegalRateException;
import com.glassehr.org.OrgDept;
import com.glassehr.org.OrgDeptRepository;
import com.glassehr.org.OrgWriteLock;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사원 기본정보(근로자 명부) 등록·수정·조회·퇴직 처리. 설계: docs/employee-design.md.
 *
 * <ul>
 *   <li>근로기준법 제41조·같은 법 시행령 제20조: 명부 기재사항을 받는다.</li>
 *   <li>현재 소속·직급·직책은 입사 등록 때만 넣고, 수정 API로는 바꾸지 않는다(인사발령 이슈에서 발령으로만 바꿈).</li>
 *   <li>근로기준법 제42조: 퇴직 처리 때 보존기한(퇴직일 + legal_rate 보존 연수)을 함께 저장한다.</li>
 *   <li>주민등록번호는 {@link RrnService}가 암호화해 따로 저장한다. 응답에는 마스킹 값만 넣는다.</li>
 * </ul>
 * 쓰기 권한(인사담당) 검사는 백엔드 인증·권한 이슈에서 붙인다(원문 조회 권한만 지금 검사).
 */
@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 고용형태 코드 중 계약기간 종료일이 반드시 있어야 하는 것(기간제법 제2조 제1호: 기간의 정함이 있는 근로계약). */
    static final String FIXED_TERM = "FIXED_TERM";

    private final EmployeeRepository employees;
    private final EmpNoGenerator empNos;
    private final CommonCodeRepository codes;
    private final OrgDeptRepository depts;
    private final RrnService rrns;
    private final RecordRetentionCalculator retention;
    private final OrgWriteLock orgLock;
    private final Clock clock;

    @Autowired
    public EmployeeService(EmployeeRepository employees, EmpNoGenerator empNos, CommonCodeRepository codes,
            OrgDeptRepository depts, RrnService rrns, RecordRetentionCalculator retention, OrgWriteLock orgLock) {
        this(employees, empNos, codes, depts, rrns, retention, orgLock, Clock.system(SEOUL));
    }

    EmployeeService(EmployeeRepository employees, EmpNoGenerator empNos, CommonCodeRepository codes,
            OrgDeptRepository depts, RrnService rrns, RecordRetentionCalculator retention, OrgWriteLock orgLock,
            Clock clock) {
        this.employees = employees;
        this.empNos = empNos;
        this.codes = codes;
        this.depts = depts;
        this.rrns = rrns;
        this.retention = retention;
        this.orgLock = orgLock;
        this.clock = clock.withZone(SEOUL);
    }

    // ---------------------------------------------------------------- 조회

    public List<EmployeeSummary> list(boolean includeRetired) {
        List<Employee> rows = includeRetired ? employees.findAllByOrderByEmpNoAsc()
                : employees.findByRetireDateIsNullOrderByEmpNoAsc();
        return rows.stream().map(EmployeeSummary::of).toList();
    }

    public EmployeeView get(long empId) {
        Employee e = load(empId);
        return EmployeeView.of(e, rrns.masked(empId).orElse(null));
    }

    // ---------------------------------------------------------------- 등록

    @Transactional
    public EmployeeView create(EmployeeCreateRequest req, RequestActor actor) {
        BasicInfo info = basicInfo(req.name(), req.gender(), req.birthDate(), req.address(), req.careerHistory(),
                req.jobDescription(), req.jobFamilyCode(), req.renewalDate(), req.contractStartDate(),
                req.contractEndDate(), req.employmentTypeCode(), req.weeklyContractMinutes(), req.workLocationCode(),
                req.employmentNote(), req.otherNote());
        validate(info, req.hireDate());
        InitialAssignment first = new InitialAssignment(req.deptId(), blankToNull(req.jobGradeCode()),
                blankToNull(req.jobTitleCode()));
        if (first.deptId() != null) {
            // 부서 폐지(OrgDeptService.close)와 같은 조직 쓰기 잠금을 잡아, 폐지 검사와 입사 등록이 엇갈려 둘 다 통과하지 않게 한다.
            orgLock.acquire();
            requireDeptOpenOn(first.deptId(), req.hireDate());
        }
        requireCode(CodeGroups.JOB_GRADE, first.jobGradeCode(), "직급", false);
        requireCode(CodeGroups.JOB_TITLE, first.jobTitleCode(), "직책", false);

        String empNo = empNos.next(req.siteCode(), req.hireDate());
        Employee e = employees.saveAndFlush(new Employee(empNo, req.siteCode(), req.hireDate(), info, first, now()));
        String masked = null;
        if (req.rrn() != null) {
            masked = rrns.save(e.getEmpId(), req.rrn(), actor);
        }
        return EmployeeView.of(e, masked);
    }

    // ---------------------------------------------------------------- 수정

    /** 명부 기본정보 수정. 소속·직급·직책·사번·입사일·퇴직 정보는 바꾸지 않는다. */
    @Transactional
    public EmployeeView update(long empId, EmployeeUpdateRequest req) {
        Employee e = loadForUpdate(empId);
        BasicInfo info = basicInfo(req.name(), req.gender(), req.birthDate(), req.address(), req.careerHistory(),
                req.jobDescription(), req.jobFamilyCode(), req.renewalDate(), req.contractStartDate(),
                req.contractEndDate(), req.employmentTypeCode(), req.weeklyContractMinutes(), req.workLocationCode(),
                req.employmentNote(), req.otherNote());
        validate(info, e.getHireDate());
        e.updateBasicInfo(info, now());
        employees.saveAndFlush(e);
        return EmployeeView.of(e, rrns.masked(empId).orElse(null));
    }

    // ---------------------------------------------------------------- 퇴직

    /**
     * 해고·퇴직·사망 처리. 시행령 제20조의 "해고, 퇴직 또는 사망한 경우 그 연월일과 사유"를 기록하고,
     * 근로기준법 제42조 보존기한을 계산해 저장한다. 한 번 퇴직 처리한 사원은 다시 처리하지 않는다(정정은 후속 이슈).
     */
    @Transactional
    public EmployeeView retire(long empId, RetirementRequest req) {
        Employee e = loadForUpdate(empId);
        if (e.isRetired()) {
            throw EmployeeException.conflict("이미 퇴직 처리된 사원이에요(퇴직일 " + e.getRetireDate() + ")");
        }
        if (req.retireDate().isBefore(e.getHireDate())) {
            throw EmployeeException.badRequest("퇴직일은 입사일(" + e.getHireDate() + ") 이후여야 해요");
        }
        String reason = req.reasonCode().strip();
        requireCode(CodeGroups.RETIREMENT_REASON, reason, "퇴직 사유", true);
        LocalDate until;
        try {
            until = retention.retentionUntil(req.retireDate());
        } catch (LegalRateException ex) {
            throw EmployeeException.conflict(
                    "근로자 명부 보존 기간 기준값(legal_rate " + ex.itemCode() + ")이 " + req.retireDate()
                            + " 기준으로 등록되어 있지 않아 퇴직 처리를 할 수 없어요. 관리자에게 기준값 입력을 요청해 주세요");
        }
        e.retire(req.retireDate(), reason, blankToNull(req.reasonDetail()), until, now());
        employees.saveAndFlush(e);
        return EmployeeView.of(e, rrns.masked(empId).orElse(null));
    }

    // ---------------------------------------------------------------- 검사

    Employee load(long empId) {
        return employees.findById(empId).orElseThrow(() -> EmployeeException.notFound(empId));
    }

    /** 쓰기용: 행을 잠가 읽는다(수정과 퇴직 처리가 서로의 결과를 덮어쓰지 않게). */
    private Employee loadForUpdate(long empId) {
        return employees.findForUpdate(empId).orElseThrow(() -> EmployeeException.notFound(empId));
    }

    private static BasicInfo basicInfo(String name, String gender, LocalDate birthDate, String address,
            String careerHistory, String jobDescription, String jobFamilyCode, LocalDate renewalDate,
            LocalDate contractStartDate, LocalDate contractEndDate, String employmentTypeCode,
            Integer weeklyContractMinutes, String workLocationCode, String employmentNote, String otherNote) {
        return new BasicInfo(name.strip(), gender, birthDate, address.strip(), blankToNull(careerHistory),
                jobDescription.strip(), blankToNull(jobFamilyCode), renewalDate, contractStartDate, contractEndDate,
                employmentTypeCode.strip(), weeklyContractMinutes, blankToNull(workLocationCode),
                blankToNull(employmentNote), blankToNull(otherNote));
    }

    /** 날짜 순서·코드 존재·계약기간 검사. DB CHECK(V009)와 같은 규칙을 먼저 검사해 알기 쉬운 메시지를 준다. */
    private void validate(BasicInfo i, LocalDate hireDate) {
        if (!i.birthDate().isBefore(hireDate)) {
            throw EmployeeException.badRequest("생년월일은 입사일보다 앞이어야 해요");
        }
        if (i.renewalDate() != null && i.renewalDate().isBefore(hireDate)) {
            throw EmployeeException.badRequest("고용갱신일은 입사일 이후여야 해요");
        }
        if (i.contractEndDate() != null && i.contractStartDate() == null) {
            throw EmployeeException.badRequest("계약 종료일을 쓰려면 계약 시작일도 입력해 주세요");
        }
        if (i.contractEndDate() != null && i.contractEndDate().isBefore(i.contractStartDate())) {
            throw EmployeeException.badRequest("계약 종료일은 계약 시작일 이후여야 해요");
        }
        requireCode(CodeGroups.EMPLOYMENT_TYPE, i.employmentTypeCode(), "고용형태", true);
        if (FIXED_TERM.equals(i.employmentTypeCode()) && i.contractEndDate() == null) {
            throw EmployeeException.badRequest("기간제 근로자는 계약기간(시작일·종료일)을 입력해 주세요");
        }
        requireCode(CodeGroups.JOB_FAMILY, i.jobFamilyCode(), "직군", false);
        requireCode(CodeGroups.WORK_LOCATION, i.workLocationCode(), "근무지", false);
    }

    /** 사용 중인 공통코드인지. required=false 이면 null 을 허용한다. */
    private void requireCode(String group, String code, String label, boolean required) {
        if (code == null) {
            if (required) {
                throw EmployeeException.badRequest(label + "을(를) 선택해 주세요");
            }
            return;
        }
        CommonCode c = codes.findById(new CommonCodeId(group, code)).orElse(null);
        if (c == null) {
            throw EmployeeException.badRequest("없는 " + label + " 코드예요: " + code);
        }
        if (!c.isActive()) {
            throw EmployeeException.badRequest("사용하지 않는 " + label + " 코드예요: " + code);
        }
    }

    /**
     * 첫 소속 부서는 입사일에 이미 신설되어 있고, 폐지일이 정해지지 않은 부서여야 한다.
     * 입사 등록은 소속 종료일·퇴직일 없이 기한 없는 배정을 만들므로, 폐지(예정) 부서에 넣으면 폐지일 뒤에도
     * 재직자가 남는다. 재직자가 있는 부서의 폐지를 거부하는 규칙(OrgDeptService.close)과 맞춘다.
     */
    private void requireDeptOpenOn(long deptId, LocalDate d) {
        OrgDept dept = depts.findById(deptId).orElseThrow(() -> EmployeeException.badRequest("부서가 없어요: " + deptId));
        if (dept.getValidFrom().isAfter(d)) {
            throw EmployeeException.badRequest("부서 " + dept.getDeptCode() + "는 입사일(" + d + ")에 존속하지 않아요");
        }
        if (dept.getValidTo() != null) {
            throw EmployeeException.badRequest("부서 " + dept.getDeptCode() + "는 폐지되었거나 폐지가 예정되어 있어요(폐지일 "
                    + dept.getValidTo() + "). 다른 부서를 선택해 주세요");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
