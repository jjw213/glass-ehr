package com.glassehr.code;

import com.glassehr.code.CodeUsageRepository.UsageRow;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 공통코드 API 요청·응답 형식. 프론트 타입: frontend/src/api/codes.ts */
public final class CodeDtos {

    private CodeDtos() {
    }

    /**
     * @param system   시스템 그룹(로직이 참조, 삭제 불가)
     * @param editPage 다른 모듈 화면에서 편집하는 그룹의 화면 키. null 이면 공통코드 화면에서 편집
     */
    public record CodeGroupView(String groupCode, String name, String description, int sortOrder, boolean active,
            boolean system, String editPage) {
        static CodeGroupView of(CodeGroup g) {
            return new CodeGroupView(g.getGroupCode(), g.getGroupName(), g.getDescription(), g.getSortOrder(),
                    g.isActive(), g.isSystem(), g.getEditPage());
        }
    }

    /** @param system 기본 코드(로직이 코드값을 참조, 삭제 불가) */
    public record CodeView(String groupCode, String code, String name, String description, int sortOrder, boolean active,
            boolean system) {
        static CodeView of(CommonCode c) {
            return new CodeView(c.getGroupCode(), c.getCode(), c.getCodeName(), c.getDescription(), c.getSortOrder(),
                    c.isActive(), c.isSystem());
        }
    }

    /**
     * 관리 화면 코드 목록 한 줄.
     *
     * @param inUse      어떤 테이블에서든 참조 중인지
     * @param usageCount 참조 건수 합(테이블별 행 수의 합)
     */
    public record CodeListItem(String groupCode, String code, String name, String description, int sortOrder,
            boolean active, boolean system, boolean inUse, long usageCount) {
        static CodeListItem of(CommonCode c, long usageCount) {
            return new CodeListItem(c.getGroupCode(), c.getCode(), c.getCodeName(), c.getDescription(),
                    c.getSortOrder(), c.isActive(), c.isSystem(), usageCount > 0, usageCount);
        }
    }

    /** 코드그룹 추가(사용자 그룹). 그룹코드는 한 번 정하면 바꿀 수 없다. */
    public record GroupCreateRequest(
            @NotNull(message = "그룹코드를 입력해 주세요")
            @Pattern(regexp = CodeGroups.CODE_PATTERN,
                    message = "그룹코드는 영문 대문자로 시작하고 영문 대문자·숫자·밑줄만 30자 이내로 써 주세요")
            String groupCode,
            @NotBlank(message = "그룹명을 입력해 주세요")
            @Size(max = 100, message = "그룹명은 100자 이내로 써 주세요")
            String name,
            @Size(max = 500, message = "설명은 500자 이내로 써 주세요")
            String description,
            @NotNull(message = "정렬 순서를 입력해 주세요")
            @Min(value = 0, message = "정렬 순서는 0 이상이어야 해요")
            @Max(value = 99999, message = "정렬 순서는 99999 이하여야 해요")
            Integer sortOrder,
            @NotNull(message = "사용 여부를 정해 주세요")
            Boolean active) {
    }

    /** 코드그룹 수정. 그룹코드는 바꾸지 않는다. 시스템 그룹은 사용 여부를 바꿀 수 없다. */
    public record GroupUpdateRequest(
            @NotBlank(message = "그룹명을 입력해 주세요")
            @Size(max = 100, message = "그룹명은 100자 이내로 써 주세요")
            String name,
            @Size(max = 500, message = "설명은 500자 이내로 써 주세요")
            String description,
            @NotNull(message = "정렬 순서를 입력해 주세요")
            @Min(value = 0, message = "정렬 순서는 0 이상이어야 해요")
            @Max(value = 99999, message = "정렬 순서는 99999 이하여야 해요")
            Integer sortOrder,
            @NotNull(message = "사용 여부를 정해 주세요")
            Boolean active) {
    }

    /** 코드 추가. 코드값은 한 번 정하면 바꿀 수 없다. */
    public record CodeCreateRequest(
            @NotNull(message = "코드값을 입력해 주세요")
            @Pattern(regexp = CodeGroups.CODE_PATTERN,
                    message = "코드값은 영문 대문자로 시작하고 영문 대문자·숫자·밑줄만 30자 이내로 써 주세요")
            String code,
            @NotBlank(message = "코드명을 입력해 주세요")
            @Size(max = 100, message = "코드명은 100자 이내로 써 주세요")
            String name,
            @Size(max = 500, message = "설명은 500자 이내로 써 주세요")
            String description,
            @NotNull(message = "정렬 순서를 입력해 주세요")
            @Min(value = 0, message = "정렬 순서는 0 이상이어야 해요")
            @Max(value = 99999, message = "정렬 순서는 99999 이하여야 해요")
            Integer sortOrder,
            @NotNull(message = "사용 여부를 정해 주세요")
            Boolean active) {
    }

    /** 코드 수정. 코드값 외의 속성만 바꾼다. */
    public record CodeUpdateRequest(
            @NotBlank(message = "코드명을 입력해 주세요")
            @Size(max = 100, message = "코드명은 100자 이내로 써 주세요")
            String name,
            @Size(max = 500, message = "설명은 500자 이내로 써 주세요")
            String description,
            @NotNull(message = "정렬 순서를 입력해 주세요")
            @Min(value = 0, message = "정렬 순서는 0 이상이어야 해요")
            @Max(value = 99999, message = "정렬 순서는 99999 이하여야 해요")
            Integer sortOrder,
            @NotNull(message = "사용 여부를 정해 주세요")
            Boolean active) {
    }

    /** 사용처 테이블 하나의 건수. */
    public record UsageTable(String table, String label, long count) {
    }

    /**
     * 코드 하나의 사용처 요약.
     *
     * @param rowsVisible 행 목록(사번·이름)을 볼 수 있는지(EMP_READ_ALL). false 면 건수만 보인다
     */
    public record UsageSummary(String groupCode, String code, long total, List<UsageTable> tables, boolean rowsVisible) {
    }

    /** 사용처 행 목록 한 페이지(page 는 0부터). */
    public record UsagePage(String table, String label, String dateLabel, long total, int page, int size,
            List<UsageRow> rows) {
    }
}
