package com.saas.admin.tenant.consolemenu;

import com.saas.admin.tenant.consolemenu.TenantMenuDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 플랫폼 관리자 — 사장님 콘솔 메뉴/권한 <b>전역 정책</b> 관리. 모든 업체가 이 설정을 공유한다.
 * 메뉴를 추가·수정·삭제하고 역할(대표/홀/주방)별 노출을 정한다. 보안은 /api/platform-admin/** (PLATFORM_ADMIN).
 */
@Tag(name = "25. 업체 콘솔 메뉴 정책(관리자)", description = "모든 업체가 공유하는 사장님 콘솔 메뉴와 역할(대표/홀/주방) 접근 권한을 설정한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/tenant-menus")
@RequiredArgsConstructor
public class PlatformConsoleMenuController {

    private final TenantConsoleMenuService menuService;

    @Operation(summary = "콘솔 메뉴 목록", description = "권한 플래그(홀/주방) 포함. 처음이면 기본 메뉴를 자동 생성한다.")
    @GetMapping
    public ResponseEntity<List<TenantMenuView>> list() {
        return ResponseEntity.ok(menuService.list());
    }

    @Operation(summary = "콘솔 메뉴 추가")
    @PostMapping
    public ResponseEntity<TenantMenuView> add(@Valid @RequestBody TenantMenuRequest req) {
        return ResponseEntity.ok(menuService.add(req));
    }

    @Operation(summary = "콘솔 메뉴 수정 (이름·URL·아이콘·권한)")
    @PatchMapping("/{menuId}")
    public ResponseEntity<TenantMenuView> update(@PathVariable Long menuId, @Valid @RequestBody TenantMenuRequest req) {
        return ResponseEntity.ok(menuService.update(menuId, req));
    }

    @Operation(summary = "콘솔 메뉴 삭제")
    @DeleteMapping("/{menuId}")
    public ResponseEntity<Void> delete(@PathVariable Long menuId) {
        menuService.delete(menuId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "콘솔 메뉴 순서 변경")
    @PostMapping("/reorder")
    public ResponseEntity<Void> reorder(@RequestBody TenantMenuReorderRequest req) {
        menuService.reorder(req.orderedIds());
        return ResponseEntity.noContent().build();
    }
}
