package com.glassehr.attendance;

import com.glassehr.attendance.WorkPatternDtos.AssignRequest;
import com.glassehr.attendance.WorkPatternDtos.AssignmentView;
import com.glassehr.attendance.WorkPatternDtos.WorkPatternRequest;
import com.glassehr.attendance.WorkPatternDtos.WorkPatternView;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근무유형 관리와 사원별 배정 이력(#25, docs/work-pattern-design.md).
 *
 * <ul>
 *   <li>근무유형 저장 시: 서울 기준 오늘 적용되는 legal_rate 값으로 1일·1주 한도(근로기준법 제50조), 휴게(제54조),
 *       주휴일(제55조 제1항)을 검사한다. 위반이 있으면 저장하지 않는다(422).</li>
 *   <li>배정 시: 배정 적용 시작일에 적용되는 legal_rate 값으로 같은 검사를 다시 한다(과거·미래 날짜 배정 대비).</li>
 *   <li>배정에 쓰인 근무유형은 시각·휴게·요일 구분을 바꿀 수 없다. 이름·사용 여부·비고만 바꾼다.</li>
 *   <li>배정 이력은 추가로만 쌓는다. 새 배정은 마지막 배정 시작일 뒤여야 하고, 마지막 배정을 새 시작일로 닫는다.</li>
 * </ul>
 */
@Service
public class WorkPatternService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WorkPatternRepository patterns;
    private final EmpWorkPatternRepository assignments;
    private final EmployeeRepository employees;
    private final WorkPatternLegalLimits limits;
    private final Clock clock;

    @Autowired
    public WorkPatternService(WorkPatternRepository patterns, EmpWorkPatternRepository assignments,
            EmployeeRepository employees, WorkPatternLegalLimits limits) {
        this(patterns, assignments, employees, limits, Clock.system(SEOUL));
    }

    WorkPatternService(WorkPatternRepository patterns, EmpWorkPatternRepository assignments,
            EmployeeRepository employees, WorkPatternLegalLimits limits, Clock clock) {
        this.patterns = patterns;
        this.assignments = assignments;
        this.employees = employees;
        this.limits = limits;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(SEOUL));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SEOUL));
    }

    @Transactional(readOnly = true)
    public List<WorkPatternView> list() {
        return patterns.findAllByOrderByPatternCodeAsc().stream()
                .map(p -> WorkPatternView.of(p, assignments.existsByPatternId(p.getPatternId())))
                .toList();
    }

    /** 저장하지 않고 검사만 한다(화면 미리보기). 기준일은 오늘. */
    @Transactional(readOnly = true)
    public WorkPatternRules.Result check(WorkPatternRules.Spec spec) {
        return evaluate(spec, today());
    }

    @Transactional
    public WorkPatternView create(WorkPatternRequest req) {
        if (patterns.existsByPatternCode(req.patternCode())) {
            throw AttendanceException.conflict("이미 있는 근무유형 코드예요: " + req.patternCode());
        }
        WorkPatternRules.Result r = requireValid(req.spec(), today());
        WorkPattern p = new WorkPattern(req.patternCode(), req.patternName().strip(), req.spec(), r,
                req.active() == null || req.active(), blankToNull(req.note()), now());
        return WorkPatternView.of(patterns.save(p), false);
    }

    @Transactional
    public WorkPatternView update(long patternId, WorkPatternRequest req) {
        WorkPattern p = patterns.findForUpdate(patternId)
                .orElseThrow(() -> AttendanceException.notFound("근무유형이 없어요: " + patternId));
        if (!p.getPatternCode().equals(req.patternCode())) {
            throw AttendanceException.badRequest("근무유형 코드는 바꿀 수 없어요");
        }
        boolean assigned = assignments.existsByPatternId(patternId);
        if (!p.spec().equals(req.spec())) {
            if (assigned) {
                throw AttendanceException.conflict(
                        "사원에게 배정된 근무유형은 시각·휴게·요일을 바꿀 수 없어요. 새 근무유형을 만들어 다시 배정해 주세요");
            }
            p.applySpec(req.spec(), requireValid(req.spec(), today()));
        }
        p.rename(req.patternName().strip(), req.active() == null || req.active(), blankToNull(req.note()), now());
        return WorkPatternView.of(p, assigned);
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> history(long empId) {
        if (!employees.existsById(empId)) {
            throw AttendanceException.notFound("사원이 없어요: " + empId);
        }
        return views(assignments.findByEmpIdOrderByValidFromAsc(empId));
    }

    /** 기준일에 적용되는 배정. 없으면 빈 값. */
    @Transactional(readOnly = true)
    public Optional<AssignmentView> effective(long empId, LocalDate date) {
        return history(empId).stream()
                .filter(a -> !a.validFrom().isAfter(date) && (a.validTo() == null || date.isBefore(a.validTo())))
                .findFirst();
    }

    @Transactional
    public AssignmentView assign(long empId, AssignRequest req, String actorId) {
        Objects.requireNonNull(req.validFrom(), "validFrom");
        Employee e = employees.findForUpdate(empId)
                .orElseThrow(() -> AttendanceException.notFound("사원이 없어요: " + empId));
        LocalDate from = req.validFrom();
        if (from.isBefore(e.getHireDate())) {
            throw AttendanceException.badRequest("적용 시작일이 입사일(" + e.getHireDate() + ")보다 빨라요");
        }
        // 퇴직일 당일까지는 재직으로 본다(EmployeeRepository 와 같은 기준).
        if (e.getRetireDate() != null && from.isAfter(e.getRetireDate())) {
            throw AttendanceException.badRequest("적용 시작일이 퇴직일(" + e.getRetireDate() + ") 뒤예요");
        }
        WorkPattern p = patterns.findForUpdate(req.patternId())
                .orElseThrow(() -> AttendanceException.notFound("근무유형이 없어요: " + req.patternId()));
        if (!p.isActive()) {
            throw AttendanceException.badRequest("사용 중지된 근무유형은 배정할 수 없어요: " + p.getPatternCode());
        }
        requireValid(p.spec(), from);

        List<EmpWorkPattern> rows = assignments.findByEmpIdOrderByValidFromAsc(empId);
        if (!rows.isEmpty()) {
            EmpWorkPattern last = rows.get(rows.size() - 1);
            if (!from.isAfter(last.getValidFrom())) {
                throw AttendanceException.badRequest("적용 시작일은 마지막 배정 시작일(" + last.getValidFrom()
                        + ") 뒤여야 해요. 이력은 추가로만 쌓아요");
            }
            if (last.getValidTo() == null || last.getValidTo().isAfter(from)) {
                last.closeAt(from);
            }
        }
        EmpWorkPattern saved = assignments.save(new EmpWorkPattern(empId, p.getPatternId(), from,
                blankToNull(req.reason()), actorId, now()));
        return view(saved, p);
    }

    private WorkPatternRules.Result evaluate(WorkPatternRules.Spec spec, LocalDate asOf) {
        List<WorkPatternRules.Violation> structure = WorkPatternRules.checkStructure(spec);
        if (!structure.isEmpty()) {
            // 형식 오류는 기준값 없이도 알려 준다.
            return new WorkPatternRules.Result(0, 0, structure);
        }
        return WorkPatternRules.check(spec, limits.asOf(asOf));
    }

    private WorkPatternRules.Result requireValid(WorkPatternRules.Spec spec, LocalDate asOf) {
        WorkPatternRules.Result r = evaluate(spec, asOf);
        if (!r.ok()) {
            throw AttendanceException.rejected(r.violations());
        }
        return r;
    }

    private List<AssignmentView> views(List<EmpWorkPattern> rows) {
        Map<Long, WorkPattern> cache = new HashMap<>();
        return rows.stream().map(a -> view(a, cache.computeIfAbsent(a.getPatternId(),
                id -> patterns.findById(id).orElseThrow()))).toList();
    }

    private static AssignmentView view(EmpWorkPattern a, WorkPattern p) {
        return new AssignmentView(a.getAssignId() == null ? 0 : a.getAssignId(), a.getEmpId(), p.getPatternId(),
                p.getPatternCode(), p.getPatternName(), a.getValidFrom(), a.getValidTo(), a.getAssignReason());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
