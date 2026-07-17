package com.saas.admin.code;

import com.saas.admin.code.dto.CodeDtos.*;
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

@Tag(name = "6. 공통코드", description = "화면 선택지(직급·직책·부서 …)를 코드로 관리한다. 그룹코드·코드값은 생성 후 불변이다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService codeService;

    @Operation(summary = "코드 그룹 목록 (코드 포함)",
            description = "그룹코드 순으로 반환한다. 각 그룹의 codes 는 순서대로 정렬돼 있다.")
    @GetMapping("/code-groups")
    public ResponseEntity<List<GroupResponse>> groups() {
        return ResponseEntity.ok(codeService.groups());
    }

    @Operation(summary = "코드 그룹 추가")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가됨"),
            @ApiResponse(responseCode = "409", description = "그룹코드 중복(CODE_GROUP_DUPLICATED)", content = @Content)
    })
    @PostMapping("/code-groups")
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(codeService.createGroup(request));
    }

    @Operation(summary = "코드 그룹 수정", description = "그룹코드는 바꿀 수 없다 — 이름과 설명만 바뀐다.")
    @ApiResponse(responseCode = "404", description = "CODE_GROUP_NOT_FOUND", content = @Content)
    @PatchMapping("/code-groups/{groupCode}")
    public ResponseEntity<GroupResponse> updateGroup(@PathVariable String groupCode,
                                                     @Valid @RequestBody UpdateGroupRequest request) {
        return ResponseEntity.ok(codeService.updateGroup(groupCode, request));
    }

    @Operation(summary = "코드 그룹 삭제", description = "코드가 남아 있으면 삭제할 수 없다 — 코드를 먼저 삭제해야 한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제됨"),
            @ApiResponse(responseCode = "404", description = "CODE_GROUP_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "코드 남아 있음(CODE_GROUP_HAS_CODES)", content = @Content)
    })
    @DeleteMapping("/code-groups/{groupCode}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String groupCode) {
        codeService.deleteGroup(groupCode);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "코드 추가")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가됨"),
            @ApiResponse(responseCode = "404", description = "CODE_GROUP_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "코드값 중복(CODE_DUPLICATED)", content = @Content)
    })
    @PostMapping("/code-groups/{groupCode}/codes")
    public ResponseEntity<CodeResponse> createCode(@PathVariable String groupCode,
                                                   @Valid @RequestBody CreateCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(codeService.createCode(groupCode, request));
    }

    @Operation(summary = "코드 수정", description = "코드값은 바꿀 수 없다 — 이름/순서/사용 여부만 바뀐다.")
    @ApiResponse(responseCode = "404", description = "CODE_NOT_FOUND", content = @Content)
    @PatchMapping("/codes/{id}")
    public ResponseEntity<CodeResponse> updateCode(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateCodeRequest request) {
        return ResponseEntity.ok(codeService.updateCode(id, request));
    }

    @Operation(summary = "코드 순서 변경 (드래그앤드랍)",
            description = "같은 그룹 안에서 position 위치로 옮긴다. 이동 후 서버가 그 그룹 코드들의 순서를 1부터 다시 매긴다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "이동됨"),
            @ApiResponse(responseCode = "404", description = "CODE_NOT_FOUND", content = @Content)
    })
    @PostMapping("/codes/{id}/move")
    public ResponseEntity<Void> moveCode(@PathVariable Long id, @Valid @RequestBody MoveCodeRequest request) {
        codeService.moveCode(id, request.position());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "코드 삭제",
            description = "과거 데이터가 이 코드를 들고 있을 수 있다 — 정말 지우기보다 use_yn='N'(중지)을 권장한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제됨"),
            @ApiResponse(responseCode = "404", description = "CODE_NOT_FOUND", content = @Content)
    })
    @DeleteMapping("/codes/{id}")
    public ResponseEntity<Void> deleteCode(@PathVariable Long id) {
        codeService.deleteCode(id);
        return ResponseEntity.noContent().build();
    }
}
