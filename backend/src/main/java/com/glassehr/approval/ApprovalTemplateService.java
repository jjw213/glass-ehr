package com.glassehr.approval;

import com.glassehr.approval.ApprovalDtos.TemplateRequest;
import com.glassehr.approval.ApprovalDtos.TemplateStepRequest;
import com.glassehr.approval.ApprovalDtos.TemplateView;
import com.glassehr.auth.AuthUser;
import com.glassehr.employee.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결재선 템플릿 관리(docs/approval-line-design.md §2). 기존 템플릿은 고치지 않고, 종료일을 넣은 뒤 새 템플릿을 추가한다.
 * 시작일은 오늘 이후여야 한다(이미 지난 날의 결재선을 바꾸지 않음). 같은 유형의 앞 템플릿보다 늦게 시작해야 한다.
 */
@Service
public class ApprovalTemplateService {

    /** ROLE 단계에 쓸 수 있는 역할(부여 역할). 부서장·일반 사원은 자동 역할이라 DEPT_HEAD 방식으로 지정한다. */
    static final Set<String> ASSIGNABLE_ROLES = Set.of(AuthUser.ROLE_ADMIN, AuthUser.ROLE_HR, AuthUser.ROLE_PAYROLL);

    private final ApprovalTemplateRepository templates;
    private final EmployeeRepository employees;
    private final ApprovalService approvals;
    private final Clock clock;

    @Autowired
    public ApprovalTemplateService(ApprovalTemplateRepository templates, EmployeeRepository employees,
            ApprovalService approvals) {
        this(templates, employees, approvals, Clock.system(ApprovalService.SEOUL));
    }

    ApprovalTemplateService(ApprovalTemplateRepository templates, EmployeeRepository employees,
            ApprovalService approvals, Clock clock) {
        this.templates = templates;
        this.employees = employees;
        this.approvals = approvals;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list(String requestType) {
        requireType(requestType);
        return templates.findByRequestTypeOrderByValidFromDesc(requestType).stream().map(TemplateView::of).toList();
    }

    @Transactional
    public TemplateView create(TemplateRequest req, AuthUser user) {
        requireType(req.requestType());
        LocalDate today = LocalDate.now(clock);
        if (req.validFrom().isBefore(today)) {
            throw ApprovalException.badRequest("적용 시작일은 오늘(" + today + ") 이후여야 해요");
        }
        List<ApprovalTemplate.Step> steps = new ArrayList<>();
        int no = 1;
        for (TemplateStepRequest s : req.steps()) {
            steps.add(step(no++, s));
        }
        Optional<ApprovalTemplate> latest = templates.lockByRequestType(req.requestType()).stream()
                .max(Comparator.comparing(ApprovalTemplate::getValidFrom));
        if (latest.isPresent()) {
            ApprovalTemplate prev = latest.get();
            if (!req.validFrom().isAfter(prev.getValidFrom())) {
                throw ApprovalException.conflict("적용 시작일은 지금 마지막 결재선의 시작일(" + prev.getValidFrom()
                        + ")보다 늦어야 해요");
            }
            prev.closeAt(req.validFrom());
            templates.saveAndFlush(prev);
        }
        ApprovalTemplate saved = templates.saveAndFlush(
                new ApprovalTemplate(req.requestType(), req.validFrom(), steps, user.loginId()));
        return TemplateView.of(saved);
    }

    private ApprovalTemplate.Step step(int no, TemplateStepRequest s) {
        String ref = s.approverRef() == null || s.approverRef().isBlank() ? null : s.approverRef().strip();
        ApproverKind kind = s.approverKind();
        if (!kind.needsRef()) {
            if (ref != null) {
                throw ApprovalException.badRequest(no + "단계: 부서장 지정에는 대상 값을 넣지 않아요");
            }
            return new ApprovalTemplate.Step(no, kind, null, s.skipIfSelf());
        }
        if (ref == null) {
            throw ApprovalException.badRequest(no + "단계: 대상(직책 코드·역할·사원)을 넣어 주세요");
        }
        if (ref.length() > 50) {
            throw ApprovalException.badRequest(no + "단계: 대상 값은 50자까지예요");
        }
        if (kind == ApproverKind.ROLE && !ASSIGNABLE_ROLES.contains(ref)) {
            throw ApprovalException.badRequest(no + "단계: 역할은 " + String.join("·", ASSIGNABLE_ROLES.stream().sorted()
                    .toList()) + " 중 하나예요");
        }
        if (kind == ApproverKind.EMPLOYEE) {
            long empId;
            try {
                empId = Long.parseLong(ref);
            } catch (NumberFormatException e) {
                throw ApprovalException.badRequest(no + "단계: 사원 지정은 사원 ID 숫자로 넣어 주세요");
            }
            if (!employees.existsById(empId)) {
                throw ApprovalException.badRequest(no + "단계: 사원(" + empId + ")을 찾을 수 없어요");
            }
        }
        return new ApprovalTemplate.Step(no, kind, ref, s.skipIfSelf());
    }

    private void requireType(String requestType) {
        if (requestType == null || !approvals.isKnownType(requestType)) {
            throw ApprovalException.badRequest("알 수 없는 결재 신청 유형이에요: " + requestType);
        }
    }
}
