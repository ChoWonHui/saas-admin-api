package com.saas.admin.tenant.menu;

import com.saas.admin.tenant.menu.dto.MenuDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 지점 메뉴판 관리. 모든 응답은 갱신된 전체 메뉴 트리를 돌려준다(프런트가 통째로 다시 그림).
 * 경로가 /tenants/{tenantId}/branches/{branchId} 아래라 SecurityConfig 의 PLATFORM_ADMIN 만 통과.
 */
@Tag(name = "11. 메뉴판", description = "지점별 메뉴 분류/메뉴/옵션 관리 + 다른 지점 복사.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/tenants/{tenantId}/branches/{branchId}/menu")
@RequiredArgsConstructor
public class TenantMenuController {

    private final TenantMenuService menuService;

    @Operation(summary = "메뉴판 조회", description = "분류→메뉴→옵션그룹→옵션 트리.")
    @GetMapping
    public ResponseEntity<MenuResponse> get(@PathVariable Long tenantId, @PathVariable Long branchId) {
        return ResponseEntity.ok(menuService.getMenu(tenantId, branchId));
    }

    @Operation(summary = "분류 추가")
    @PostMapping("/categories")
    public ResponseEntity<MenuResponse> addCategory(@PathVariable Long tenantId, @PathVariable Long branchId,
                                                    @Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.ok(menuService.addCategory(tenantId, branchId, req));
    }

    @Operation(summary = "분류명 수정")
    @PatchMapping("/categories/{categoryId}")
    public ResponseEntity<MenuResponse> renameCategory(@PathVariable Long tenantId, @PathVariable Long branchId,
                                                       @PathVariable Long categoryId, @Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.ok(menuService.renameCategory(tenantId, branchId, categoryId, req));
    }

    @Operation(summary = "분류 삭제", description = "분류에 속한 메뉴·옵션도 함께 삭제된다.")
    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<MenuResponse> deleteCategory(@PathVariable Long tenantId, @PathVariable Long branchId,
                                                       @PathVariable Long categoryId) {
        return ResponseEntity.ok(menuService.deleteCategory(tenantId, branchId, categoryId));
    }

    @Operation(summary = "메뉴 추가", description = "옵션그룹도 함께 저장.")
    @PostMapping("/categories/{categoryId}/items")
    public ResponseEntity<MenuResponse> addItem(@PathVariable Long tenantId, @PathVariable Long branchId,
                                                @PathVariable Long categoryId, @Valid @RequestBody ItemRequest req) {
        return ResponseEntity.ok(menuService.addItem(tenantId, branchId, categoryId, req));
    }

    @Operation(summary = "메뉴 수정", description = "옵션그룹은 통째로 교체. categoryId 로 분류 이동 가능.")
    @PatchMapping("/items/{itemId}")
    public ResponseEntity<MenuResponse> updateItem(@PathVariable Long tenantId, @PathVariable Long branchId,
                                                   @PathVariable Long itemId, @Valid @RequestBody ItemRequest req) {
        return ResponseEntity.ok(menuService.updateItem(tenantId, branchId, itemId, req));
    }

    @Operation(summary = "메뉴 삭제")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<MenuResponse> deleteItem(@PathVariable Long tenantId, @PathVariable Long branchId,
                                                   @PathVariable Long itemId) {
        return ResponseEntity.ok(menuService.deleteItem(tenantId, branchId, itemId));
    }

    @Operation(summary = "다른 지점 메뉴 복사", description = "같은 업체의 다른 지점 메뉴판을 이 지점으로 복사(기존 메뉴 대체).")
    @PostMapping("/copy")
    public ResponseEntity<MenuResponse> copy(@PathVariable Long tenantId, @PathVariable Long branchId,
                                             @RequestBody CopyMenuRequest req) {
        return ResponseEntity.ok(menuService.copyFrom(tenantId, branchId, req));
    }
}
