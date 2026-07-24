package com.saas.admin.tenantnotice;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenantnotice.dto.TenantNoticeDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 업체(사장님) 콘솔의 공지사항 — 관리자가 등록한 공지를 조회만 한다. 팝업 목록도 제공. */
@Tag(name = "14. 업체 공지사항(업체)", description = "사장님이 관리자 공지를 조회한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/notices")
@RequiredArgsConstructor
public class TenantNoticeBoardController {

    private final TenantNoticeService service;

    @Operation(summary = "공지 목록 (고정 먼저, 최신순)")
    @GetMapping
    public ResponseEntity<List<TenantNoticeSummary>> list(@AuthenticationPrincipal AuthPrincipal p) {
        requireTenant(p);
        return ResponseEntity.ok(service.listForTenant());
    }

    @Operation(summary = "공지 상세 (조회수 +1)")
    @GetMapping("/{id}")
    public ResponseEntity<TenantNoticeDetail> get(@AuthenticationPrincipal AuthPrincipal p, @PathVariable Long id) {
        requireTenant(p);
        return ResponseEntity.ok(service.getForTenant(id));
    }

    @Operation(summary = "지금 띄울 팝업 공지 목록")
    @GetMapping("/popups")
    public ResponseEntity<List<TenantNoticePopup>> popups(@AuthenticationPrincipal AuthPrincipal p) {
        requireTenant(p);
        return ResponseEntity.ok(service.activePopups());
    }

    private void requireTenant(AuthPrincipal p) {
        if (p == null || p.isAdmin() || !p.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
    }
}
