package com.saas.admin.org.dto;

import com.saas.admin.org.domain.Organization;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 조직/조직도 관련 요청·응답 DTO 모음. */
public final class OrgDtos {

    private OrgDtos() {
    }

    @Schema(description = "조직 추가. parentId 를 주면 그 조직의 하위로 들어간다.")
    public record CreateOrgRequest(
            @Schema(description = "부서코드. 전사 유니크.", example = "DEV")
            @NotBlank(message = "부서코드는 필수입니다.")
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,19}$", message = "부서코드는 대문자로 시작하는 1~20자 대문자/숫자/_ 여야 합니다.")
            String orgCode,

            @Schema(example = "플랫폼개발팀")
            @NotBlank(message = "조직명은 필수입니다.")
            @Size(max = 50, message = "조직명은 50자를 넘을 수 없습니다.")
            String name,

            @Schema(description = "상위 조직 ID. 없으면 최상위.")
            Long parentId,

            @Schema(description = "표시 순서. 없으면 맨 뒤에 붙는다.")
            Integer sortOrder
    ) {
    }

    @Schema(description = "조직 수정 — 부서코드와 이름을 바꾼다. 이동은 /move, 부서장은 /leader 로 한다.")
    public record UpdateOrgRequest(
            @NotBlank(message = "부서코드는 필수입니다.")
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,19}$", message = "부서코드는 대문자로 시작하는 1~20자 대문자/숫자/_ 여야 합니다.")
            String orgCode,

            @NotBlank(message = "조직명은 필수입니다.")
            @Size(max = 50, message = "조직명은 50자를 넘을 수 없습니다.")
            String name
    ) {
    }

    @Schema(description = "조직 이동 (드래그앤드랍). parentId 를 비우면 최상위로.")
    public record MoveOrgRequest(
            Long parentId,

            @NotNull(message = "위치는 필수입니다.")
            @Min(value = 0, message = "위치는 0 이상이어야 합니다.")
            Integer position
    ) {
    }

    @Schema(description = "부서장 지정. empNo 를 비우면 부서장을 해제한다. 그 조직 소속이어야 지정된다.")
    public record AssignLeaderRequest(String empNo) {
    }

    @Schema(description = "관리자를 조직에 배치 (드래그앤드랍). orgId 를 비우면 미배치로 되돌린다.")
    public record AssignMemberRequest(Long orgId) {
    }

    /** 조직도의 한 사람. */
    public record MemberNode(String empNo, String name, String jobGrade, String jobTitle, boolean leader) {
    }

    /** 조직도의 한 조직 — 부서장/팀원과 하위 조직을 함께 담는다. */
    public record OrgNode(
            Long id,
            Long parentId,
            String orgCode,
            String name,
            int sortOrder,
            List<MemberNode> members,
            List<OrgNode> children
    ) {
    }

    /** 관리 화면의 조직 단건 응답 (트리 없이). */
    public record OrgResponse(Long id, Long parentId, String orgCode, String name, String leaderEmpNo, int sortOrder) {
        public static OrgResponse from(Organization org) {
            return new OrgResponse(org.getId(), org.parentId(), org.getOrgCode(), org.getName(),
                    org.getLeaderEmpNo(), org.getSortOrder());
        }
    }
}
