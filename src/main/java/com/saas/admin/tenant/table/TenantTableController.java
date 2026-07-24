package com.saas.admin.tenant.table;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.QrGenerator;
import com.saas.admin.tenant.dto.BranchDtos.LayoutResponse;
import com.saas.admin.tenant.dto.BranchDtos.LayoutSaveRequest;
import com.saas.admin.tenant.table.TenantTableDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 업체(사장님) 콘솔의 테이블 관리. 업체코드(토큰의 테넌트)를 키로 자기 가게 테이블을 만들고 QR 을 뽑는다.
 */
@Tag(name = "12. 테이블(업체)", description = "사장님이 자기 가게 테이블을 생성하고 주문 QR 을 받는다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/tables")
@RequiredArgsConstructor
public class TenantTableController {

    private final TenantTableService tableService;

    @Value("${order.base-url:http://localhost:5173}")
    private String orderBaseUrl;

    @Operation(summary = "테이블 배치도 조회 (기본 지점)", description = "관리자 콘솔과 동일한 배치 편집기용 데이터.")
    @GetMapping("/layout")
    public ResponseEntity<LayoutResponse> layout(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(tableService.getLayout(tenantId(principal)));
    }

    @Operation(summary = "테이블 배치도 저장 (기본 지점)")
    @PutMapping("/layout")
    public ResponseEntity<LayoutResponse> saveLayout(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @Valid @RequestBody LayoutSaveRequest req) {
        return ResponseEntity.ok(tableService.saveLayout(tenantId(principal), req));
    }

    @Operation(summary = "테이블 목록 (우리 가게)")
    @GetMapping
    public ResponseEntity<List<TableView>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(tableService.list(tenantId(principal)));
    }

    @Operation(summary = "테이블 생성")
    @PostMapping
    public ResponseEntity<TableView> create(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody TableCreateRequest req) {
        return ResponseEntity.ok(tableService.create(tenantId(principal), req));
    }

    @Operation(summary = "테이블 삭제")
    @DeleteMapping("/{tableId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long tableId) {
        tableService.delete(tenantId(principal), tableId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "테이블 주문 QR (내URL/업체코드/테이블코드)")
    @GetMapping(value = "/{tableId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable Long tableId) {
        String url = tableService.orderUrl(tenantId(principal), tableId, orderBaseUrl);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(QrGenerator.png(url, 360));
    }

    /** 토큰에 업체(테넌트) 컨텍스트가 있어야 한다. */
    private Long tenantId(AuthPrincipal principal) {
        if (principal == null || principal.isAdmin() || !principal.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
        return principal.tenantId();
    }
}
