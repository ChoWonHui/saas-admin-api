package com.saas.admin.tenant.home;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.home.TenantHomeDtos.HomeSaveRequest;
import com.saas.admin.tenant.home.TenantHomeDtos.HomeView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 사장님 콘솔 — 가게 메인 페이지 편집. */
@Tag(name = "15. 가게 홈(업체)", description = "사장님이 손님에게 보일 가게 메인 페이지를 편집한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/home")
@RequiredArgsConstructor
public class TenantHomeController {

    private final TenantHomeService homeService;

    @Operation(summary = "가게 홈 조회")
    @GetMapping
    public ResponseEntity<HomeView> get(@AuthenticationPrincipal AuthPrincipal p) {
        return ResponseEntity.ok(homeService.get(tenantId(p)));
    }

    @Operation(summary = "가게 홈 저장")
    @PutMapping
    public ResponseEntity<HomeView> save(@AuthenticationPrincipal AuthPrincipal p,
                                         @Valid @RequestBody HomeSaveRequest req) {
        return ResponseEntity.ok(homeService.save(tenantId(p), req));
    }

    private Long tenantId(AuthPrincipal p) {
        if (p == null || p.isAdmin() || !p.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
        return p.tenantId();
    }
}
