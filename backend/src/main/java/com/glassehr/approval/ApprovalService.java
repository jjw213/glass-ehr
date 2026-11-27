package com.glassehr.approval;

import com.glassehr.approval.ApprovalDtos.DocDetail;
import com.glassehr.approval.ApprovalDtos.DocSummary;
import com.glassehr.approval.ApprovalDtos.LogView;
import com.glassehr.approval.ApprovalDtos.PersonView;
import com.glassehr.approval.ApprovalDtos.RequestTypeView;
import com.glassehr.approval.ApprovalDtos.StepView;
import com.glassehr.auth.AuthUser;
import com.glassehr.auth.Permission;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공통 결재(docs/approval-line-design.md §5). 모듈은 {@link #submit}으로 결재를 올리고
 * {@link ApprovalCompletionHandler}로 결과를 받는다. 승인·반려·회수는 문서를 잠그고 처리하며 이력을 추가만 한다.
 */
@Service
public class ApprovalService {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 처리함·내 신청 목록 최대 건수(최근 순). */
    static final int LIST_LIMIT = 200;

    private final ApprovalTemplateRepository templates;
    private final ApprovalDocRepository docs;
    private final ApprovalActionLogRepository logs;
    private final EmployeeRepository employees;
    private final ApproverResolver resolver;
    private final Map<String, ApprovalCompletionHandler> handlers;
    private final Clock clock;

    @Autowired
    public ApprovalService(ApprovalTemplateRepository templates, ApprovalDocRepository docs,
            ApprovalActionLogRepository logs, EmployeeRepository employees, ApproverResolver.Directory directory,
            List<ApprovalCompletionHandler> handlers) {
        this(templates, docs, logs, employees, directory, handlers, Clock.system(SEOUL));
    }

    ApprovalService(ApprovalTemplateRepository templates, ApprovalDocRepository docs,
            ApprovalActionLogRepository logs, EmployeeRepository employees, ApproverResolver.Directory directory,
            List<ApprovalCompletionHandler> handlers, Clock clock) {
        this.templates = templates;
        this.docs = docs;
        this.logs = logs;
        this.employees = employees;
        this.resolver = new ApproverResolver(directory);
        this.handlers = byType(handlers);
        this.clock = clock;
    }

    static Map<String, ApprovalCompletionHandler> byType(List<ApprovalCompletionHandler> hs) {
        Map<String, ApprovalCompletionHandler> m = new LinkedHashMap<>();
        for (ApprovalCompletionHandler h : hs) {
            if (m.put(h.requestType(), h) != null) {
                throw new IllegalStateException("결재 신청 유형 " + h.requestType() + " 핸들러가 둘 이상이에요");
            }
        }
        return m;
    }

    public List<RequestTypeView> requestTypes() {
        return handlers.values().stream().map(h -> new RequestTypeView(h.requestType(), h.label())).toList();
    }

    boolean isKnownType(String requestType) {
        return handlers.containsKey(requestType);
    }

    /**
     * 결재를 올린다. 신청일(오늘, 서울) 기준 유효 템플릿으로 결재자를 풀어 스냅샷한다.
     * 템플릿이 없거나 결재자를 찾지 못하면 422(신청 거부). 모든 단계를 건너뛰면 바로 승인되고 핸들러가 불린다.
     *
     * enterCd 는 신청자(문서) 법인이다(#127). 템플릿·결재자는 이 법인 안에서만 찾는다.
     *
     * @return 결재 문서 ID
     */
    @Transactional
    public long submit(String enterCd, String requestType, long requestRef, String title, long requesterEmpId) {
        ApprovalCompletionHandler handler = handler(requestType);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        List<ApprovalTemplate> found = templates.findEffective(enterCd, requestType, today);
        if (found.isEmpty()) {
            throw ApprovalException.rejected(handler.label() + " 결재선이 설정되지 않아 신청할 수 없어요. "
                    + "관리자에게 결재선 설정을 요청해 주세요");
        }
        if (found.size() > 1) {
            throw ApprovalException.conflict(handler.label() + " 결재선이 " + today + "에 둘 이상 겹쳐 있어요");
        }
        ApprovalTemplate t = found.get(0);
        List<ApproverResolver.ResolvedStep> resolved = resolver.resolve(t.getSteps(), enterCd, requesterEmpId,
                today);
        ApprovalDoc doc = docs.save(new ApprovalDoc(enterCd, requestType, requestRef, title, requesterEmpId,
                t.getTemplateId(), resolved, now));
        long id = doc.getApprovalId();
        logs.save(new ApprovalActionLog(enterCd, id, null, ApprovalActionLog.Action.SUBMIT, requesterEmpId, now,
                null));
        for (ApproverResolver.ResolvedStep r : resolved) {
            if (r.skipped()) {
                logs.save(new ApprovalActionLog(enterCd, id, r.stepNo(), ApprovalActionLog.Action.SKIP,
                        requesterEmpId, now, "신청자 본인 단계 건너뜀"));
            }
        }
        if (doc.getStatus() == ApprovalDoc.Status.APPROVED) {
            handler.onApproved(enterCd, requestRef);
        }
        return id;
    }

    @Transactional
    public DocDetail approve(long approvalId, AuthUser user, String comment) {
        long emp = requireEmp(user);
        ApprovalDoc doc = lock(user.enterCd(), approvalId);
        int stepNo = currentStepNo(doc);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean done = doc.approve(emp, blankToNull(comment), now);
        logs.save(new ApprovalActionLog(doc.getEnterCd(), approvalId, stepNo, ApprovalActionLog.Action.APPROVE, emp,
                now, blankToNull(comment)));
        if (done) {
            handler(doc.getRequestType()).onApproved(doc.getEnterCd(), doc.getRequestRef());
        }
        return detail(doc, user);
    }

    @Transactional
    public DocDetail reject(long approvalId, AuthUser user, String comment) {
        long emp = requireEmp(user);
        if (comment == null || comment.isBlank()) {
            throw ApprovalException.badRequest("반려 사유를 적어 주세요");
        }
        ApprovalDoc doc = lock(user.enterCd(), approvalId);
        int stepNo = currentStepNo(doc);
        LocalDateTime now = LocalDateTime.now(clock);
        doc.reject(emp, comment.strip(), now);
        logs.save(new ApprovalActionLog(doc.getEnterCd(), approvalId, stepNo, ApprovalActionLog.Action.REJECT, emp,
                now, comment.strip()));
        handler(doc.getRequestType()).onRejected(doc.getEnterCd(), doc.getRequestRef());
        return detail(doc, user);
    }

    @Transactional
    public DocDetail withdraw(long approvalId, AuthUser user) {
        long emp = requireEmp(user);
        ApprovalDoc doc = lock(user.enterCd(), approvalId);
        LocalDateTime now = LocalDateTime.now(clock);
        doc.withdraw(emp, now);
        logs.save(new ApprovalActionLog(doc.getEnterCd(), approvalId, null, ApprovalActionLog.Action.WITHDRAW, emp,
                now, null));
        handler(doc.getRequestType()).onWithdrawn(doc.getEnterCd(), doc.getRequestRef());
        return detail(doc, user);
    }

    /** 결재함: 현재 법인 문서 중 지금 내 차례인 문서(오래된 순). */
    @Transactional(readOnly = true)
    public List<DocSummary> inbox(AuthUser user) {
        return summaries(user.enterCd(), docs.findInbox(user.enterCd(), requireEmp(user)));
    }

    /** 내가 처리(승인·반려)한 문서. */
    @Transactional(readOnly = true)
    public List<DocSummary> processed(AuthUser user) {
        return summaries(user.enterCd(),
                docs.findProcessedBy(user.enterCd(), requireEmp(user), PageRequest.of(0, LIST_LIMIT)));
    }

    /** 내가 올린 문서. */
    @Transactional(readOnly = true)
    public List<DocSummary> mine(AuthUser user) {
        return summaries(user.enterCd(), docs.findByEnterCdAndRequesterEmpIdOrderBySubmittedAtDesc(
                user.enterCd(), requireEmp(user), PageRequest.of(0, LIST_LIMIT)));
    }

    /** 상세. 신청자·결재자 후보 또는 전체 사원 조회 권한자만 볼 수 있다. 다른 법인 문서는 404(#127). */
    @Transactional(readOnly = true)
    public DocDetail get(long approvalId, AuthUser user) {
        ApprovalDoc doc = docs.findByEnterCdAndApprovalId(user.enterCd(), approvalId)
                .orElseThrow(() -> notFound(approvalId));
        boolean involved = user.empId() != null && doc.involves(user.empId());
        if (!involved && !user.has(Permission.EMP_READ_ALL)) {
            throw ApprovalException.forbidden("이 결재 문서를 볼 권한이 없어요");
        }
        return detail(doc, user);
    }

    /** 모듈이 원본 신청의 결재 문서를 찾을 때. */
    @Transactional(readOnly = true)
    public ApprovalDoc findByRequest(String enterCd, String requestType, long requestRef) {
        return docs.findByEnterCdAndRequestTypeAndRequestRef(enterCd, requestType, requestRef).orElse(null);
    }

    private ApprovalCompletionHandler handler(String requestType) {
        ApprovalCompletionHandler h = handlers.get(requestType);
        if (h == null) {
            throw ApprovalException.badRequest("알 수 없는 결재 신청 유형이에요: " + requestType);
        }
        return h;
    }

    /** 현재 법인 문서만 잠근다. 다른 법인 문서 ID 는 404(존재 여부를 드러내지 않음, #127). */
    private ApprovalDoc lock(String enterCd, long approvalId) {
        return docs.findForUpdate(enterCd, approvalId).orElseThrow(() -> notFound(approvalId));
    }

    private static int currentStepNo(ApprovalDoc doc) {
        return doc.getCurrentStepNo() == null ? 0 : doc.getCurrentStepNo();
    }

    private static ApprovalException notFound(long id) {
        return ApprovalException.notFound("결재 문서(" + id + ")를 찾을 수 없어요");
    }

    private static long requireEmp(AuthUser user) {
        if (user.empId() == null) {
            throw ApprovalException.forbidden("사원과 연결된 계정만 결재를 쓸 수 있어요");
        }
        return user.empId();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private List<DocSummary> summaries(String enterCd, List<ApprovalDoc> list) {
        Set<Long> ids = new HashSet<>();
        list.forEach(d -> ids.add(d.getRequesterEmpId()));
        Map<Long, String> names = names(enterCd, ids);
        return list.stream().map(d -> summary(d, names)).toList();
    }

    private DocSummary summary(ApprovalDoc d, Map<Long, String> names) {
        ApprovalCompletionHandler h = handlers.get(d.getRequestType());
        return new DocSummary(d.getApprovalId(), d.getRequestType(), h == null ? d.getRequestType() : h.label(),
                d.getRequestRef(), d.getTitle(), person(d.getRequesterEmpId(), names), d.getStatus(),
                d.getCurrentStepNo(), d.getSteps().size(), d.getSubmittedAt(), d.getCompletedAt());
    }

    private DocDetail detail(ApprovalDoc d, AuthUser user) {
        List<ApprovalActionLog> history = d.getApprovalId() == null ? List.of()
                : logs.findByEnterCdAndApprovalIdOrderByActionId(d.getEnterCd(), d.getApprovalId());
        Set<Long> ids = new HashSet<>();
        ids.add(d.getRequesterEmpId());
        for (ApprovalDocStep s : d.getSteps()) {
            ids.addAll(s.getApproverEmpIds());
            if (s.getActedEmpId() != null) {
                ids.add(s.getActedEmpId());
            }
        }
        history.forEach(l -> ids.add(l.getActorEmpId()));
        Map<Long, String> names = names(d.getEnterCd(), ids);
        List<StepView> steps = d.getSteps().stream().map(s -> new StepView(s.getStepNo(), s.getApproverKind(),
                s.getApproverRef(), s.getStepStatus(),
                s.getApproverEmpIds().stream().sorted().map(e -> person(e, names)).toList(),
                s.getActedEmpId() == null ? null : person(s.getActedEmpId(), names), s.getActedAt(),
                s.getStepComment())).toList();
        List<LogView> logViews = history.stream()
                .sorted(Comparator.comparing(ApprovalActionLog::getActionId))
                .map(l -> new LogView(l.getActionId(), l.getStepNo(), l.getAction(),
                        person(l.getActorEmpId(), names), l.getActedAt(), l.getActionComment()))
                .toList();
        Long me = user.empId();
        boolean canAct = me != null && d.isCurrentApprover(me);
        boolean canWithdraw = me != null && d.getStatus() == ApprovalDoc.Status.IN_PROGRESS
                && d.getRequesterEmpId().equals(me)
                && d.getSteps().stream().noneMatch(s -> s.getStepStatus() == ApprovalDocStep.Status.APPROVED);
        ApprovalCompletionHandler h = handlers.get(d.getRequestType());
        Object requestDetail = h == null ? null : h.requestDetail(d.getEnterCd(), d.getRequestRef());
        return new DocDetail(summary(d, names), requestDetail, steps, logViews, canAct, canWithdraw);
    }

    /** 이름은 문서 법인 사원에서만 읽는다(#127). */
    private Map<Long, String> names(String enterCd, Collection<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        for (Employee e : employees.findByEnterCdAndEmpIdIn(enterCd, ids)) {
            m.put(e.getEmpId(), e.getName());
        }
        return m;
    }

    private static PersonView person(long empId, Map<Long, String> names) {
        return new PersonView(empId, names.getOrDefault(empId, "사원 " + empId));
    }
}
