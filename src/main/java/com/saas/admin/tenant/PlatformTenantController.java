package com.saas.admin.tenant;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.audit.AuditService;
import com.saas.admin.tenant.domain.TenantStatus;
import com.saas.admin.tenant.dto.*;
import com.saas.admin.tenant.dto.BranchDtos.*;
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
    private final TenantBranchService branchService;

    @org.springframework.beans.factory.annotation.Value("${order.base-url:http://localhost:5173}")
    private String orderBaseUrl;

    @Operation(
            summary = "업체 등록 + 대표 계정(온보딩)",
            description = """
                    **업체 + 대표 계정 + 구독을 한 트랜잭션에서 생성**한다.
                    (업체 정보만 등록하려면 `POST /` 를 쓴다)

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
    @PostMapping("/with-owner")
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

    @Operation(summary = "업체 목록",
            description = "기본은 삭제되지 않은 업체만. `includeDeleted=true` 면 삭제된 것도 포함. `status` 로 상태 필터.")
    @GetMapping
    public ResponseEntity<Page<TenantResponse>> list(
            @Parameter(description = "상태 필터 (생략 시 전체)")
            @RequestParam(required = false) TenantStatus status,
            @Parameter(description = "삭제된 업체 포함 여부")
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(tenantService.list(status, includeDeleted, pageable));
    }

    @Operation(summary = "요금제 목록", description = "업체 등록·수정 콤보박스용.")
    @GetMapping("/plans")
    public ResponseEntity<java.util.List<TenantPlanResponse>> plans() {
        return ResponseEntity.ok(tenantService.plans());
    }

    @Operation(summary = "업체 등록(업체 정보만)",
            description = "업체 정보만 등록한다(대표 계정 없이). PENDING 으로 생성되며 개설은 /activate 로.")
    @PostMapping
    public ResponseEntity<TenantResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody TenantCreateRequest request,
                                                 HttpServletRequest servletRequest) {
        TenantResponse response = tenantService.create(
                request, principal.userId(),
                AuditService.clientIp(servletRequest), AuditService.userAgent(servletRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "업체 수정", description = "업체명·요금제·대표자·연락처·주소 등을 수정한다. slug·상태는 바꾸지 않는다.")
    @PatchMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable Long tenantId,
                                                 @Valid @RequestBody TenantUpdateRequest request,
                                                 HttpServletRequest servletRequest) {
        return ResponseEntity.ok(tenantService.update(
                tenantId, request, principal.userId(),
                AuditService.clientIp(servletRequest), AuditService.userAgent(servletRequest)));
    }

    @Operation(summary = "업체 삭제(소프트)", description = "삭제여부를 'Y' 로 바꾼다. 목록에서 숨겨지고 복구할 수 있다.")
    @ApiResponse(responseCode = "409", description = "이미 삭제됨(TENANT_ALREADY_DELETED)", content = @Content)
    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long tenantId,
                                       HttpServletRequest servletRequest) {
        tenantService.delete(tenantId, principal.userId(),
                AuditService.clientIp(servletRequest), AuditService.userAgent(servletRequest));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "업체 삭제 복구", description = "삭제여부를 'N' 으로 되돌린다.")
    @PostMapping("/{tenantId}/restore")
    public ResponseEntity<TenantResponse> restore(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Long tenantId,
                                                  HttpServletRequest servletRequest) {
        return ResponseEntity.ok(tenantService.restore(
                tenantId, principal.userId(),
                AuditService.clientIp(servletRequest), AuditService.userAgent(servletRequest)));
    }

    // ==================== 지점(호점) ====================

    @Operation(summary = "지점(호점) 목록", description = "이 업체의 지점을 호점 순으로 반환.")
    @GetMapping("/{tenantId}/branches")
    public ResponseEntity<java.util.List<BranchResponse>> branches(@PathVariable Long tenantId) {
        return ResponseEntity.ok(branchService.list(tenantId));
    }

    @Operation(summary = "지점(호점) 추가", description = "호점 번호는 서버가 자동 채번한다(기존 최댓값 + 1).")
    @PostMapping("/{tenantId}/branches")
    public ResponseEntity<BranchResponse> addBranch(@PathVariable Long tenantId,
                                                    @Valid @RequestBody BranchCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchService.create(tenantId, request));
    }

    @Operation(summary = "지점(호점) 수정")
    @PatchMapping("/{tenantId}/branches/{branchId}")
    public ResponseEntity<BranchResponse> updateBranch(@PathVariable Long tenantId,
                                                       @PathVariable Long branchId,
                                                       @Valid @RequestBody BranchUpdateRequest request) {
        return ResponseEntity.ok(branchService.update(tenantId, branchId, request));
    }

    @Operation(summary = "지점(호점) 삭제(소프트)")
    @DeleteMapping("/{tenantId}/branches/{branchId}")
    public ResponseEntity<Void> deleteBranch(@PathVariable Long tenantId, @PathVariable Long branchId) {
        branchService.delete(tenantId, branchId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 영업장 테이블 배치 ====================

    @Operation(summary = "지점 영업장 배치 조회", description = "포장전문점 여부·층수·테이블(좌표 포함) 목록.")
    @GetMapping("/{tenantId}/branches/{branchId}/layout")
    public ResponseEntity<LayoutResponse> layout(@PathVariable Long tenantId, @PathVariable Long branchId) {
        return ResponseEntity.ok(branchService.layout(tenantId, branchId));
    }

    @Operation(summary = "지점 영업장 배치 저장", description = "테이블은 code 기준 upsert(위치만 갱신, 코드·QR 유지).")
    @PutMapping("/{tenantId}/branches/{branchId}/layout")
    public ResponseEntity<LayoutResponse> saveLayout(@PathVariable Long tenantId, @PathVariable Long branchId,
                                                     @RequestBody LayoutSaveRequest request) {
        return ResponseEntity.ok(branchService.saveLayout(tenantId, branchId, request));
    }

    @Operation(summary = "테이블 주문 QR 이미지", description = "이 테이블의 주문 URL 을 담은 QR PNG. (order.base-url + /{업체코드}/{테이블코드})")
    @GetMapping(value = "/{tenantId}/branches/{branchId}/tables/{tableId}/qr",
            produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> tableQr(@PathVariable Long tenantId, @PathVariable Long branchId,
                                          @PathVariable Long tableId) {
        var table = branchService.findByCode(tenantId, branchId, tableId);
        // QR 은 "내URL/업체코드/테이블코드" 형태로 발급한다. 둘 다 예측 불가능한 코드라 안전하고,
        // 손님 주문 화면이 업체·테이블을 한 번에 식별할 수 있다.
        String tenantCode = tenantService.get(tenantId).tenantCode();
        String url = orderBaseUrl + "/" + tenantCode + "/" + table.getCode();
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .body(QrGenerator.png(url, 360));
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
