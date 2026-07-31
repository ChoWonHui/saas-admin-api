package com.saas.admin.tenant.staff;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.staff.StaffDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사장님 콘솔의 직원 관리 — 대표(사장님)가 자기 가게 직원(매니저/직원) 계정을 직접 만들고 관리한다.
 * 플랫폼 관리자용({@link TenantStaffController})과 같은 서비스를 쓰되, 업체는 토큰(대표)에서 가져온다.
 */
@Tag(name = "14. 직원 관리(업체)", description = "대표가 자기 가게 직원 로그인 계정을 관리한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/staff")
@RequiredArgsConstructor
public class TenantConsoleStaffController {

    private static final String OWNER = "TENANT_OWNER";

    private final TenantStaffService staffService;

    @Operation(summary = "직원 목록 (우리 가게)")
    @GetMapping
    public ResponseEntity<List<StaffResponse>> list(@AuthenticationPrincipal AuthPrincipal p) {
        return ResponseEntity.ok(staffService.list(tenantId(p)));
    }

    @Operation(summary = "직원 추가", description = "로그인 아이디/비밀번호 + 이름·역할. 대표 역할은 만들 수 없다(가게당 1명).")
    @PostMapping
    public ResponseEntity<StaffResponse> create(@AuthenticationPrincipal AuthPrincipal p,
                                                @Valid @RequestBody StaffCreateRequest req) {
        long tid = tenantId(p);
        denyOwnerRole(req.roleId());
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.create(tid, req));
    }

    @Operation(summary = "직원 수정 (이름·연락처·역할·상태)")
    @PatchMapping("/{tenantUserId}")
    public ResponseEntity<StaffResponse> update(@AuthenticationPrincipal AuthPrincipal p,
                                                @PathVariable Long tenantUserId,
                                                @Valid @RequestBody StaffUpdateRequest req) {
        long tid = tenantId(p);
        denyOwnerRole(req.roleId());
        return ResponseEntity.ok(staffService.update(tid, tenantUserId, req));
    }

    @Operation(summary = "직원 비밀번호 재설정")
    @PostMapping("/{tenantUserId}/password")
    public ResponseEntity<Void> resetPassword(@AuthenticationPrincipal AuthPrincipal p,
                                              @PathVariable Long tenantUserId,
                                              @Valid @RequestBody ResetPasswordRequest req) {
        staffService.resetPassword(tenantId(p), tenantUserId, req);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "직원 삭제", description = "대표(본인) 계정은 삭제할 수 없다.")
    @DeleteMapping("/{tenantUserId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal p,
                                       @PathVariable Long tenantUserId) {
        staffService.delete(tenantId(p), tenantUserId);
        return ResponseEntity.noContent().build();
    }

    /** 직원 관리는 대표(사장님)만. 토큰에 업체 컨텍스트 + 대표 역할이 있어야 한다. */
    private Long tenantId(AuthPrincipal p) {
        if (p == null || p.isAdmin() || !p.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
        if (!OWNER.equals(p.roleCode())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "직원 관리는 대표만 가능합니다.");
        }
        return p.tenantId();
    }

    /** 대표(role 2)는 콘솔에서 만들거나 배정할 수 없다 — 가게당 대표는 1명. */
    private void denyOwnerRole(Integer roleId) {
        if (com.saas.admin.auth.domain.Role.TENANT_OWNER_ID.equals(roleId)) {
            throw new ApiException(ErrorCode.STAFF_ROLE_INVALID, "대표 역할은 콘솔에서 배정할 수 없습니다.");
        }
    }
}
