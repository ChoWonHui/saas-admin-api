package com.saas.admin.tenant.staff;

import com.saas.admin.tenant.staff.StaffDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 업체 직원(로그인 계정) 관리. 플랫폼 관리자만. */
@Tag(name = "12. 업체 직원", description = "업체별 직원(대표/매니저/직원) 로그인 계정 관리.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/tenants/{tenantId}/staff")
@RequiredArgsConstructor
public class TenantStaffController {

    private final TenantStaffService staffService;

    @Operation(summary = "직원 목록", description = "이 업체의 대표·매니저·직원 계정 목록.")
    @GetMapping
    public ResponseEntity<List<StaffResponse>> list(@PathVariable Long tenantId) {
        return ResponseEntity.ok(staffService.list(tenantId));
    }

    @Operation(summary = "직원 계정 생성", description = "로그인 계정(이메일/비번) + 소속·역할을 만든다.")
    @PostMapping
    public ResponseEntity<StaffResponse> create(@PathVariable Long tenantId,
                                                @Valid @RequestBody StaffCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.create(tenantId, request));
    }

    @Operation(summary = "직원 정보 수정", description = "이름·연락처·역할·상태(활성/정지)를 바꾼다.")
    @PatchMapping("/{tenantUserId}")
    public ResponseEntity<StaffResponse> update(@PathVariable Long tenantId, @PathVariable Long tenantUserId,
                                                @Valid @RequestBody StaffUpdateRequest request) {
        return ResponseEntity.ok(staffService.update(tenantId, tenantUserId, request));
    }

    @Operation(summary = "비밀번호 재설정")
    @PostMapping("/{tenantUserId}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long tenantId, @PathVariable Long tenantUserId,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        staffService.resetPassword(tenantId, tenantUserId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "직원 삭제", description = "멤버십을 지운다. 다른 업체 소속이 없으면 로그인 계정도 삭제. 대표는 삭제 불가.")
    @DeleteMapping("/{tenantUserId}")
    public ResponseEntity<Void> delete(@PathVariable Long tenantId, @PathVariable Long tenantUserId) {
        staffService.delete(tenantId, tenantUserId);
        return ResponseEntity.noContent().build();
    }
}
