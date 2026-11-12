package com.glassehr.code;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 공통코드 API 요청·응답 형식. 프론트 타입: frontend/src/api/codes.ts */
public final class CodeDtos {

    private CodeDtos() {
    }

    public record CodeGroupView(String groupCode, String name, String description, int sortOrder, boolean active) {
        static CodeGroupView of(CodeGroup g) {
            return new CodeGroupView(g.getGroupCode(), g.getGroupName(), g.getDescription(), g.getSortOrder(), g.isActive());
        }
    }

    public record CodeView(String groupCode, String code, String name, String description, int sortOrder, boolean active) {
        static CodeView of(CommonCode c) {
            return new CodeView(c.getGroupCode(), c.getCode(), c.getCodeName(), c.getDescription(), c.getSortOrder(), c.isActive());
        }
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
}
