package com.saas.admin.order.stats;

import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.order.stats.StatsDtos.OrderStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 사장님 콘솔 — 자기 가게 매출 통계(결제 완료 주문 기준). */
@Tag(name = "17. 매출 통계(업체)", description = "결제된 주문을 기간별로 집계한다(총매출·객단가·일별·결제수단·인기메뉴).")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tenant/stats")
@RequiredArgsConstructor
public class TenantStatsController {

    private final OrderStatsService statsService;

    @Operation(summary = "매출 통계", description = "from/to(yyyy-MM-dd, 생략 시 최근 30일)")
    @GetMapping
    public ResponseEntity<OrderStats> stats(@AuthenticationPrincipal AuthPrincipal p,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(statsService.stats(tenantId(p), from, to));
    }

    @Operation(summary = "결제 취소(환불)", description = "결제 목록의 주문을 취소한다. 취소되면 매출·목록에서 빠진다.")
    @org.springframework.web.bind.annotation.PostMapping("/payments/{orderId}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthPrincipal p,
                                       @org.springframework.web.bind.annotation.PathVariable Long orderId) {
        statsService.cancelPayment(tenantId(p), orderId);
        return ResponseEntity.noContent().build();
    }

    private Long tenantId(AuthPrincipal p) {
        if (p == null || p.isAdmin() || !p.hasTenantContext()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "업체 로그인이 필요합니다.");
        }
        return p.tenantId();
    }
}
