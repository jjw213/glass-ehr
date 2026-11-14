package com.glassehr.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사원 기본정보(근로자 명부). 테이블: V009__employee.sql. 설계: docs/employee-design.md.
 *
 * <p>근로기준법 제41조(근로자 명부)·같은 법 시행령 제20조(명부 기재사항)의 항목을 담는다:
 * 성명·성별·생년월일·주소·이력·종사 업무·고용(갱신)일과 계약기간 등 고용에 관한 사항·해고/퇴직/사망 일자와 사유·그 밖의 사항.
 *
 * <p>규칙
 * <ul>
 *   <li>{@code empId}는 불변 내부 키, 사번({@code empNo})도 등록 후 바꾸지 않는다(#9).</li>
 *   <li>현재 소속·직급·직책·재직상태는 확정된 인사발령 결과로만 바뀐다(#59). 입사 등록 때 첫 값을 받고, 그 뒤에는
 *       {@link EmployeeRepository#applyAppointment}(AppointmentService 의 발령 반영)만 바꾼다. 이 엔티티에는 바꾸는
 *       메서드가 없고 컬럼도 {@code updatable = false}다. 사원 등록·수정 API에는 이 값이 없다.</li>
 *   <li>주민등록번호는 이 테이블에 없다. {@link EmpRrn}에 암호문으로만 있다(개인정보 보호법 제24조의2·제29조).</li>
 * </ul>
 */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emp_id", nullable = false, updatable = false)
    private Long empId;

    @Column(name = "emp_no", nullable = false, updatable = false)
    private String empNo;

    @Column(name = "site_code", nullable = false, updatable = false)
    private String siteCode;

    // 시행령 제20조: 성명·성별·생년월일·주소
    @Column(name = "emp_name", nullable = false)
    private String name;

    @Column(name = "gender", nullable = false)
    private String gender;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "address", nullable = false)
    private String address;

    // 시행령 제20조: 이력
    @Column(name = "career_history")
    private String careerHistory;

    // 시행령 제20조: 종사하는 업무의 종류
    @Column(name = "job_description", nullable = false)
    private String jobDescription;

    @Column(name = "job_family_code")
    private String jobFamilyCode;

    // 시행령 제20조: 고용 또는 고용갱신 연월일, 계약기간, 그 밖의 고용에 관한 사항
    @Column(name = "hire_date", nullable = false, updatable = false)
    private LocalDate hireDate;

    @Column(name = "renewal_date")
    private LocalDate renewalDate;

    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    @Column(name = "employment_type_code", nullable = false)
    private String employmentTypeCode;

    @Column(name = "weekly_contract_minutes")
    private Integer weeklyContractMinutes;

    @Column(name = "work_location_code")
    private String workLocationCode;

    @Column(name = "employment_note")
    private String employmentNote;

    // 현재 소속·직급·직책·재직상태: 확정된 인사발령 결과로만 바뀐다(수정 API 없음).
    // 엔티티로는 바꿀 수 없고(updatable = false), EmployeeRepository.applyAppointment 만 바꾼다(#59)
    @Column(name = "current_dept_id", updatable = false)
    private Long currentDeptId;

    @Column(name = "job_grade_code", updatable = false)
    private String jobGradeCode;

    @Column(name = "job_title_code", updatable = false)
    private String jobTitleCode;

    // V019. 값은 com.glassehr.appointment.EmpStatus(ACTIVE·ON_LEAVE·RETIRED). 입사 등록 때는 재직
    @Column(name = "emp_status", nullable = false, updatable = false)
    private String empStatus = "ACTIVE";

    // 시행령 제20조: 해고·퇴직·사망 연월일과 사유
    @Column(name = "retire_date")
    private LocalDate retireDate;

    @Column(name = "retire_reason_code")
    private String retireReasonCode;

    @Column(name = "retire_reason_detail")
    private String retireReasonDetail;

    // 근로기준법 제42조: 퇴직일 + 보존 기간(legal_rate EMPLOYEE_RECORD_RETENTION_YEARS)
    @Column(name = "record_retention_until")
    private LocalDate recordRetentionUntil;

    // 시행령 제20조: 그 밖에 필요한 사항
    @Column(name = "other_note")
    private String otherNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Employee() {
    }

    /** 명부의 일반 기재사항. 등록·수정 API가 바꿀 수 있는 값만 모았다(소속·직급·직책·퇴직 정보 제외). */
    public record BasicInfo(String name, String gender, LocalDate birthDate, String address, String careerHistory,
            String jobDescription, String jobFamilyCode, LocalDate renewalDate, LocalDate contractStartDate,
            LocalDate contractEndDate, String employmentTypeCode, Integer weeklyContractMinutes,
            String workLocationCode, String employmentNote, String otherNote) {
    }

    /** 입사 시 첫 소속·직급·직책. 이후 변경은 인사발령으로만 한다. */
    public record InitialAssignment(Long deptId, String jobGradeCode, String jobTitleCode) {
    }

    Employee(String empNo, String siteCode, LocalDate hireDate, BasicInfo info, InitialAssignment first,
            LocalDateTime now) {
        this.empNo = empNo;
        this.siteCode = siteCode;
        this.hireDate = hireDate;
        this.currentDeptId = first.deptId();
        this.jobGradeCode = first.jobGradeCode();
        this.jobTitleCode = first.jobTitleCode();
        this.createdAt = now;
        apply(info, now);
    }

    /** 테스트용: id 를 지정한다. */
    static Employee withId(long empId, String empNo, LocalDate hireDate, BasicInfo info, InitialAssignment first) {
        Employee e = new Employee(empNo, empNo.substring(0, 3), hireDate, info, first, LocalDateTime.of(2026, 1, 1, 0, 0));
        e.empId = empId;
        return e;
    }

    void updateBasicInfo(BasicInfo info, LocalDateTime now) {
        apply(info, now);
    }

    private void apply(BasicInfo i, LocalDateTime now) {
        this.name = i.name();
        this.gender = i.gender();
        this.birthDate = i.birthDate();
        this.address = i.address();
        this.careerHistory = i.careerHistory();
        this.jobDescription = i.jobDescription();
        this.jobFamilyCode = i.jobFamilyCode();
        this.renewalDate = i.renewalDate();
        this.contractStartDate = i.contractStartDate();
        this.contractEndDate = i.contractEndDate();
        this.employmentTypeCode = i.employmentTypeCode();
        this.weeklyContractMinutes = i.weeklyContractMinutes();
        this.workLocationCode = i.workLocationCode();
        this.employmentNote = i.employmentNote();
        this.otherNote = i.otherNote();
        this.updatedAt = now;
    }

    /**
     * 해고·퇴직·사망 처리(시행령 제20조 기재사항). 보존기한은 호출자가 legal_rate 로 계산해 넘긴다
     * ({@link RecordRetentionCalculator}).
     */
    void retire(LocalDate retireDate, String reasonCode, String reasonDetail, LocalDate retentionUntil,
            LocalDateTime now) {
        this.retireDate = retireDate;
        this.retireReasonCode = reasonCode;
        this.retireReasonDetail = reasonDetail;
        this.recordRetentionUntil = retentionUntil;
        this.updatedAt = now;
    }

    public String getEmpStatus() {
        return empStatus;
    }

    public boolean isRetired() {
        return retireDate != null;
    }

    public Long getEmpId() {
        return empId;
    }

    public String getEmpNo() {
        return empNo;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getName() {
        return name;
    }

    public String getGender() {
        return gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getAddress() {
        return address;
    }

    public String getCareerHistory() {
        return careerHistory;
    }

    public String getJobDescription() {
        return jobDescription;
    }

    public String getJobFamilyCode() {
        return jobFamilyCode;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public LocalDate getRenewalDate() {
        return renewalDate;
    }

    public LocalDate getContractStartDate() {
        return contractStartDate;
    }

    public LocalDate getContractEndDate() {
        return contractEndDate;
    }

    public String getEmploymentTypeCode() {
        return employmentTypeCode;
    }

    public Integer getWeeklyContractMinutes() {
        return weeklyContractMinutes;
    }

    public String getWorkLocationCode() {
        return workLocationCode;
    }

    public String getEmploymentNote() {
        return employmentNote;
    }

    public Long getCurrentDeptId() {
        return currentDeptId;
    }

    public String getJobGradeCode() {
        return jobGradeCode;
    }

    public String getJobTitleCode() {
        return jobTitleCode;
    }

    public LocalDate getRetireDate() {
        return retireDate;
    }

    public String getRetireReasonCode() {
        return retireReasonCode;
    }

    public String getRetireReasonDetail() {
        return retireReasonDetail;
    }

    public LocalDate getRecordRetentionUntil() {
        return recordRetentionUntil;
    }

    public String getOtherNote() {
        return otherNote;
    }
}
