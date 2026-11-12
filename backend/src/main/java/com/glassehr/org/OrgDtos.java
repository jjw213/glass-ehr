package com.glassehr.org;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 조직 API 요청·응답 형식. */
public final class OrgDtos {

    /** 부서코드: 영문 대문자·숫자로 시작, 영문 대문자·숫자·밑줄, 20자 이내(V008 CHECK·컬럼 길이와 같음). */
    public static final String DEPT_CODE_PATTERN = "^[A-Z0-9][A-Z0-9_]*$";

    private OrgDtos() {
    }

    /** 기준일 조직 트리. roots 는 상위부서가 없는 부서들. */
    public record OrgTree(LocalDate baseDate, List<DeptNode> roots) {
    }

    /** 트리 노드. validFrom/validTo 는 이 속성(이름·상위부서 등)이 유효한 기간. */
    public record DeptNode(long deptId, String deptCode, String name, Long parentDeptId, Long headEmpId,
            int sortOrder, LocalDate validFrom, LocalDate validTo, List<DeptNode> children) {
    }

    /** 쓰기 결과. 속성 기간(validFrom/validTo)과 부서 존속 기간(deptValidFrom/deptValidTo)을 함께 돌려준다. */
    public record DeptView(long deptId, String deptCode, String name, Long parentDeptId, Long headEmpId,
            int sortOrder, LocalDate validFrom, LocalDate validTo, DeptChangeType changeType,
            LocalDate deptValidFrom, LocalDate deptValidTo) {
        static DeptView of(OrgDept d, OrgDeptHist h) {
            return new DeptView(d.getDeptId(), d.getDeptCode(), h.getDeptName(), h.getParentDeptId(), h.getHeadEmpId(),
                    h.getSortOrder(), h.getValidFrom(), h.getValidTo(), h.getChangeType(),
                    d.getValidFrom(), d.getValidTo());
        }
    }

    /** 부서 신설. effectiveDate = 신설일(이날부터 조직도에 보임). parentDeptId 가 없으면 최상위. */
    public record DeptCreateRequest(
            @NotNull(message = "부서코드를 입력해 주세요")
            @Size(max = 20, message = "부서코드는 20자 이내로 써 주세요")
            @Pattern(regexp = DEPT_CODE_PATTERN, message = "부서코드는 영문 대문자·숫자·밑줄만 써 주세요(첫 글자는 영문 대문자나 숫자)")
            String deptCode,
            @NotBlank(message = "부서명을 입력해 주세요")
            @Size(max = 100, message = "부서명은 100자 이내로 써 주세요")
            String name,
            @Positive(message = "상위부서가 올바르지 않아요")
            Long parentDeptId,
            @Positive(message = "부서장이 올바르지 않아요")
            Long headEmpId,
            @NotNull(message = "정렬 순서를 입력해 주세요")
            @Min(value = 0, message = "정렬 순서는 0 이상이어야 해요")
            @Max(value = 99999, message = "정렬 순서는 99999 이하여야 해요")
            Integer sortOrder,
            @NotNull(message = "신설일을 입력해 주세요")
            LocalDate effectiveDate,
            @Size(max = 500, message = "사유는 500자 이내로 써 주세요")
            String reason) {
    }

    /**
     * 부서 변경. effectiveDate 부터 적용할 속성 전체를 보낸다(바꾸지 않는 값도 그대로 보냄).
     * 부서코드는 바꿀 수 없다.
     */
    public record DeptChangeRequest(
            @NotBlank(message = "부서명을 입력해 주세요")
            @Size(max = 100, message = "부서명은 100자 이내로 써 주세요")
            String name,
            @Positive(message = "상위부서가 올바르지 않아요")
            Long parentDeptId,
            @Positive(message = "부서장이 올바르지 않아요")
            Long headEmpId,
            @NotNull(message = "정렬 순서를 입력해 주세요")
            @Min(value = 0, message = "정렬 순서는 0 이상이어야 해요")
            @Max(value = 99999, message = "정렬 순서는 99999 이하여야 해요")
            Integer sortOrder,
            @NotNull(message = "적용일을 입력해 주세요")
            LocalDate effectiveDate,
            @Size(max = 500, message = "사유는 500자 이내로 써 주세요")
            String reason) {
    }

    /** 부서 폐지. effectiveDate = 폐지일(이날부터 조직도에 없음). */
    public record DeptCloseRequest(
            @NotNull(message = "폐지일을 입력해 주세요")
            LocalDate effectiveDate,
            @Size(max = 500, message = "사유는 500자 이내로 써 주세요")
            String reason) {
    }
}
