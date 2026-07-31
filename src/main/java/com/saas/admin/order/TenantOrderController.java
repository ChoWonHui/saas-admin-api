package com.saas.admin.order;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.order.dto.OrderDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 사장님 콘솔의 주문 관리 — 자기 가게 주문 조회/상태변경. tenantId 는 토큰에서. */
@Tag(name = "15. 주문(업체)", description = "사장님이 자기 가게 주문을 확인하고 상태를 바꾼다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/orders")
@RequiredArgsConstructor
public class TenantOrderController {

    private final OrderService orderService;

    @Operation(summary = "주문 목록(날짜별 페이징)",
            description = "date(yyyy-MM-dd, 기본 오늘)의 주문을 page 단위로. status=ALL 또는 WAITING,RECEIVED,… (콤마 다중)")
    @GetMapping
    public ResponseEntity<OrderPage> list(@AuthenticationPrincipal AuthPrincipal p,
                                          @RequestParam(defaultValue = "ALL") String status,
                                          @RequestParam(required = false)
                                          @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.listByDate(tenantId(p), status, date, page, size));
    }

    @Operation(summary = "진행 중 주문(테이블 현황판)", description = "종료/취소/결제완료 제외한 활성 주문만.")
    @GetMapping("/active")
    public ResponseEntity<List<OrderSummary>> active(@AuthenticationPrincipal AuthPrincipal p) {
        return ResponseEntity.ok(orderService.listActive(tenantId(p)));
    }

    @Operation(summary = "주문 상세")
    @GetMapping("/{id}")
    public ResponseEntity<OrderDetail> get(@AuthenticationPrincipal AuthPrincipal p, @PathVariable Long id) {
        return ResponseEntity.ok(orderService.get(tenantId(p), id));
    }

    @Operation(summary = "주문 등록", description = "직원 직접 등록용(손님 QR 주문도 같은 서비스를 쓴다).")
    @PostMapping
    public ResponseEntity<OrderDetail> create(@AuthenticationPrincipal AuthPrincipal p,
                                              @Valid @RequestBody OrderCreateRequest req) {
        return ResponseEntity.ok(orderService.create(tenantId(p), req));
    }

    @Operation(summary = "주문 상태 변경", description = "허용된 전이만 가능(예: COOKING→READY).")
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDetail> changeStatus(@AuthenticationPrincipal AuthPrincipal p,
                                                    @PathVariable Long id,
                                                    @Valid @RequestBody StatusChangeRequest req) {
        return ResponseEntity.ok(orderService.changeStatus(tenantId(p), id, req.status()));
    }

    private Long tenantId(AuthPrincipal p) {
        if (p == null || p.isAdmin() || !p.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
        return p.tenantId();
    }
}
