package com.saas.admin.tenant.menu;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.TenantBranchService;
import com.saas.admin.tenant.menu.dto.MenuDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 업체(사장님) 콘솔의 메뉴판 관리. 업체코드(토큰의 테넌트)를 키로 자기 가게(기본 지점) 메뉴만 다룬다.
 * 관리자 콘솔의 메뉴 편집기와 동일한 서비스({@link TenantMenuService})를 그대로 위임한다.
 */
@Tag(name = "13. 메뉴판(업체)", description = "사장님이 자기 가게 메뉴판을 관리한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/menu")
@RequiredArgsConstructor
public class TenantMenuBoardController {

    private final TenantMenuService menuService;
    private final TenantBranchService branchService;

    @Operation(summary = "메뉴판 조회 (우리 가게)")
    @GetMapping
    public ResponseEntity<MenuResponse> get(@AuthenticationPrincipal AuthPrincipal p) {
        long tid = tenantId(p);
        return ResponseEntity.ok(menuService.getMenu(tid, branchService.defaultBranchId(tid)));
    }

    @Operation(summary = "분류 추가")
    @PostMapping("/categories")
    public ResponseEntity<MenuResponse> addCategory(@AuthenticationPrincipal AuthPrincipal p,
                                                    @Valid @RequestBody CategoryRequest req) {
        long tid = tenantId(p);
        return ResponseEntity.ok(menuService.addCategory(tid, branchService.defaultBranchId(tid), req));
    }

    @Operation(summary = "분류명 수정")
    @PatchMapping("/categories/{categoryId}")
    public ResponseEntity<MenuResponse> renameCategory(@AuthenticationPrincipal AuthPrincipal p,
                                                       @PathVariable Long categoryId, @Valid @RequestBody CategoryRequest req) {
        long tid = tenantId(p);
        return ResponseEntity.ok(menuService.renameCategory(tid, branchService.defaultBranchId(tid), categoryId, req));
    }

    @Operation(summary = "분류 삭제")
    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<MenuResponse> deleteCategory(@AuthenticationPrincipal AuthPrincipal p,
                                                       @PathVariable Long categoryId) {
        long tid = tenantId(p);
        return ResponseEntity.ok(menuService.deleteCategory(tid, branchService.defaultBranchId(tid), categoryId));
    }

    @Operation(summary = "메뉴 추가")
    @PostMapping("/categories/{categoryId}/items")
    public ResponseEntity<MenuResponse> addItem(@AuthenticationPrincipal AuthPrincipal p,
                                                @PathVariable Long categoryId, @Valid @RequestBody ItemRequest req) {
        long tid = tenantId(p);
        return ResponseEntity.ok(menuService.addItem(tid, branchService.defaultBranchId(tid), categoryId, req));
    }

    @Operation(summary = "메뉴 수정")
    @PatchMapping("/items/{itemId}")
    public ResponseEntity<MenuResponse> updateItem(@AuthenticationPrincipal AuthPrincipal p,
                                                   @PathVariable Long itemId, @Valid @RequestBody ItemRequest req) {
        long tid = tenantId(p);
        return ResponseEntity.ok(menuService.updateItem(tid, branchService.defaultBranchId(tid), itemId, req));
    }

    @Operation(summary = "메뉴 삭제")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<MenuResponse> deleteItem(@AuthenticationPrincipal AuthPrincipal p,
                                                   @PathVariable Long itemId) {
        long tid = tenantId(p);
        return ResponseEntity.ok(menuService.deleteItem(tid, branchService.defaultBranchId(tid), itemId));
    }

    /** 토큰에 업체(테넌트) 컨텍스트가 있어야 한다. */
    private long tenantId(AuthPrincipal p) {
        if (p == null || p.isAdmin() || !p.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
        return p.tenantId();
    }
}
