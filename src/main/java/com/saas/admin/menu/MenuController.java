package com.saas.admin.menu;

import com.saas.admin.menu.dto.MenuDtos.CreateMenuRequest;
import com.saas.admin.menu.dto.MenuDtos.MenuResponse;
import com.saas.admin.menu.dto.MenuDtos.MoveMenuRequest;
import com.saas.admin.menu.dto.MenuDtos.UpdateMenuRequest;
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

@Tag(name = "5. 콘솔 메뉴", description = "콘솔 상단(header) 메뉴 관리. 여기서 바꾸면 콘솔 내비게이션에 바로 반영된다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @Operation(summary = "메뉴 트리 조회",
            description = "최상위 메뉴 목록을 반환한다. 하위 메뉴는 각 항목의 children 에 담겨 있다 (2단까지).")
    @GetMapping
    public ResponseEntity<List<MenuResponse>> tree() {
        return ResponseEntity.ok(menuService.tree());
    }

    @Operation(summary = "메뉴 추가",
            description = "parentId 를 주면 그 메뉴의 하위(드롭다운)로 들어간다. 하위의 하위는 만들 수 없다(2단 제한).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가됨"),
            @ApiResponse(responseCode = "404", description = "상위 메뉴 없음(MENU_PARENT_NOT_FOUND)", content = @Content),
            @ApiResponse(responseCode = "409", description = "2단 초과(MENU_DEPTH_EXCEEDED) / 이름 중복(MENU_NAME_DUPLICATED)", content = @Content)
    })
    @PostMapping
    public ResponseEntity<MenuResponse> create(@Valid @RequestBody CreateMenuRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.create(request));
    }

    @Operation(summary = "메뉴 수정",
            description = "name / url / parentId / sortOrder 를 보낸 값으로 교체한다. parentId 를 비우면 최상위로 이동한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = "MENU_NOT_FOUND / MENU_PARENT_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "2단 초과 / 자기 자신을 상위로 지정 / 이름 중복", content = @Content)
    })
    @PatchMapping("/{id}")
    public ResponseEntity<MenuResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateMenuRequest request) {
        return ResponseEntity.ok(menuService.update(id, request));
    }

    @Operation(summary = "메뉴 이동 (드래그앤드랍)",
            description = """
                    이름·URL 은 건드리지 않고 상위와 순서만 바꾼다. position 은 새 형제 목록(자신 제외)의
                    0 기반 위치다. 이동 후 서버가 관련 형제들의 순서를 1부터 다시 매긴다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "이동됨"),
            @ApiResponse(responseCode = "404", description = "MENU_NOT_FOUND / MENU_PARENT_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "2단 초과 / 자기 자신을 상위로 지정 / 이름 중복", content = @Content)
    })
    @PostMapping("/{id}/move")
    public ResponseEntity<Void> move(@PathVariable Long id, @Valid @RequestBody MoveMenuRequest request) {
        menuService.move(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "메뉴 삭제",
            description = "하위 메뉴가 있으면 삭제할 수 없다 — 하위를 먼저 삭제해야 한다(실수로 묶음째 지우는 것을 막는다).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제됨"),
            @ApiResponse(responseCode = "404", description = "MENU_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "하위 메뉴 있음(MENU_HAS_CHILDREN)", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
