package com.glassehr.employee;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 사원 API 요청·응답 형식. 길이 제한은 V009 컬럼 길이와 같다. */
public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    /** 1주 소정근로시간(분) 입력 상한: 1주 전체 분(7일 × 24시간 × 60분). 법령 기준값이 아니라 입력 오류 방지용이다. */
    static final int MINUTES_PER_WEEK = 7 * 24 * 60;

    /**
     * 입사 등록. 사번은 서버가 채번한다(사업장코드-입사연도-순번).
     * deptId·jobGradeCode·jobTitleCode 는 입사 시 첫 값이며, 이후에는 인사발령으로만 바뀐다.
     * rrn 은 선택(나중에 PUT /rrn 으로 등록 가능). 응답에는 마스킹 값만 나간다.
     */
    public record EmployeeCreateRequest(
            @NotNull(message = "사업장을 선택해 주세요")
            @Pattern(regexp = "^S[0-9][0-9]$", message = "사업장코드는 S01 형식이에요")
            String siteCode,
            @NotBlank(message = "성명을 입력해 주세요") @Size(max = 100, message = "성명은 100자 이내로 써 주세요")
            String name,
            @NotNull(message = "성별을 선택해 주세요") @Pattern(regexp = "^[MF]$", message = "성별은 M 또는 F예요")
            String gender,
            @NotNull(message = "생년월일을 입력해 주세요")
            LocalDate birthDate,
            @NotBlank(message = "주소를 입력해 주세요") @Size(max = 300, message = "주소는 300자 이내로 써 주세요")
            String address,
            @Size(max = 4000, message = "이력은 4000자 이내로 써 주세요")
            String careerHistory,
            @NotBlank(message = "종사 업무를 입력해 주세요") @Size(max = 500, message = "종사 업무는 500자 이내로 써 주세요")
            String jobDescription,
            String jobFamilyCode,
            @NotNull(message = "입사일을 입력해 주세요")
            LocalDate hireDate,
            LocalDate renewalDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            @NotBlank(message = "고용형태를 선택해 주세요")
            String employmentTypeCode,
            @Positive(message = "1주 소정근로시간은 0보다 커야 해요")
            @Max(value = MINUTES_PER_WEEK, message = "1주 소정근로시간(분)이 너무 커요")
            Integer weeklyContractMinutes,
            String workLocationCode,
            @Size(max = 1000, message = "고용 관련 메모는 1000자 이내로 써 주세요")
            String employmentNote,
            @Size(max = 1000, message = "기타 사항은 1000자 이내로 써 주세요")
            String otherNote,
            @Positive(message = "부서가 올바르지 않아요")
            Long deptId,
            String jobGradeCode,
            String jobTitleCode,
            Rrn rrn) {
    }

    /**
     * 기본정보 수정. 명부 기재사항 전체를 보낸다(바꾸지 않는 값도 그대로).
     * 소속·직급·직책·사번·사업장·입사일·퇴직 정보는 받지 않는다. 모르는 필드(예: deptId)를 보내면 400으로 거부한다.
     */
    public record EmployeeUpdateRequest(
            @NotBlank(message = "성명을 입력해 주세요") @Size(max = 100, message = "성명은 100자 이내로 써 주세요")
            String name,
            @NotNull(message = "성별을 선택해 주세요") @Pattern(regexp = "^[MF]$", message = "성별은 M 또는 F예요")
            String gender,
            @NotNull(message = "생년월일을 입력해 주세요")
            LocalDate birthDate,
            @NotBlank(message = "주소를 입력해 주세요") @Size(max = 300, message = "주소는 300자 이내로 써 주세요")
            String address,
            @Size(max = 4000, message = "이력은 4000자 이내로 써 주세요")
            String careerHistory,
            @NotBlank(message = "종사 업무를 입력해 주세요") @Size(max = 500, message = "종사 업무는 500자 이내로 써 주세요")
            String jobDescription,
            String jobFamilyCode,
            LocalDate renewalDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            @NotBlank(message = "고용형태를 선택해 주세요")
            String employmentTypeCode,
            @Positive(message = "1주 소정근로시간은 0보다 커야 해요")
            @Max(value = MINUTES_PER_WEEK, message = "1주 소정근로시간(분)이 너무 커요")
            Integer weeklyContractMinutes,
            String workLocationCode,
            @Size(max = 1000, message = "고용 관련 메모는 1000자 이내로 써 주세요")
            String employmentNote,
            @Size(max = 1000, message = "기타 사항은 1000자 이내로 써 주세요")
            String otherNote) {

        /**
         * 모르는 필드는 거부한다. 전역 설정(FAIL_ON_UNKNOWN_PROPERTIES=false)은 모르는 필드를 조용히 버리므로,
         * 소속·직급·직책을 보낸 요청이 성공한 것처럼 보이지 않게 여기서 막는다.
         */
        @JsonAnySetter
        void rejectUnknown(String field, Object value) {
            throw new IllegalArgumentException("수정할 수 없는 항목이에요: " + field);
        }
    }

    /** 해고·퇴직·사망 처리(시행령 제20조 기재사항). 사유는 공통코드 RETIREMENT_REASON. */
    public record RetirementRequest(
            @NotNull(message = "퇴직일을 입력해 주세요")
            LocalDate retireDate,
            @NotBlank(message = "퇴직 사유를 선택해 주세요")
            String reasonCode,
            @Size(max = 1000, message = "퇴직 사유 상세는 1000자 이내로 써 주세요")
            String reasonDetail) {
    }

    /** 주민등록번호 등록·정정. */
    public record RrnRequest(@NotNull(message = "주민등록번호를 입력해 주세요") Rrn rrn) {
    }

    /** 원문 조회 응답. toString 은 원문을 찍지 않는다. */
    public record RrnReveal(long empId, String rrn) {
        @Override
        public String toString() {
            return "RrnReveal[empId=" + empId + "]";
        }
    }

    /** 상세. 주민등록번호는 마스킹 값만(rrnMasked, 미등록이면 null). */
    public record EmployeeView(long empId, String empNo, String siteCode, String name, String gender,
            LocalDate birthDate, String address, String careerHistory, String jobDescription, String jobFamilyCode,
            LocalDate hireDate, LocalDate renewalDate, LocalDate contractStartDate, LocalDate contractEndDate,
            String employmentTypeCode, Integer weeklyContractMinutes, String workLocationCode, String employmentNote,
            Long currentDeptId, String jobGradeCode, String jobTitleCode,
            LocalDate retireDate, String retireReasonCode, String retireReasonDetail, LocalDate recordRetentionUntil,
            String otherNote, String rrnMasked) {

        static EmployeeView of(Employee e, String rrnMasked) {
            return new EmployeeView(e.getEmpId(), e.getEmpNo(), e.getSiteCode(), e.getName(), e.getGender(),
                    e.getBirthDate(), e.getAddress(), e.getCareerHistory(), e.getJobDescription(), e.getJobFamilyCode(),
                    e.getHireDate(), e.getRenewalDate(), e.getContractStartDate(), e.getContractEndDate(),
                    e.getEmploymentTypeCode(), e.getWeeklyContractMinutes(), e.getWorkLocationCode(),
                    e.getEmploymentNote(), e.getCurrentDeptId(), e.getJobGradeCode(), e.getJobTitleCode(),
                    e.getRetireDate(), e.getRetireReasonCode(), e.getRetireReasonDetail(),
                    e.getRecordRetentionUntil(), e.getOtherNote(), rrnMasked);
        }

        /**
         * 인적사항 원문 권한이 없는 사람(부서장 등)에게 보내는 형태(#19, 서버 쪽 마스킹).
         * 생년월일·이력·메모·퇴직 사유 상세·주민등록번호 마스킹 값은 빼고, 주소는 첫 단위(시·도)만 남긴다
         * (화면 frontend/src/lib/privacy.ts 의 규칙과 같음).
         */
        public EmployeeView withoutPersonal() {
            return new EmployeeView(empId, empNo, siteCode, name, gender, null, maskAddress(address), null,
                    jobDescription, jobFamilyCode, hireDate, renewalDate, contractStartDate, contractEndDate,
                    employmentTypeCode, weeklyContractMinutes, workLocationCode, null, currentDeptId, jobGradeCode,
                    jobTitleCode, retireDate, retireReasonCode, null, recordRetentionUntil, null, null);
        }

        static String maskAddress(String a) {
            if (a == null || a.isBlank()) {
                return null;
            }
            return a.strip().split("\\s+")[0] + " ***";
        }
    }

    /** 목록 한 줄. 주소·이력·주민등록번호는 넣지 않는다. */
    public record EmployeeSummary(long empId, String empNo, String name, Long currentDeptId, String jobGradeCode,
            String jobTitleCode, String employmentTypeCode, LocalDate hireDate, LocalDate retireDate) {

        static EmployeeSummary of(Employee e) {
            return new EmployeeSummary(e.getEmpId(), e.getEmpNo(), e.getName(), e.getCurrentDeptId(),
                    e.getJobGradeCode(), e.getJobTitleCode(), e.getEmploymentTypeCode(), e.getHireDate(),
                    e.getRetireDate());
        }
    }
}
