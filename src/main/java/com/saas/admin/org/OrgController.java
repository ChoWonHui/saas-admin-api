package com.saas.admin.org;

import com.saas.admin.org.dto.OrgDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "3. 조직 / 조직도", description = "조직(부서) 트리와 부서장·사원 배치. 관리자 화면 우측 조직도가 이걸 쓴다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgService;

    @Operation(summary = "조직도 조회",
            description = "최상위 조직 목록을 반환한다. 각 조직에 소속 사원(members, 부서장 먼저)과 하위 조직(children)이 담긴다.")
    @GetMapping("/org-chart")
    public ResponseEntity<List<OrgNode>> orgChart() {
        return ResponseEntity.ok(orgService.orgChart());
    }

    @Operation(summary = "조직 추가")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가됨"),
            @ApiResponse(responseCode = "404", description = "상위 조직 없음(ORG_PARENT_NOT_FOUND)", content = @Content)
    })
    @PostMapping("/orgs")
    public ResponseEntity<OrgResponse> create(@Valid @RequestBody CreateOrgRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orgService.create(request));
    }

    @Operation(summary = "조직 수정", description = "부서코드와 이름을 바꾼다. 부서코드는 전사 유니크다.")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "ORG_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "부서코드 중복(ORG_CODE_DUPLICATED)", content = @Content)
    })
    @PatchMapping("/orgs/{id}")
    public ResponseEntity<OrgResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateOrgRequest request) {
        return ResponseEntity.ok(orgService.update(id, request));
    }

    @Operation(summary = "조직 이동 (드래그앤드랍)", description = "상위·순서만 바꾼다. 자기 자신/후손을 상위로 지정하면 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "이동됨"),
            @ApiResponse(responseCode = "409", description = "순환(ORG_CYCLE)", content = @Content)
    })
    @PostMapping("/orgs/{id}/move")
    public ResponseEntity<Void> move(@PathVariable Long id, @Valid @RequestBody MoveOrgRequest request) {
        orgService.move(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "부서장 지정/해제", description = "empNo 를 비우면 해제. 지정할 땐 그 조직 소속 사원이어야 한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "처리됨"),
            @ApiResponse(responseCode = "409", description = "소속 아님(LEADER_NOT_IN_ORG)", content = @Content)
    })
    @PostMapping("/orgs/{id}/leader")
    public ResponseEntity<Void> assignLeader(@PathVariable Long id, @RequestBody AssignLeaderRequest request) {
        orgService.assignLeader(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "조직 삭제", description = "하위 조직이나 소속 사원이 있으면 삭제할 수 없다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제됨"),
            @ApiResponse(responseCode = "409", description = "하위 조직(ORG_HAS_CHILDREN) / 소속 사원(ORG_HAS_MEMBERS)", content = @Content)
    })
    @DeleteMapping("/orgs/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "사원 조직 배치 (드래그앤드랍)", description = "orgId 를 비우면 미배치로 되돌린다.")
    @ApiResponse(responseCode = "204", description = "배치됨")
    @PostMapping("/admins/{empNo}/org")
    public ResponseEntity<Void> assignMember(@PathVariable String empNo, @RequestBody AssignMemberRequest request) {
        orgService.assignMember(empNo, request);
        return ResponseEntity.noContent().build();
    }
}
