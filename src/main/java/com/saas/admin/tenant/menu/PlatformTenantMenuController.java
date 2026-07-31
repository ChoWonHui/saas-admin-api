package com.saas.admin.tenant.menu;

import com.saas.admin.tenant.TenantBranchService;
import com.saas.admin.tenant.menu.dto.MenuDtos.MenuResponse;
import com.saas.admin.tenant.menu.dto.MenuDtos.SoldOutRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 플랫폼 관리자 — 업체 주문관리 화면에서 그 업체 메뉴의 품절을 빠르게 처리한다.
 * 업체의 기본 지점 메뉴를 대상으로 하며(관리자는 지점을 따로 고르지 않는다), 손님 메뉴판에 즉시 반영된다.
 */
@Tag(name = "22. 업체 메뉴 품절(관리자)", description = "플랫폼 관리자가 업체 메뉴를 품절/판매중으로 전환한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/tenants/{tenantId}/menu")
@RequiredArgsConstructor
public class PlatformTenantMenuController {

    private final TenantMenuService menuService;
    private final TenantBranchService branchService;

    @Operation(summary = "업체 메뉴 조회(기본 지점)", description = "품절 처리를 위해 분류→메뉴 트리를 돌려준다.")
    @GetMapping
    public ResponseEntity<MenuResponse> get(@PathVariable Long tenantId) {
        return ResponseEntity.ok(menuService.getMenu(tenantId, branchService.defaultBranchId(tenantId)));
    }

    @Operation(summary = "메뉴 품절 토글", description = "품절/판매중 전환. 손님 메뉴판에 즉시 반영.")
    @PatchMapping("/items/{itemId}/soldout")
    public ResponseEntity<MenuResponse> setSoldOut(@PathVariable Long tenantId, @PathVariable Long itemId,
                                                   @RequestBody SoldOutRequest req) {
        return ResponseEntity.ok(menuService.setItemSoldOut(
                tenantId, branchService.defaultBranchId(tenantId), itemId, req.soldOut()));
    }
}
