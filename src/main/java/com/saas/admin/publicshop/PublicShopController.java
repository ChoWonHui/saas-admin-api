package com.saas.admin.publicshop;

import com.saas.admin.order.dto.OrderDtos.OrderSummary;
import com.saas.admin.publicshop.PublicShopDtos.*;
import com.saas.admin.tenant.home.TenantHomeDtos.HomeView;
import com.saas.admin.tenant.menu.dto.MenuDtos.MenuResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 손님(무인증) 테이블 주문 API. QR 링크 {@code /{업체코드}/{테이블코드}} 로 들어온
 * 손님용 화면(saas-client-web)이 호출한다. 인증 없이 열려 있으므로,
 * 가게·테이블은 URL 로만 지정되고 그 외에는 아무것도 바꿀 수 없다.
 */
@Tag(name = "20. 손님 주문(무인증)", description = "QR 로 들어온 손님이 메뉴판을 보고 테이블 주문을 넣는다.")
@RestController
@RequestMapping("/api/public/shops/{tenantCode}")
@RequiredArgsConstructor
public class PublicShopController {

    private final PublicShopService service;

    @Operation(summary = "가게·테이블 확인", description = "QR 진입 시 가게 이름과 테이블을 확인한다.")
    @GetMapping("/tables/{tableCode}")
    public ResponseEntity<ShopTableView> table(@PathVariable String tenantCode,
                                               @PathVariable String tableCode) {
        return ResponseEntity.ok(service.table(tenantCode, tableCode));
    }

    @Operation(summary = "가게 메인 페이지", description = "손님 화면 상단에 보일 가게 소개/영업시간 등. 미표시면 published=false.")
    @GetMapping("/home")
    public ResponseEntity<HomeView> home(@PathVariable String tenantCode) {
        return ResponseEntity.ok(service.home(tenantCode));
    }

    @Operation(summary = "메뉴판 조회")
    @GetMapping("/menu")
    public ResponseEntity<MenuResponse> menu(@PathVariable String tenantCode) {
        return ResponseEntity.ok(service.menu(tenantCode));
    }

    @Operation(summary = "포장 주문 가능 여부", description = "포장 QR 진입 시. false 면 손님 화면에 '포장 주문 정지'를 띄운다.")
    @GetMapping("/takeout")
    public ResponseEntity<ShopTakeoutView> takeout(@PathVariable String tenantCode) {
        return ResponseEntity.ok(service.takeout(tenantCode));
    }

    @Operation(summary = "포장 주문 접수", description = "포장주문이 꺼져 있으면 409(TAKEOUT_STOPPED).")
    @PostMapping("/takeout/orders")
    public ResponseEntity<OrderPlaced> takeoutOrder(@PathVariable String tenantCode,
                                                    @Valid @RequestBody PlaceOrderRequest req) {
        return ResponseEntity.ok(service.placeTakeoutOrder(tenantCode, req));
    }

    @Operation(summary = "테이블 주문 접수")
    @PostMapping("/tables/{tableCode}/orders")
    public ResponseEntity<OrderPlaced> order(@PathVariable String tenantCode,
                                             @PathVariable String tableCode,
                                             @Valid @RequestBody PlaceOrderRequest req) {
        return ResponseEntity.ok(service.placeOrder(tenantCode, tableCode, req));
    }

    @Operation(summary = "이 테이블의 진행 중 주문", description = "종료 전 주문 목록. QR 메뉴판의 '내 주문 내역'.")
    @GetMapping("/tables/{tableCode}/orders")
    public ResponseEntity<List<OrderSummary>> tableOrders(@PathVariable String tenantCode,
                                                          @PathVariable String tableCode) {
        return ResponseEntity.ok(service.tableOrders(tenantCode, tableCode));
    }

    @Operation(summary = "주문 id 목록으로 조회", description = "포장 등 테이블이 없을 때, 이 기기에서 넣은 주문 상태 조회. ids=1,2,3")
    @GetMapping("/orders")
    public ResponseEntity<List<OrderSummary>> ordersByIds(@PathVariable String tenantCode,
                                                          @RequestParam(required = false) String ids) {
        return ResponseEntity.ok(service.ordersByIds(tenantCode, parseIds(ids)));
    }

    private List<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<Long> out = new java.util.ArrayList<>();
        for (String s : csv.split(",")) {
            try { out.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) { /* skip */ }
        }
        return out;
    }
}
