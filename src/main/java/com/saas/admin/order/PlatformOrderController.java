package com.saas.admin.order;

import com.saas.admin.order.dto.OrderDtos.OrderDetail;
import com.saas.admin.order.dto.OrderDtos.OrderPage;
import com.saas.admin.order.dto.OrderDtos.OrderSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 플랫폼 관리자 — 업체별 주문 조회(읽기 전용). 업체 관리 화면에서 특정 업체의 주문을 확인한다.
 * 상태 변경·취소는 업체(사장님) 콘솔에서 한다. 여기선 보기만.
 */
@Tag(name = "21. 업체 주문 조회(관리자)", description = "플랫폼 관리자가 업체별 주문을 확인한다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/tenants/{tenantId}/orders")
@RequiredArgsConstructor
public class PlatformOrderController {

    private final OrderService orderService;

    @Operation(summary = "업체 주문 목록(날짜별 페이징)",
            description = "date(yyyy-MM-dd, 기본 오늘)의 주문을 page 단위로. status=RECEIVED,COOKING … CSV 필터, 비우면 전체.")
    @GetMapping
    public ResponseEntity<OrderPage> list(@PathVariable Long tenantId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false)
                                          @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.listByDate(tenantId, status, date, page, size));
    }

    @Operation(summary = "업체 주문 상세")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetail> get(@PathVariable Long tenantId, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.get(tenantId, orderId));
    }
}
