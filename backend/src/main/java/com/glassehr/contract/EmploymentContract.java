package com.glassehr.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 근로계약 1건. 테이블: V012__employment_contract.sql. 설계: docs/employment-contract-design.md.
 *
 * <p>명시사항(근로기준법 제17조·같은 법 시행령 제8조, 기간제법 제17조)은 비어 있어도 저장한다. 누락은 {@link ContractTermsChecker}가
 * 경고로 알린다. 서면 교부일을 기록한 뒤에는 내용을 고치지 않는다(교부한 서면과 기록이 달라지지 않게). 행을 지우지 않는다(제42조 보존).
 */
@Entity
@Table(name = "employment_contract")
public class EmploymentContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_id", nullable = false, updatable = false)
    private Long contractId;

    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Column(name = "contract_type_code", nullable = false)
    private String contractTypeCode;

    @Column(name = "contract_date", nullable = false)
    private LocalDate contractDate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "wage_amount")
    private Long wageAmount;

    @Column(name = "wage_unit_code")
    private String wageUnitCode;

    @Column(name = "wage_components")
    private String wageComponents;

    @Column(name = "wage_calc_method")
    private String wageCalcMethod;

    @Column(name = "wage_payment_method")
    private String wagePaymentMethod;

    @Column(name = "weekly_contract_minutes")
    private Integer weeklyContractMinutes;

    @Column(name = "work_hours_text")
    private String workHoursText;

    @Column(name = "work_days_text")
    private String workDaysText;

    @Column(name = "holiday_text")
    private String holidayText;

    @Column(name = "leave_text")
    private String leaveText;

    @Column(name = "work_place")
    private String workPlace;

    @Column(name = "job_duty")
    private String jobDuty;

    @Column(name = "other_terms")
    private String otherTerms;

    @Column(name = "written_delivered_date")
    private LocalDate writtenDeliveredDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EmploymentContract() {
    }

    /** 계약 내용. 등록·수정 API가 바꿀 수 있는 값(서면 교부일 제외). */
    public record Terms(String contractTypeCode, LocalDate contractDate, LocalDate startDate, LocalDate endDate,
            Long wageAmount, String wageUnitCode, String wageComponents, String wageCalcMethod,
            String wagePaymentMethod, Integer weeklyContractMinutes, String workHoursText, String workDaysText,
            String holidayText, String leaveText, String workPlace, String jobDuty, String otherTerms) {
    }

    EmploymentContract(long empId, Terms terms, LocalDateTime now) {
        this.empId = empId;
        this.createdAt = now;
        apply(terms, now);
    }

    void update(Terms t, LocalDateTime now) {
        apply(t, now);
    }

    private void apply(Terms t, LocalDateTime now) {
        this.contractTypeCode = t.contractTypeCode();
        this.contractDate = t.contractDate();
        this.startDate = t.startDate();
        this.endDate = t.endDate();
        this.wageAmount = t.wageAmount();
        this.wageUnitCode = t.wageUnitCode();
        this.wageComponents = t.wageComponents();
        this.wageCalcMethod = t.wageCalcMethod();
        this.wagePaymentMethod = t.wagePaymentMethod();
        this.weeklyContractMinutes = t.weeklyContractMinutes();
        this.workHoursText = t.workHoursText();
        this.workDaysText = t.workDaysText();
        this.holidayText = t.holidayText();
        this.leaveText = t.leaveText();
        this.workPlace = t.workPlace();
        this.jobDuty = t.jobDuty();
        this.otherTerms = t.otherTerms();
        this.updatedAt = now;
    }

    /** 기간의 정함이 없는 앞 계약을 새 계약 시작 전날로 끝낸다(새 계약이 근로조건을 대체). */
    void endBefore(LocalDate nextStart, LocalDateTime now) {
        this.endDate = nextStart.minusDays(1);
        this.updatedAt = now;
    }

    void recordDelivery(LocalDate deliveredDate, LocalDateTime now) {
        this.writtenDeliveredDate = deliveredDate;
        this.updatedAt = now;
    }

    public Terms terms() {
        return new Terms(contractTypeCode, contractDate, startDate, endDate, wageAmount, wageUnitCode, wageComponents,
                wageCalcMethod, wagePaymentMethod, weeklyContractMinutes, workHoursText, workDaysText, holidayText,
                leaveText, workPlace, jobDuty, otherTerms);
    }

    public boolean isDelivered() {
        return writtenDeliveredDate != null;
    }

    /** 이 계약 기간과 [start, end](end NULL = 끝 없음)가 하루라도 겹치는지. */
    boolean overlaps(LocalDate start, LocalDate end) {
        boolean startsBeforeOtherEnds = end == null || !startDate.isAfter(end);
        boolean endsAfterOtherStarts = endDate == null || !endDate.isBefore(start);
        return startsBeforeOtherEnds && endsAfterOtherStarts;
    }

    public Long getContractId() {
        return contractId;
    }

    public Long getEmpId() {
        return empId;
    }

    public String getContractTypeCode() {
        return contractTypeCode;
    }

    public LocalDate getContractDate() {
        return contractDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getWrittenDeliveredDate() {
        return writtenDeliveredDate;
    }
}
