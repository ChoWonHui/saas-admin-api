package com.saas.admin.tenant.home;

import com.saas.admin.tenant.home.TenantHomeDtos.HomeView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 플랫폼 관리자 — 업체별 가게 꾸미기(store decorate) 조회(읽기 전용).
 * 업체 관리 화면에서 특정 업체의 미니룸/가게 홈을 확인한다. 편집은 사장님 콘솔에서 한다.
 * publicView 가 아니라 get() 을 쓰므로 미표시(published=false) 상태여도 확인 가능.
 */
@Tag(name = "22. 업체 가게꾸미기 조회(관리자)", description = "플랫폼 관리자가 업체별 store decorate(미니룸)를 확인한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/tenants/{tenantId}/home")
@RequiredArgsConstructor
public class PlatformHomeController {

    private final TenantHomeService homeService;

    @Operation(summary = "업체 가게꾸미기 조회", description = "미표시 상태여도 콘텐츠 전체를 반환한다(읽기 전용).")
    @GetMapping
    public ResponseEntity<HomeView> get(@PathVariable Long tenantId) {
        return ResponseEntity.ok(homeService.get(tenantId));
    }
}
