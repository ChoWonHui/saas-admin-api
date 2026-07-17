package com.saas.admin.code.dto;

import com.saas.admin.code.domain.CodeGroup;
import com.saas.admin.code.domain.CommonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 공통코드 관련 요청/응답 DTO 모음. */
public final class CodeDtos {

    private CodeDtos() {
    }

    @Schema(description = "코드 그룹 추가. groupCode 는 생성 후 바꿀 수 없다.")
    public record CreateGroupRequest(
            @Schema(description = "그룹 식별자. 대문자로 시작, 대문자/숫자/_ 만.", example = "JOB_GRADE")
            @NotBlank(message = "그룹코드는 필수입니다.")
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$", message = "그룹코드는 대문자로 시작하는 2~30자 대문자/숫자/_ 여야 합니다.")
            String groupCode,

            @Schema(example = "직급")
            @NotBlank(message = "그룹명은 필수입니다.")
            @Size(max = 50, message = "그룹명은 50자를 넘을 수 없습니다.")
            String name,

            @Size(max = 200, message = "설명은 200자를 넘을 수 없습니다.")
            String description
    ) {
    }

    @Schema(description = "코드 그룹 수정. 그룹코드는 바꿀 수 없다 — 이름과 설명만 바뀐다.")
    public record UpdateGroupRequest(
            @NotBlank(message = "그룹명은 필수입니다.")
            @Size(max = 50, message = "그룹명은 50자를 넘을 수 없습니다.")
            String name,

            @Size(max = 200, message = "설명은 200자를 넘을 수 없습니다.")
            String description
    ) {
    }

    @Schema(description = "코드 추가. 코드값은 생성 후 바꿀 수 없다.")
    public record CreateCodeRequest(
            @Schema(description = "코드값. 대문자/숫자/_ 만.", example = "SENIOR")
            @NotBlank(message = "코드값은 필수입니다.")
            @Pattern(regexp = "^[A-Z0-9_]{1,30}$", message = "코드값은 1~30자 대문자/숫자/_ 여야 합니다.")
            String code,

            @Schema(description = "화면에 보여줄 이름", example = "대리")
            @NotBlank(message = "코드명은 필수입니다.")
            @Size(max = 50, message = "코드명은 50자를 넘을 수 없습니다.")
            String name,

            @Schema(description = "표시 순서. 없으면 맨 뒤에 붙는다.")
            Integer sortOrder
    ) {
    }

    @Schema(description = "코드 수정. 코드값은 바꿀 수 없다 — 이름/순서/사용 여부만 바뀐다.")
    public record UpdateCodeRequest(
            @NotBlank(message = "코드명은 필수입니다.")
            @Size(max = 50, message = "코드명은 50자를 넘을 수 없습니다.")
            String name,

            Integer sortOrder,

            @Schema(description = "'Y' = 사용 / 'N' = 중지 (선택지에서만 빠지고 과거 데이터 표시는 유지)")
            @Pattern(regexp = "^[YN]$", message = "사용 여부는 Y 또는 N 이어야 합니다.")
            String useYn
    ) {
    }

    @Schema(description = "코드 순서 변경 (드래그앤드랍). 같은 그룹 안 형제 목록(자신 제외)에서의 0 기반 위치.")
    public record MoveCodeRequest(
            @Min(value = 0, message = "위치는 0 이상이어야 합니다.")
            int position
    ) {
    }

    public record CodeResponse(Long id, String code, String name, int sortOrder, String useYn) {
        public static CodeResponse from(CommonCode code) {
            return new CodeResponse(code.getId(), code.getCode(), code.getName(), code.getSortOrder(), code.getUseYn());
        }
    }

    public record GroupResponse(String groupCode, String name, String description, List<CodeResponse> codes) {
        public static GroupResponse of(CodeGroup group, List<CodeResponse> codes) {
            return new GroupResponse(group.getGroupCode(), group.getName(), group.getDescription(), codes);
        }
    }
}
