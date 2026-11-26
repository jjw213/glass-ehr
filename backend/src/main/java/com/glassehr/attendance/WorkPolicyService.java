package com.glassehr.attendance;

import com.glassehr.attendance.WorkPatternRules.Violation;
import com.glassehr.attendance.WorkPolicyDtos.DeleteResult;
import com.glassehr.attendance.WorkPolicyDtos.WorkPolicyRequest;
import com.glassehr.attendance.WorkPolicyDtos.WorkPolicyView;
import com.glassehr.attendance.WorkPolicyRules.Policy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근무 정책(기록·인정 규칙, #159). 법인마다 기본 정책 하나 + 근무유형별 재정의 하나(사용 중인 것 기준).
 * 다른 법인 정책·근무유형은 404. 법정 한도를 넘는 값은 422({@link WorkPolicyRules#check}).
 */
@Service
public class WorkPolicyService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WorkPolicyRepository policies;
    private final WorkPatternRepository patterns;
    private final EmpWorkPatternRepository assignments;
    private final WorkPatternLegalLimits limits;
    private final Clock clock;

    @Autowired
    public WorkPolicyService(WorkPolicyRepository policies, WorkPatternRepository patterns,
            EmpWorkPatternRepository assignments, WorkPatternLegalLimits limits) {
        this(policies, patterns, assignments, limits, Clock.system(SEOUL));
    }

    WorkPolicyService(WorkPolicyRepository policies, WorkPatternRepository patterns,
            EmpWorkPatternRepository assignments, WorkPatternLegalLimits limits, Clock clock) {
        this.policies = policies;
        this.patterns = patterns;
        this.assignments = assignments;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkPolicyView> list(String enterCd, boolean includeHidden) {
        return policies.findByEnterCdOrderByPolicyIdAsc(enterCd).stream()
                .filter(p -> includeHidden || p.isActive())
                .map(WorkPolicyView::of)
                .toList();
    }

    @Transactional
    public WorkPolicyView create(String enterCd, WorkPolicyRequest req, String actor) {
        WorkPatternRules.Spec spec = patternSpec(enterCd, req.patternId());
        Policy p = req.toPolicy();
        validate(p, spec);
        boolean taken = policies.findByEnterCdAndActive(enterCd, true).stream()
                .anyMatch(w -> java.util.Objects.equals(w.getPatternId(), req.patternId()));
        if (taken) {
            throw AttendanceException.conflict(req.patternId() == null ? "이 법인에는 이미 기본 정책이 있어요"
                    : "이 근무유형에는 이미 정책이 있어요");
        }
        try {
            return WorkPolicyView.of(policies.saveAndFlush(
                    new WorkPolicy(enterCd, req.patternId(), req.policyName().strip(), p, actor, now())));
        } catch (DataIntegrityViolationException e) {
            throw AttendanceException.conflict("같은 대상의 정책이 방금 만들어졌어요. 다시 확인해 주세요");
        }
    }

    @Transactional
    public WorkPolicyView update(String enterCd, long policyId, WorkPolicyRequest req, String actor) {
        WorkPolicy w = find(enterCd, policyId);
        if (!w.isActive()) {
            throw AttendanceException.conflict("숨긴 정책은 고칠 수 없어요");
        }
        Policy p = req.toPolicy();
        validate(p, patternSpec(enterCd, w.getPatternId()));
        w.apply(req.policyName().strip(), p, actor, now());
        return WorkPolicyView.of(w);
    }

    /** 쓰인 정책(이 정책이 적용된 근로시간 집계 행이 있음)은 숨기고, 아니면 지운다. */
    @Transactional
    public DeleteResult delete(String enterCd, long policyId, String actor) {
        WorkPolicy w = find(enterCd, policyId);
        if (policies.countSummaries(enterCd, w.getPatternId()) > 0) {
            w.hide(actor, now());
            return new DeleteResult(policyId, true);
        }
        policies.delete(w);
        return new DeleteResult(policyId, false);
    }

    /** 법인의 사용 중 정책(기본 + 근무유형별 재정의). 근로시간 집계·연장 신청이 쓴다. */
    @Transactional(readOnly = true)
    public Resolved resolved(String enterCd) {
        Policy def = null;
        Map<Long, Policy> byPattern = new HashMap<>();
        for (WorkPolicy w : policies.findByEnterCdAndActive(enterCd, true)) {
            if (w.getPatternId() == null) {
                def = w.toPolicy();
            } else {
                byPattern.put(w.getPatternId(), w.toPolicy());
            }
        }
        return new Resolved(def == null ? WorkPolicyRules.SYSTEM_DEFAULT : def, Map.copyOf(byPattern));
    }

    /** 사원이 그날 배정된 근무유형의 정책(없으면 법인 기본 → 시스템 기본). */
    @Transactional(readOnly = true)
    public Policy forEmployee(String enterCd, long empId, LocalDate date) {
        Long patternId = assignments.findByEnterCdAndEmpIdOrderByValidFromAsc(enterCd, empId).stream()
                .filter(a -> a.isEffectiveOn(date)).findFirst().map(EmpWorkPattern::getPatternId).orElse(null);
        return resolved(enterCd).forPattern(patternId);
    }

    /** 정책 해석 결과. 근무유형 재정의 → 법인 기본 → 시스템 기본 순. */
    public record Resolved(Policy defaultPolicy, Map<Long, Policy> byPattern) {
        public static final Resolved NONE = new Resolved(WorkPolicyRules.SYSTEM_DEFAULT, Map.of());

        public Policy forPattern(Long patternId) {
            return patternId == null ? defaultPolicy : byPattern.getOrDefault(patternId, defaultPolicy);
        }
    }

    private void validate(Policy p, WorkPatternRules.Spec spec) {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        List<Violation> v = WorkPolicyRules.check(p, spec, limits.asOf(today), limits.weeklyOvertimeLimitMinutes(today));
        if (!v.isEmpty()) {
            throw AttendanceException.rejected(v);
        }
    }

    private WorkPatternRules.Spec patternSpec(String enterCd, Long patternId) {
        if (patternId == null) {
            return null;
        }
        return patterns.findByEnterCdAndPatternId(enterCd, patternId)
                .orElseThrow(() -> AttendanceException.notFound("근무유형이 없어요: " + patternId)).spec();
    }

    private WorkPolicy find(String enterCd, long policyId) {
        return policies.findByEnterCdAndPolicyId(enterCd, policyId)
                .orElseThrow(() -> AttendanceException.notFound("근무 정책이 없어요: " + policyId));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SEOUL));
    }
}
