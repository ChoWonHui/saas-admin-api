package com.saas.admin.order.stats;

import com.saas.admin.order.stats.StatsDtos.OrderStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 플랫폼 관리자 — 업체별 매출 통계(업체 콘솔과 동일 지표). 보안은 /api/platform-admin/** (PLATFORM_ADMIN). */
@Tag(name = "27. 업체 매출 통계(관리자)", description = "플랫폼 관리자가 업체별 결제 매출을 기간별로 집계해 본다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/tenants/{tenantId}/stats")
@RequiredArgsConstructor
public class PlatformStatsController {

    private final OrderStatsService statsService;

    @Operation(summary = "업체 매출 통계", description = "from/to(yyyy-MM-dd, 생략 시 최근 30일)")
    @GetMapping
    public ResponseEntity<OrderStats> stats(@PathVariable Long tenantId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(statsService.stats(tenantId, from, to));
    }

    @Operation(summary = "업체 결제 취소(환불)", description = "결제 목록의 주문을 취소한다. 취소되면 매출·목록에서 빠진다.")
    @org.springframework.web.bind.annotation.PostMapping("/payments/{orderId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long tenantId,
                                       @org.springframework.web.bind.annotation.PathVariable Long orderId) {
        statsService.cancelPayment(tenantId, orderId);
        return ResponseEntity.noContent().build();
    }
}
