package com.saas.admin.tenantnotice;

import com.saas.admin.adminaccount.AdminAccountService;
import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenantnotice.dto.TenantNoticeDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 업체 공지사항 — 관리자 등록/관리. 업체(사장님)에게 노출되는 공지를 여기서 쓴다.
 * (/api/platform-admin/** 는 PLATFORM_ADMIN 만 통과)
 */
@Tag(name = "14. 업체 공지사항(관리자)", description = "업체에 노출할 공지 등록/관리. 상단 고정 + 팝업(기간).")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/tenant-notices")
@RequiredArgsConstructor
public class PlatformTenantNoticeController {

    private final TenantNoticeService service;
    private final AdminAccountService adminAccountService;

    private AdminAccount me(AuthPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return adminAccountService.get(principal.empNo());
    }

    @Operation(summary = "업체 공지 목록", description = "고정 먼저, 최신순. keyword 로 제목 검색.")
    @GetMapping
    public ResponseEntity<Page<TenantNoticeSummary>> list(@RequestParam(defaultValue = "") String keyword,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.list(keyword, page, size));
    }

    @Operation(summary = "업체 공지 상세")
    @GetMapping("/{id}")
    public ResponseEntity<TenantNoticeDetail> get(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal p) {
        return ResponseEntity.ok(service.getForAdmin(id, me(p)));
    }

    @Operation(summary = "업체 공지 작성")
    @PostMapping
    public ResponseEntity<TenantNoticeDetail> create(@Valid @RequestBody CreateTenantNoticeRequest req,
                                                     @AuthenticationPrincipal AuthPrincipal p) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, me(p)));
    }

    @Operation(summary = "업체 공지 수정", description = "작성자 본인 또는 대표만.")
    @PatchMapping("/{id}")
    public ResponseEntity<TenantNoticeDetail> update(@PathVariable Long id, @Valid @RequestBody UpdateTenantNoticeRequest req,
                                                     @AuthenticationPrincipal AuthPrincipal p) {
        return ResponseEntity.ok(service.update(id, req, me(p)));
    }

    @Operation(summary = "업체 공지 삭제", description = "작성자 본인 또는 대표만.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal p) {
        service.delete(id, me(p));
        return ResponseEntity.noContent().build();
    }
}
