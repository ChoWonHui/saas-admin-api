package com.saas.admin.auth;

import com.saas.admin.auth.dto.*;
import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "1. 인증", description = "로그인 / 업체 선택 / 토큰 재발급")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "로그인",
            description = """
                    이메일 + 비밀번호로 로그인한다. **인증이 필요 없다.**

                    반환되는 accessToken 에는 **테넌트 컨텍스트가 없다.**

                    - **플랫폼 관리자**(`platformAdmin: true`): 이 토큰만으로 `/api/platform-admin/**` 을 쓸 수 있다.
                      `memberships` 는 빈 배열이다.
                    - **업체 사용자**: `memberships` 에서 업체를 고른 뒤 `/api/auth/select-tenant` 를 호출해
                      테넌트 컨텍스트가 담긴 토큰을 다시 받아야 한다. 한 사람이 여러 업체에 소속될 수 있기 때문이다.

                    연속 5회 실패하면 계정이 15분간 잠긴다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "이메일/비밀번호 불일치(INVALID_CREDENTIALS) 또는 계정 잠김(ACCOUNT_LOCKED)", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "사용할 수 없는 계정(ACCOUNT_DISABLED)", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @SecurityRequirements  // 인증 불필요
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest servletRequest) {
        return ResponseEntity.ok(authService.login(
                request,
                AuditService.clientIp(servletRequest),
                AuditService.userAgent(servletRequest)));
    }

    @Operation(
            summary = "업체 로그인 (업체코드 + 아이디 + 비밀번호)",
            description = """
                    사장님/직원이 **업체코드 + 로그인 아이디 + 비밀번호**로 한 번에 로그인한다.
                    업체코드가 가게를 특정하므로 별도의 업체 선택 단계 없이
                    바로 `tenantId` + `roleCode` 가 담긴 토큰을 돌려준다.

                    아이디는 '가게 안에서만' 유일하다. 업체코드/아이디/비밀번호가 하나라도
                    틀리면 어느 것이 틀렸는지 구분해 알려주지 않는다(INVALID_CREDENTIALS).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "업체코드/아이디/비밀번호 불일치(INVALID_CREDENTIALS) 또는 계정 잠김(ACCOUNT_LOCKED)", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "403", description = "사용할 수 없는 계정/소속(ACCOUNT_DISABLED, NOT_A_MEMBER)", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @SecurityRequirements  // 인증 불필요
    @PostMapping("/tenant-login")
    public ResponseEntity<TokenResponse> tenantLogin(@Valid @RequestBody TenantLoginRequest request,
                                                     HttpServletRequest servletRequest) {
        return ResponseEntity.ok(authService.tenantLogin(
                request.tenantCode(),
                request.loginId(),
                request.password(),
                AuditService.clientIp(servletRequest),
                AuditService.userAgent(servletRequest)));
    }

    @Operation(
            summary = "업체코드로 가게 이름 확인 (로그인 전, 공개)",
            description = "자동입력 로그인 링크에서 '우리 가게가 맞는지' 이름을 보여주기 위한 공개 조회. 코드가 유효하지 않으면 found=false 만 돌려준다.")
    @SecurityRequirements  // 인증 불필요
    @GetMapping("/tenant-lookup")
    public ResponseEntity<TenantLookupResponse> tenantLookup(@RequestParam("code") String code) {
        return ResponseEntity.ok(authService.tenantLookup(code));
    }

    @Operation(
            summary = "업체 선택 → 테넌트 컨텍스트 토큰 재발급",
            description = """
                    로그인 응답의 `memberships` 중 하나를 골라 호출한다.
                    반환된 accessToken 에는 `tenantId` 와 `roleCode` 가 담긴다.

                    **프론트가 보낸 tenantId 는 신뢰하지 않는다.** 서버가 `tenant_user` 에서
                    실제 소속과 상태를 매번 재검증한다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "403", description = "해당 업체 소속이 아님(NOT_A_MEMBER)", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "404", description = "업체 없음(TENANT_NOT_FOUND)", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping("/select-tenant")
    public ResponseEntity<TokenResponse> selectTenant(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @Valid @RequestBody SelectTenantRequest request,
                                                      HttpServletRequest servletRequest) {
        return ResponseEntity.ok(authService.selectTenant(
                principal,
                request.tenantId(),
                AuditService.clientIp(servletRequest),
                AuditService.userAgent(servletRequest)));
    }

    @Operation(
            summary = "액세스 토큰 재발급",
            description = """
                    리프레시 토큰으로 새 액세스 토큰을 받는다. **인증이 필요 없다.**

                    **토큰 회전**: 사용한 리프레시 토큰은 즉시 폐기되고 새 리프레시 토큰이 함께 발급된다.
                    재발급 시점에 소속과 계정 상태를 다시 확인하므로, 권한이 회수됐다면 여기서 막힌다.
                    """)
    @SecurityRequirements  // 인증 불필요
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                 HttpServletRequest servletRequest) {
        return ResponseEntity.ok(authService.refresh(
                request.refreshToken(),
                AuditService.clientIp(servletRequest),
                AuditService.userAgent(servletRequest)));
    }

    @Operation(
            summary = "로그아웃",
            description = "해당 사용자의 살아있는 리프레시 토큰을 전부 폐기한다. 액세스 토큰은 만료까지 유효하다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logout(principal);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "현재 토큰의 주체 조회",
            description = "토큰에 담긴 userId / email / tenantId / roleCode 를 그대로 돌려준다. tenantId 가 null 이면 테넌트 컨텍스트가 없는 토큰이다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ResponseEntity<AuthPrincipal> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(principal);
    }
}
