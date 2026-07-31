package com.saas.admin.tenant.consolemenu;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.consolemenu.TenantMenuDtos.TenantNavItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사장님 콘솔 — 로그인한 사용자의 역할(대표/홀/주방)이 볼 수 있는 상단 메뉴를 돌려준다.
 * 콘솔 네비게이션이 이 결과로 그려진다.
 */
@Tag(name = "26. 콘솔 메뉴(업체)", description = "로그인 역할이 볼 수 있는 사장님 콘솔 메뉴.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/menus")
@RequiredArgsConstructor
public class TenantConsoleMenuController {

    private final TenantConsoleMenuService menuService;

    @Operation(summary = "내 역할이 볼 수 있는 콘솔 메뉴")
    @GetMapping
    public ResponseEntity<List<TenantNavItem>> myMenus(@AuthenticationPrincipal AuthPrincipal p) {
        if (p == null || p.isAdmin() || !p.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
        return ResponseEntity.ok(menuService.navFor(p.roleCode()));
    }
}
