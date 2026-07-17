package com.saas.admin.adminaccount;

import com.saas.admin.adminaccount.dto.AdminDtos.AdminLoginRequest;
import com.saas.admin.adminaccount.dto.AdminDtos.AdminMeResponse;
import com.saas.admin.adminaccount.dto.AdminDtos.AdminRefreshRequest;
import com.saas.admin.adminaccount.dto.AdminDtos.AdminTokenResponse;
import com.saas.admin.adminaccount.dto.AdminDtos.ChangeMyPasswordRequest;
import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.audit.AuditService;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "0. 관리자 인증", description = "내부 직원(플랫폼 관리자) — 사번으로 로그인한다")
@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminAccountService adminAccountService;
    private final com.saas.admin.permission.PermissionService permissionService;

    @Operation(
            summary = "관리자 로그인 (사번)",
            description = """
                    사번 + 비밀번호로 로그인한다. **인증이 필요 없다.**

                    업체 사용자의 `/api/auth/login`(이메일) 과는 다른 엔드포인트다.
                    식별자 체계가 달라서 분리했다 — 관리자는 사번(260001), 업체 사용자는 이메일.

                    발급된 토큰에는 `subjectType: ADMIN` 이 담긴다. 이 토큰만으로
                    `/api/platform-admin/**` 을 쓸 수 있다. 연속 5회 실패하면 15분간 잠긴다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "사번/비밀번호 불일치(INVALID_ADMIN_CREDENTIALS) 또는 계정 잠김(ACCOUNT_LOCKED)", content = @Content),
            @ApiResponse(responseCode = "403", description = "사용할 수 없는 계정(ACCOUNT_DISABLED)", content = @Content)
    })
    @SecurityRequirements  // 인증 불필요
    @PostMapping("/login")
    public ResponseEntity<AdminTokenResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                                    HttpServletRequest servletRequest) {
        return ResponseEntity.ok(adminAuthService.login(
                request.empNo(),
                request.password(),
                AuditService.clientIp(servletRequest),
                AuditService.userAgent(servletRequest)));
    }

    @Operation(
            summary = "관리자 액세스 토큰 재발급",
            description = """
                    **토큰 회전**: 사용한 리프레시 토큰은 즉시 폐기되고 새 것이 함께 발급된다.
                    재발급 시점에 계정 상태를 다시 확인하므로, 그 사이 삭제·정지됐다면 여기서 막힌다.
                    """)
    @SecurityRequirements  // 인증 불필요
    @PostMapping("/refresh")
    public ResponseEntity<AdminTokenResponse> refresh(@Valid @RequestBody AdminRefreshRequest request,
                                                      HttpServletRequest servletRequest) {
        return ResponseEntity.ok(adminAuthService.refresh(
                request.refreshToken(),
                AuditService.clientIp(servletRequest),
                AuditService.userAgent(servletRequest)));
    }

    @Operation(
            summary = "관리자 로그아웃",
            description = "이 관리자의 살아있는 리프레시 토큰을 전부 폐기한다. 액세스 토큰은 만료까지 유효하다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        adminAuthService.logout(principal);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "현재 관리자 조회",
            description = """
                    콘솔 헤더 표시용. 이름은 토큰이 아니라 **DB 에서** 읽는다 — 이름을 수정하면
                    재로그인 없이 다음 조회부터 반영된다. mustChangePassword 는 토큰 클레임 값이다
                    (서버 권한 판정과 같은 기준). isSuper 면 메뉴 권한 규칙을 우회한다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ResponseEntity<AdminMeResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        // 업체 사용자 토큰은 이 엔드포인트의 주체가 아니다.
        if (!principal.isAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return ResponseEntity.ok(AdminMeResponse.of(
                adminAccountService.get(principal.empNo()),
                principal.mustChangePassword()));
    }

    @Operation(
            summary = "내 접근 가능 메뉴 id",
            description = """
                    로그인한 관리자가 접근할 수 있는 메뉴 id 목록. 부서(조직) 허용 ∪ 직책 허용에
                    부모 메뉴·대시보드를 더한 것이다. 슈퍼관리자는 전 메뉴가 나온다.
                    프론트가 네비게이션·라우트 가드에 쓴다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/my-menus")
    public ResponseEntity<java.util.Set<Long>> myMenus(@AuthenticationPrincipal AuthPrincipal principal) {
        if (!principal.isAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return ResponseEntity.ok(permissionService.allowedMenuIds(adminAccountService.get(principal.empNo())));
    }

    @Operation(
            summary = "본인 비밀번호 변경",
            description = """
                    로그인한 본인이 자기 비밀번호를 바꾼다. 기본 비밀번호(exprism1234!)로 로그인한 사람은
                    **이것 말고는 아무 API 도 쓸 수 없다.**

                    - `newPassword` 와 `confirmPassword` 가 정확히 같아야 한다. 다르면 400.
                    - 기본 비밀번호를 그대로 쓸 수 없다. 다르면 400.
                    - 성공하면 **모든 토큰이 폐기된다.** 바로 들어가지지 않고 다시 로그인해야 한다 —
                      새 비밀번호를 한 번 더 입력하게 해 기억을 굳히기 위함이다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경됨. 새 비밀번호로 다시 로그인해야 한다."),
            @ApiResponse(responseCode = "400", description = "확인 불일치(PASSWORD_CONFIRM_MISMATCH) 또는 초기 비밀번호와 동일(PASSWORD_SAME_AS_DEFAULT)", content = @Content)
    })
    @PostMapping("/password")
    public ResponseEntity<Void> changeMyPassword(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody ChangeMyPasswordRequest request) {
        adminAccountService.changeMyPassword(principal.empNo(), request.newPassword(), request.confirmPassword());
        return ResponseEntity.noContent().build();
    }
}
