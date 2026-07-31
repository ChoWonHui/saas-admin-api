package com.saas.admin.tenant.waitlist;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.waitlist.WaitlistDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 사장님 콘솔 — 대기(예약) 관리. 테이블이 모두 차 있을 때 대기 순번을 발급·관리한다. */
@Tag(name = "16. 대기 관리(업체)", description = "테이블이 모두 사용 중일 때 손님에게 대기 순번을 발급한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/waitlist")
@RequiredArgsConstructor
public class TenantWaitlistController {

    private final TenantWaitlistService waitlistService;

    @Operation(summary = "대기 현황", description = "테이블 점유 현황 + 활성 대기 목록.")
    @GetMapping
    public ResponseEntity<WaitlistBoard> board(@AuthenticationPrincipal AuthPrincipal p) {
        return ResponseEntity.ok(waitlistService.board(tenantId(p)));
    }

    @Operation(summary = "대기 순번 발급")
    @PostMapping
    public ResponseEntity<WaitlistEntryView> add(@AuthenticationPrincipal AuthPrincipal p,
                                                 @Valid @RequestBody WaitlistAddRequest req) {
        return ResponseEntity.ok(waitlistService.add(tenantId(p), req));
    }

    @Operation(summary = "대기 상태 변경", description = "CALLED(호출) / SEATED(착석).")
    @PatchMapping("/{id}/status")
    public ResponseEntity<WaitlistEntryView> changeStatus(@AuthenticationPrincipal AuthPrincipal p,
                                                          @PathVariable Long id,
                                                          @Valid @RequestBody WaitlistStatusRequest req) {
        return ResponseEntity.ok(waitlistService.changeStatus(tenantId(p), id, req.status()));
    }

    @Operation(summary = "대기 취소")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthPrincipal p, @PathVariable Long id) {
        waitlistService.cancel(tenantId(p), id);
        return ResponseEntity.noContent().build();
    }

    private Long tenantId(AuthPrincipal p) {
        if (p == null || p.isAdmin() || !p.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
        return p.tenantId();
    }
}
