package com.saas.admin.permission;

import com.saas.admin.adminaccount.AdminAccountService;
import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.permission.domain.PermissionSubject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "7. 메뉴 권한", description = "부서(조직)별·직책별 메뉴 접근 권한. 권한관리 메뉴 접근 권한이 있는 관리자만 설정할 수 있다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;
    private final AdminAccountService adminAccountService;

    public record PermissionRequest(
            @NotNull PermissionSubject subjectType,
            @NotBlank String subjectKey,
            List<Long> menuIds
    ) {
    }

    @Operation(summary = "주체별 허용 메뉴 조회", description = "부서(ORG, key=조직 id) 또는 직책(TITLE, key=직책명)에 허용된 메뉴 id 목록.")
    @GetMapping
    public ResponseEntity<List<Long>> get(@RequestParam PermissionSubject subjectType,
                                          @RequestParam String subjectKey,
                                          @AuthenticationPrincipal AuthPrincipal principal) {
        requirePermissionMenu(principal);
        return ResponseEntity.ok(permissionService.permissionsOf(subjectType, subjectKey));
    }

    @Operation(summary = "주체별 허용 메뉴 저장", description = "보낸 menuIds 로 그 주체의 권한을 통째로 교체한다.")
    @PutMapping
    public ResponseEntity<Void> put(@RequestBody PermissionRequest request,
                                    @AuthenticationPrincipal AuthPrincipal principal) {
        requirePermissionMenu(principal);
        permissionService.replacePermissions(request.subjectType(), request.subjectKey(), request.menuIds());
        return ResponseEntity.noContent().build();
    }

    /** 권한관리 화면(/permissions)의 URL. 이 메뉴에 접근 권한이 있는 사람만 권한을 설정할 수 있다. */
    private static final String PERMISSION_MENU_URL = "/permissions";

    /**
     * 권한 설정은 <b>권한관리 메뉴 접근 권한이 있는 관리자</b>만.
     * (슈퍼는 전 메뉴 접근이므로 당연히 포함) — 화면에서 안 보이는 사람이 API 를 직접 호출해 권한을 바꾸는 것을 막는다.
     */
    private void requirePermissionMenu(AuthPrincipal principal) {
        if (!permissionService.hasMenuAccess(adminAccountService.get(principal.empNo()), PERMISSION_MENU_URL)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
