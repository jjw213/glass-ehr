package com.glassehr.retirement;

import com.glassehr.code.CodeGroups;
import com.glassehr.code.CodeSysTypes.RetirementReason;
import com.glassehr.code.CommonCode;
import com.glassehr.code.CommonCodeId;
import com.glassehr.code.CommonCodeRepository;
import com.glassehr.contract.EmploymentContract;
import com.glassehr.contract.EmploymentContractRepository;
import com.glassehr.employee.Employee;
import com.glassehr.employee.EmployeeDtos;
import com.glassehr.employee.EmployeeRepository;
import com.glassehr.employee.EmployeeService;
import com.glassehr.retirement.DismissalNoticeChecker.NoticeCheck;
import com.glassehr.retirement.DismissalNoticeChecker.Result;
import com.glassehr.retirement.RetirementDtos.CertificateItem;
import com.glassehr.retirement.RetirementDtos.CertificateRequest;
import com.glassehr.retirement.RetirementDtos.CertificateView;
import com.glassehr.retirement.RetirementDtos.ChecklistItemView;
import com.glassehr.retirement.RetirementDtos.ChecklistTemplateItem;
import com.glassehr.retirement.RetirementDtos.ChecklistUpdateRequest;
import com.glassehr.retirement.RetirementDtos.NoticeView;
import com.glassehr.retirement.RetirementDtos.RetentionExpiredRow;
import com.glassehr.retirement.RetirementDtos.RetirementRequest;
import com.glassehr.retirement.RetirementDtos.RetirementView;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 퇴직 처리(체크리스트·해고 예고 확인)·사용증명서 발급·보존기한 경과 목록(#18). 설계: docs/retirement-design.md.
 * 근로자 명부의 퇴직일·사유·보존기한 기록은 {@link EmployeeService#retire}가 맡고, 여기서는 같은 트랜잭션에서 부른다.
 * 퇴직금 계산은 급여 모듈에서 한다.
 */
@Service
public class RetirementService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final EmployeeService employeeService;
    private final EmployeeRepository employees;
    private final RetirementCaseRepository cases;
    private final RetirementChecklistItemRepository checklist;
    private final EmploymentCertificateRepository certificates;
    private final EmploymentContractRepository contracts;
    private final CommonCodeRepository codes;
    private final DismissalNoticeChecker noticeChecker;
    private final Clock clock;

    @Autowired
    public RetirementService(EmployeeService employeeService, EmployeeRepository employees,
            RetirementCaseRepository cases, RetirementChecklistItemRepository checklist,
            EmploymentCertificateRepository certificates, EmploymentContractRepository contracts,
            CommonCodeRepository codes, DismissalNoticeChecker noticeChecker) {
        this(employeeService, employees, cases, checklist, certificates, contracts, codes, noticeChecker,
                Clock.system(SEOUL));
    }

    RetirementService(EmployeeService employeeService, EmployeeRepository employees, RetirementCaseRepository cases,
            RetirementChecklistItemRepository checklist, EmploymentCertificateRepository certificates,
            EmploymentContractRepository contracts, CommonCodeRepository codes, DismissalNoticeChecker noticeChecker,
            Clock clock) {
        this.employeeService = employeeService;
        this.employees = employees;
        this.cases = cases;
        this.checklist = checklist;
        this.certificates = certificates;
        this.contracts = contracts;
        this.codes = codes;
        this.noticeChecker = noticeChecker;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 퇴직 처리

    /** 등록 전 화면용 체크리스트 미리보기. 해고는 예고 확인 전이라 수당 검토 항목을 넣어 보여 준다. */
    public List<ChecklistTemplateItem> template(String enterCd, String reasonCode) {
        RetirementReason type = reasonType(enterCd, Objects.requireNonNullElse(reasonCode, "").strip());
        NoticeCheck check = type == RetirementReason.DISMISSAL ? NoticeCheck.UNKNOWN : NoticeCheck.NOT_APPLICABLE;
        return RetirementChecklist.forReason(type, check).stream()
                .map(i -> new ChecklistTemplateItem(i.code(), i.label(), i.legalBasis()))
                .toList();
    }

    /**
     * 퇴직 처리 등록. 해고면 예고일·서면통지일이 없으면 400(근로기준법 제26조·제27조),
     * 예고기간 미달은 막지 않고 {@code warnings}로 알린다.
     * enterCd 는 세션의 현재 법인. 다른 법인 사원은 404(#123).
     */
    @Transactional
    public RetirementView register(String enterCd, RetirementRequest req, String actor) {
        String reason = req.reasonCode().strip();
        RetirementReason type = reasonType(enterCd, reason);
        // 근로기준법 제26조·제27조 검사 대상은 처리방식이 해고(DISMISSAL)인 모든 퇴직사유 코드(#141)
        boolean dismissal = type == RetirementReason.DISMISSAL;
        validateNotice(req, dismissal);

        // 근로자 명부 퇴직일·사유·보존기한(근로기준법 제42조) — 사원 잠금·중복 퇴직·사유 코드 검사 포함
        employeeService.retire(enterCd, req.empId(),
                new EmployeeDtos.RetirementRequest(req.retireDate(), reason, req.reasonDetail()));
        if (cases.existsByEnterCdAndEmpId(enterCd, req.empId())) {
            throw RetirementException.conflict("이미 퇴직 처리가 등록된 사원이에요");
        }
        Employee e = employees.findByEnterCdAndEmpId(enterCd, req.empId())
                .orElseThrow(() -> RetirementException.notFound("사원을 찾을 수 없어요: " + req.empId()));

        Result notice = dismissal
                ? noticeChecker.check(e.getHireDate(), req.dismissalNoticeDate(), req.retireDate(), req.noticeExempt())
                : new Result(NoticeCheck.NOT_APPLICABLE, null, null, null, List.of());
        LocalDateTime now = now();
        RetirementCase c = cases.saveAndFlush(new RetirementCase(e.getEnterCd(), req.empId(), reason, type,
                req.retireDate(), req.dismissalNoticeDate(), req.writtenNoticeDate(), req.noticeExempt(), notice.check(),
                notice.noticeDays(), notice.shortfallDays(), actor, now));
        List<RetirementChecklistItem> items = new ArrayList<>();
        for (RetirementChecklist.Item t : RetirementChecklist.forReason(type, notice.check())) {
            RetirementChecklistItem i = new RetirementChecklistItem(c.getEnterCd(), c.getCaseId(), t);
            if (RetirementChecklist.autoDone(t.code())) {
                i.mark(true, "등록 시 입력(예고일·서면통지일)", actor, now);
            }
            items.add(i);
        }
        checklist.saveAll(items);
        return view(c, e, items, notice.warnings());
    }

    /**
     * 퇴직사유 코드 → 처리방식(#141). 공통코드는 법인별이라 그 법인에서 찾고, 없으면 400.
     * 사용 안 함 여부는 근로자 명부 기록({@link EmployeeService#retire})이 검사한다.
     */
    private RetirementReason reasonType(String enterCd, String reasonCode) {
        CommonCode c = codes.findByEnterCdAndGroupCodeAndCode(enterCd, CodeGroups.RETIREMENT_REASON, reasonCode)
                .orElseThrow(() -> RetirementException.badRequest("없는 퇴직사유 코드예요: " + reasonCode));
        return RetirementReason.of(c);
    }

    private static void validateNotice(RetirementRequest req, boolean dismissal) {
        if (!dismissal) {
            if (req.dismissalNoticeDate() != null || req.writtenNoticeDate() != null || req.noticeExempt() != null) {
                throw RetirementException.badRequest("해고 예고·서면통지 항목은 퇴직 유형이 해고일 때만 입력해요");
            }
            return;
        }
        // 근로기준법 제26조: 해고 예고
        if (req.dismissalNoticeDate() == null) {
            throw RetirementException.badRequest("해고는 해고 예고일을 입력해야 해요(근로기준법 제26조)");
        }
        // 근로기준법 제27조: 해고사유와 해고시기를 서면으로 통지해야 효력이 있다
        if (req.writtenNoticeDate() == null) {
            throw RetirementException.badRequest("해고는 해고사유·해고시기 서면통지일을 입력해야 해요(근로기준법 제27조)");
        }
        if (req.dismissalNoticeDate().isAfter(req.retireDate())) {
            throw RetirementException.badRequest("해고 예고일은 해고일(퇴직일)보다 늦을 수 없어요");
        }
        if (req.writtenNoticeDate().isAfter(req.retireDate())) {
            throw RetirementException.badRequest("서면통지일은 해고일(퇴직일)보다 늦을 수 없어요");
        }
    }

    /** 법인 조건(#124): 그 법인 사원의 퇴직 처리만. 다른 법인 사원은 404. */
    public RetirementView get(String enterCd, long empId) {
        RetirementCase c = cases.findByEnterCdAndEmpId(enterCd, empId)
                .orElseThrow(() -> RetirementException.notFound("퇴직 처리 기록이 없어요: 사원 " + empId));
        Employee e = loadEmployee(enterCd, empId);
        return view(c, e, checklist.findByEnterCdAndCaseIdOrderBySortOrderAsc(enterCd, c.getCaseId()), List.of());
    }

    @Transactional
    public RetirementView updateChecklist(String enterCd, long empId, String itemCode, ChecklistUpdateRequest req,
            String actor) {
        RetirementCase c = cases.findByEnterCdAndEmpId(enterCd, empId)
                .orElseThrow(() -> RetirementException.notFound("퇴직 처리 기록이 없어요: 사원 " + empId));
        RetirementChecklistItem item = checklist.findByEnterCdAndCaseIdAndItemCode(enterCd, c.getCaseId(), itemCode)
                .orElseThrow(() -> RetirementException.notFound("체크리스트 항목이 없어요: " + itemCode));
        String note = req.note() == null || req.note().isBlank() ? null : req.note().strip();
        item.mark(req.done(), note, actor, now());
        checklist.saveAndFlush(item);
        return get(enterCd, empId);
    }

    private RetirementView view(RetirementCase c, Employee e, List<RetirementChecklistItem> items,
            List<String> warnings) {
        NoticeView notice = new NoticeView(c.getNoticeCheck(), c.getDismissalNoticeDate(), c.getWrittenNoticeDate(),
                c.getNoticeExempt(), c.getNoticeDays(), c.getNoticeShortfallDays());
        List<ChecklistItemView> list = items.stream()
                .sorted(Comparator.comparingInt(RetirementChecklistItem::getSortOrder))
                .map(ChecklistItemView::of).toList();
        return new RetirementView(c.getCaseId(), c.getEmpId(), e.getEmpNo(), e.getName(), c.getRetireReasonCode(),
                c.getRetireDate(), e.getRecordRetentionUntil(), notice, list, List.copyOf(warnings), c.getCreatedBy(),
                c.getCreatedAt());
    }

    // ---------------------------------------------------------------- 보존기한

    /**
     * 보존기한이 지난 퇴직자 서류 목록. 보존기한(근로기준법 제42조, employee.record_retention_until)은 그날까지 보존하는
     * 마지막 날이라 {@code asOf}보다 앞선 사원만 담는다. 파기는 이 목록을 보고 사람이 한다(자동 삭제 없음).
     */
    public List<RetentionExpiredRow> retentionExpired(String enterCd, LocalDate asOf) {
        LocalDate day = asOf == null ? today() : asOf;
        return employees.findByEnterCdAndRecordRetentionUntilBeforeOrderByRecordRetentionUntilAscEmpNoAsc(enterCd, day)
                .stream()
                .map(e -> new RetentionExpiredRow(e.getEmpId(), e.getEmpNo(), e.getName(), e.getRetireDate(),
                        e.getRetireReasonCode(), e.getRecordRetentionUntil(),
                        ChronoUnit.DAYS.between(e.getRecordRetentionUntil(), day)))
                .toList();
    }

    // ---------------------------------------------------------------- 사용증명서

    /**
     * 사용증명서 발급. 근로기준법 제39조 제1항: 사용 기간, 업무 종류, 지위와 임금, 그 밖에 필요한 사항.
     * 같은 조 제2항: 근로자가 요구한 사항만 적는다 — 요구하지 않은 항목은 값을 채우지 않는다.
     * 성명은 증명 대상을 가리키려고 항상 적는다.
     */
    @Transactional
    public CertificateView issueCertificate(String enterCd, long empId, CertificateRequest req, String actor) {
        Employee e = loadEmployee(enterCd, empId);
        Set<CertificateItem> items = EnumSet.copyOf(req.items());
        LocalDate useFrom = null;
        LocalDate useTo = null;
        if (items.contains(CertificateItem.USE_PERIOD)) {
            useFrom = e.getHireDate();
            useTo = e.getRetireDate(); // 재직 중이면 비움(화면에 '현재 재직 중')
        }
        String jobType = items.contains(CertificateItem.JOB_TYPE) ? e.getJobDescription() : null;
        String position = items.contains(CertificateItem.POSITION) ? position(e) : null;
        Long wage = null;
        String wageUnit = null;
        if (items.contains(CertificateItem.WAGE)) {
            EmploymentContract.Terms t = latestWageTerms(e, req.requestedOn());
            wage = t.wageAmount();
            wageUnit = t.wageUnitCode();
        }
        String retireReason = null;
        if (items.contains(CertificateItem.RETIRE_REASON)) {
            if (e.getRetireReasonCode() == null) {
                throw RetirementException.badRequest("퇴직하지 않은 사원은 퇴직 사유를 적을 수 없어요");
            }
            retireReason = codeName(e.getEnterCd(), CodeGroups.RETIREMENT_REASON, e.getRetireReasonCode());
        }
        String itemsText = items.stream().map(Enum::name).collect(Collectors.joining(","));
        String purpose = req.purpose() == null || req.purpose().isBlank() ? null : req.purpose().strip();
        EmploymentCertificate cert = certificates.saveAndFlush(new EmploymentCertificate(e.getEnterCd(), empId, req.requestedOn(),
                itemsText, purpose, new EmploymentCertificate.Content(e.getName(), useFrom, useTo, jobType, position,
                        wage, wageUnit, retireReason), actor, now()));
        return view(cert, e.getEmpNo());
    }

    public List<CertificateView> certificates(String enterCd, long empId) {
        Employee e = loadEmployee(enterCd, empId);
        return certificates.findByEnterCdAndEmpIdOrderByIssuedAtDescCertIdDesc(enterCd, empId).stream()
                .map(c -> view(c, e.getEmpNo())).toList();
    }

    /** 다른 법인 사용증명서 ID 는 404(존재 여부를 드러내지 않음, #124). */
    public CertificateView certificate(String enterCd, long certId) {
        EmploymentCertificate c = certificates.findByEnterCdAndCertId(enterCd, certId)
                .orElseThrow(() -> RetirementException.notFound("사용증명서를 찾을 수 없어요: " + certId));
        return view(c, loadEmployee(enterCd, c.getEmpId()).getEmpNo());
    }

    /** 지위: 직급·직책 이름. 둘 다 없으면 409. */
    private String position(Employee e) {
        List<String> parts = new ArrayList<>();
        if (e.getJobGradeCode() != null) {
            parts.add(codeName(e.getEnterCd(), CodeGroups.JOB_GRADE, e.getJobGradeCode()));
        }
        if (e.getJobTitleCode() != null) {
            parts.add(codeName(e.getEnterCd(), CodeGroups.JOB_TITLE, e.getJobTitleCode()));
        }
        if (parts.isEmpty()) {
            throw RetirementException.conflict("사원에게 직급·직책이 없어 지위를 적을 수 없어요");
        }
        return String.join(" / ", parts);
    }

    /**
     * 임금: 청구일까지 이미 퇴직했으면 퇴직일, 재직 중이거나 퇴직 예정이면 청구일 기준으로 시작한 계약 중 가장 늦은 것의 임금.
     * 없으면 409.
     */
    private EmploymentContract.Terms latestWageTerms(Employee e, LocalDate requestedOn) {
        LocalDate retire = e.getRetireDate();
        LocalDate asOf = retire != null && !retire.isAfter(requestedOn) ? retire : requestedOn;
        return contracts.findByEnterCdAndEmpIdOrderByStartDateAsc(e.getEnterCd(), e.getEmpId()).stream()
                .map(EmploymentContract::terms)
                .filter(t -> t.wageAmount() != null && !t.startDate().isAfter(asOf))
                .reduce((a, b) -> b)
                .orElseThrow(() -> RetirementException.conflict("임금이 적힌 근로계약이 없어 임금을 적을 수 없어요"));
    }

    /** 공통코드는 법인별(#121)이라 사원의 법인에서 찾는다. */
    private String codeName(String enterCd, String group, String code) {
        return codes.findByEnterCdAndGroupCodeAndCode(enterCd, group, code).map(CommonCode::getCodeName).orElse(code);
    }

    private static CertificateView view(EmploymentCertificate c, String empNo) {
        List<CertificateItem> items = Arrays.stream(c.getRequestedItems().split(","))
                .map(String::strip).map(CertificateItem::valueOf).sorted().toList();
        return new CertificateView(c.getCertId(), c.getEmpId(), empNo, c.getRequestedOn(), items, c.getPurpose(),
                c.getEmpName(), c.getUseFrom(), c.getUseTo(), c.getJobType(), c.getPositionText(), c.getWageAmount(),
                c.getWageUnitCode(), c.getRetireReason(), c.getIssuedBy(), c.getIssuedAt());
    }

    // ---------------------------------------------------------------- 공통

    private Employee loadEmployee(String enterCd, long empId) {
        return employees.findByEnterCdAndEmpId(enterCd, empId)
                .orElseThrow(() -> RetirementException.notFound("사원을 찾을 수 없어요: " + empId));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SEOUL));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(SEOUL)).truncatedTo(ChronoUnit.MICROS);
    }
}
