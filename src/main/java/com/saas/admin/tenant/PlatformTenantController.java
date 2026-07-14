package com.saas.admin.tenant;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.audit.AuditService;
import com.saas.admin.tenant.domain.TenantStatus;
import com.saas.admin.tenant.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 플랫폼 통합 관리자 — 업체 등록 / 조회 / 개설 / 중지. (설계안 §3.3)
 * 이 경로는 SecurityConfig 에서 ROLE_PLATFORM_ADMIN 만 통과한다.
 */
@Tag(name = "2. 플랫폼 관리자 - 업체",
        description = "업체 등록 / 조회 / 서비스 개설·중지. **플랫폼 관리자(is_platform_admin) 만 접근 가능**")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final TenantService tenantService;

    @Operation(
            summary = "업체 등록",
            description = """
                    **업체 + 대표 계정 + 구독을 한 트랜잭션에서 생성**한다.

                    업체는 `PENDING` 상태로 만들어진다. 고객 화면에 노출하려면
                    `/activate` 를 따로 호출해 개설해야 한다.

                    ### slug 검증 (등록 시점에 3중으로 막는다)

                    | 상황 | 응답 |
                    |---|---|
                    | 형식 위반 — 한글, 연속 하이픈(`--`), 3자 미만, 하이픈으로 시작·종료 | `400 SLUG_INVALID_FORMAT` |
                    | 예약어 — `admin`, `api`, `login` 등 37건 | `409 SLUG_RESERVED` |
                    | 이미 사용 중 | `409 SLUG_DUPLICATED` |

                    slug 는 소문자로 정규화된다. 업체명(`tenantName`)은 한글을 써도 된다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 오류 또는 slug 형식 위반", content = @Content),
            @ApiResponse(responseCode = "403", description = "플랫폼 관리자가 아님", content = @Content),
            @ApiResponse(responseCode = "404", description = "요금제 없음(PLAN_NOT_FOUND)", content = @Content),
            @ApiResponse(responseCode = "409", description = "slug 예약어/중복 또는 이메일 중복(EMAIL_DUPLICATED)", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CreateTenantResponse> register(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @Valid @RequestBody CreateTenantRequest request,
                                                         HttpServletRequest servletRequest) {
        CreateTenantResponse response = tenantService.register(
                request,
                principal.userId(),
                AuditService.clientIp(servletRequest),
                AuditService.userAgent(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "업체 목록", description = "`status` 로 걸러낼 수 있다. 생략하면 전체를 반환한다.")
    @GetMapping
    public ResponseEntity<Page<TenantResponse>> list(
            @Parameter(description = "상태 필터 (생략 시 전체)")
            @RequestParam(required = false) TenantStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(tenantService.list(status, pageable));
    }

    @Operation(summary = "업체 단건 조회")
    @ApiResponse(responseCode = "404", description = "TENANT_NOT_FOUND", content = @Content)
    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> get(@PathVariable Long tenantId) {
        return ResponseEntity.ok(tenantService.get(tenantId));
    }

    @Operation(
            summary = "서비스 개설",
            description = """
                    업체를 `ACTIVE` 로 만들어 고객 화면에 노출시킨다.
                    `PENDING` 또는 `SUSPENDED` 에서만 가능하다. 그 외 상태면 `409` 다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "개설 완료"),
            @ApiResponse(responseCode = "409", description = "허용되지 않는 상태 전이(INVALID_STATUS_TRANSITION)", content = @Content)
    })
    @PostMapping("/{tenantId}/activate")
    public ResponseEntity<TenantResponse> activate(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable Long tenantId,
                                                   HttpServletRequest servletRequest) {
        return ResponseEntity.ok(tenantService.activate(
                tenantId,
                principal.userId(),
                AuditService.clientIp(servletRequest),
                AuditService.userAgent(servletRequest)));
    }

    @Operation(
            summary = "서비스 중지",
            description = """
                    요금 미납·운영 정지 등의 사유로 업체를 `SUSPENDED` 로 만든다. `ACTIVE` 에서만 가능하다.

                    중지된 업체의 고객 화면은 **404 가 아니라 503 + 안내 페이지**로 응답한다.
                    검색엔진이 색인을 삭제하지 않도록 하기 위함이다. (설계안 §4.1)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "중지 완료"),
            @ApiResponse(responseCode = "409", description = "ACTIVE 가 아님(INVALID_STATUS_TRANSITION)", content = @Content)
    })
    @PostMapping("/{tenantId}/suspend")
    public ResponseEntity<TenantResponse> suspend(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Long tenantId,
                                                  @Valid @RequestBody SuspendTenantRequest request,
                                                  HttpServletRequest servletRequest) {
        return ResponseEntity.ok(tenantService.suspend(
                tenantId,
                request.reason(),
                principal.userId(),
                AuditService.clientIp(servletRequest),
                AuditService.userAgent(servletRequest)));
    }
}
