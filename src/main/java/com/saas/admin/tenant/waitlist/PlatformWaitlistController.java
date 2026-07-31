package com.saas.admin.tenant.waitlist;

import com.saas.admin.tenant.waitlist.WaitlistDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 플랫폼 관리자 — 업체별 대기(예약) 확인·수정. 업체 관리 화면에서 특정 업체의 대기열을 다룬다.
 * TenantWaitlistService 를 tenantId 경로변수로 그대로 재사용한다. 보안은 /api/platform-admin/** (PLATFORM_ADMIN).
 */
@Tag(name = "24. 업체 대기 관리(관리자)", description = "플랫폼 관리자가 업체별 대기 순번을 확인·발급·호출·착석·취소한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/tenants/{tenantId}/waitlist")
@RequiredArgsConstructor
public class PlatformWaitlistController {

    private final TenantWaitlistService waitlistService;

    @Operation(summary = "업체 대기 현황")
    @GetMapping
    public ResponseEntity<WaitlistBoard> board(@PathVariable Long tenantId) {
        return ResponseEntity.ok(waitlistService.board(tenantId));
    }

    @Operation(summary = "업체 대기 순번 발급")
    @PostMapping
    public ResponseEntity<WaitlistEntryView> add(@PathVariable Long tenantId, @Valid @RequestBody WaitlistAddRequest req) {
        return ResponseEntity.ok(waitlistService.add(tenantId, req));
    }

    @Operation(summary = "업체 대기 상태 변경", description = "CALLED(호출) / SEATED(착석).")
    @PatchMapping("/{id}/status")
    public ResponseEntity<WaitlistEntryView> changeStatus(@PathVariable Long tenantId, @PathVariable Long id,
                                                          @Valid @RequestBody WaitlistStatusRequest req) {
        return ResponseEntity.ok(waitlistService.changeStatus(tenantId, id, req.status()));
    }

    @Operation(summary = "업체 대기 취소")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long tenantId, @PathVariable Long id) {
        waitlistService.cancel(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
