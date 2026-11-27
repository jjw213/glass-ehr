package com.glassehr.approval;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 템플릿 단계를 신청일 기준 실제 결재자로 푼다(docs/approval-line-design.md §2·§3).
 * <ul>
 *   <li>신청자 본인은 결재자 후보에서 뺀다(자기 신청을 자기가 승인하지 않음).</li>
 *   <li>후보가 본인뿐이면 {@code skip_if_self} 단계는 건너뛰고, 아니면 신청을 거부한다.</li>
 *   <li>결재자를 찾지 못하면(부서장 공석, 해당 역할·직책 재직자 없음) 신청을 거부한다(2026-11-19 사람 답변 1번안).</li>
 *   <li>ROLE·POSITION 은 재직 중인 후보 전원을 스냅샷에 넣고, 그중 한 명이 결재한다(같은 답변).</li>
 * </ul>
 * 재직(ACTIVE)이 아닌 사원(휴직·퇴직)은 결재자가 될 수 없다.
 */
public class ApproverResolver {

    /**
     * 결재자 조회에 필요한 인사·조직 정보. 모든 조회는 결재 문서 법인(enterCd) 안에서만 한다(#122·#127).
     * 다른 법인 사원은 재직자가 아니라고 답해 결재선에 들어가지 않게 한다.
     */
    public interface Directory {
        /** 기준일 그 법인 사원의 소속 부서(없으면 empty). */
        Optional<Long> deptOf(String enterCd, long empId, LocalDate date);

        /** 기준일 그 법인의 부서 정보(없으면 empty). */
        Optional<DeptInfo> dept(String enterCd, long deptId, LocalDate date);

        /** 그 법인에서 그 직책을 가진 재직자. */
        Set<Long> activeWithTitle(String enterCd, String titleCode);

        /** 그 법인에서 그 역할을 받은 사용 중 계정의 재직자. */
        Set<Long> activeWithRole(String enterCd, String roleCode);

        /** 그 법인 사원이고 기준일에 재직(휴직·퇴직 아님)인가. */
        boolean isActive(String enterCd, long empId, LocalDate date);
    }

    /** 부서장(공석이면 null)과 상위 부서(최상위면 null). */
    public record DeptInfo(Long headEmpId, Long parentDeptId) {
    }

    /** 풀린 단계. skipped 이면 후보가 비어 있을 수 있다. */
    public record ResolvedStep(int stepNo, ApproverKind kind, String ref, Set<Long> approverEmpIds, boolean skipped) {
    }

    private final Directory dir;

    public ApproverResolver(Directory dir) {
        this.dir = dir;
    }

    /** enterCd 는 결재 문서(신청자) 법인. 그 법인 재직자만 결재자가 된다. */
    public List<ResolvedStep> resolve(List<ApprovalTemplate.Step> steps, String enterCd, long requesterEmpId,
            LocalDate date) {
        List<ResolvedStep> out = new ArrayList<>();
        for (ApprovalTemplate.Step s : steps) {
            Set<Long> found = new TreeSet<>();
            for (Long e : candidates(s, enterCd, requesterEmpId, date)) {
                if (e != null && dir.isActive(enterCd, e, date)) {
                    found.add(e);
                }
            }
            boolean hadSelf = found.remove(requesterEmpId);
            if (!found.isEmpty()) {
                out.add(new ResolvedStep(s.getStepNo(), s.getApproverKind(), s.getApproverRef(), found, false));
            } else if (hadSelf && s.isSkipIfSelf()) {
                out.add(new ResolvedStep(s.getStepNo(), s.getApproverKind(), s.getApproverRef(), Set.of(), true));
            } else if (hadSelf) {
                throw ApprovalException.rejected(s.getStepNo() + "단계 결재자가 신청자 본인뿐이라 신청할 수 없어요. "
                        + "관리자에게 결재선 설정을 요청해 주세요");
            } else {
                throw ApprovalException.rejected(s.getStepNo() + "단계 결재자(" + describe(s)
                        + ")를 찾지 못해 신청할 수 없어요. 관리자에게 결재선·조직 설정을 요청해 주세요");
            }
        }
        return out;
    }

    private Set<Long> candidates(ApprovalTemplate.Step s, String enter, long requester, LocalDate date) {
        return switch (s.getApproverKind()) {
            case DEPT_HEAD -> dir.deptOf(enter, requester, date)
                    .flatMap(d -> dir.dept(enter, d, date))
                    .map(ApproverResolver::head)
                    .orElse(Set.of());
            case UPPER_DEPT_HEAD -> dir.deptOf(enter, requester, date)
                    .flatMap(d -> dir.dept(enter, d, date))
                    .map(DeptInfo::parentDeptId)
                    .flatMap(p -> dir.dept(enter, p, date))
                    .map(ApproverResolver::head)
                    .orElse(Set.of());
            case POSITION -> dir.activeWithTitle(enter, s.getApproverRef());
            case ROLE -> dir.activeWithRole(enter, s.getApproverRef());
            case EMPLOYEE -> Set.of(Long.parseLong(s.getApproverRef()));
        };
    }

    private static Set<Long> head(DeptInfo d) {
        return d.headEmpId() == null ? Set.of() : Set.of(d.headEmpId());
    }

    private static String describe(ApprovalTemplate.Step s) {
        return switch (s.getApproverKind()) {
            case DEPT_HEAD -> "부서장";
            case UPPER_DEPT_HEAD -> "상위 부서장";
            case POSITION -> "직책 " + s.getApproverRef();
            case ROLE -> "역할 " + s.getApproverRef();
            case EMPLOYEE -> "사원 " + s.getApproverRef();
        };
    }
}
