package com.saas.admin.publicshop;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.order.OrderService;
import com.saas.admin.order.dto.OrderDtos.OrderCreateRequest;
import com.saas.admin.order.dto.OrderDtos.OrderDetail;
import com.saas.admin.order.dto.OrderDtos.OrderLine;
import com.saas.admin.order.dto.OrderDtos.OrderSummary;
import com.saas.admin.publicshop.PublicShopDtos.*;
import com.saas.admin.tenant.TenantBranchService;
import com.saas.admin.tenant.domain.BranchTable;
import com.saas.admin.tenant.domain.Tenant;
import com.saas.admin.tenant.home.TenantHomeDtos.HomeView;
import com.saas.admin.tenant.home.TenantHomeService;
import com.saas.admin.tenant.menu.TenantMenuService;
import com.saas.admin.tenant.menu.dto.MenuDtos.MenuResponse;
import com.saas.admin.tenant.repository.BranchTableRepository;
import com.saas.admin.tenant.repository.TenantBranchRepository;
import com.saas.admin.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 손님(무인증) 테이블 주문. QR 의 업체코드/테이블코드로 가게·테이블을 확인하고,
 * 메뉴판을 보여주고, 주문을 접수한다. 인증이 없으므로 손님이 건드릴 수 있는 것은
 * "이 테이블에서의 주문 생성"뿐이다(가게·테이블은 URL 로만 지정, 그 외 수정 불가).
 */
@Service
@RequiredArgsConstructor
public class PublicShopService {

    private final TenantRepository tenantRepository;
    private final TenantBranchRepository branchRepository;
    private final BranchTableRepository tableRepository;
    private final TenantBranchService branchService;
    private final TenantMenuService menuService;
    private final TenantHomeService homeService;
    private final OrderService orderService;

    /** 가게 + 테이블 정보(화면 헤더용). */
    @Transactional(readOnly = true)
    public ShopTableView table(String tenantCode, String tableCode) {
        Tenant tenant = requireTenant(tenantCode);
        BranchTable t = requireTableOf(tenant.getId(), tableCode);
        String label = (t.getLabel() == null || t.getLabel().isBlank()) ? "테이블" : t.getLabel();
        return new ShopTableView(tenant.getName(), tenant.getCode(), t.getId(), t.getCode(), label, t.getSeats());
    }

    /** 가게 메인 페이지 콘텐츠(손님용). 미표시면 published=false 로 가게명만 온다. */
    @Transactional(readOnly = true)
    public HomeView home(String tenantCode) {
        Tenant tenant = requireTenant(tenantCode);
        return homeService.publicView(tenant.getId(), tenant.getName());
    }

    /** 이 가게(기본 지점)의 메뉴판. */
    @Transactional(readOnly = true)
    public MenuResponse menu(String tenantCode) {
        Tenant tenant = requireTenant(tenantCode);
        return menuService.getMenu(tenant.getId(), branchService.defaultBranchId(tenant.getId()));
    }

    /** 이 테이블의 진행 중 주문(종료 전) — QR 메뉴판의 '내 주문 내역'. */
    @Transactional(readOnly = true)
    public List<OrderSummary> tableOrders(String tenantCode, String tableCode) {
        Tenant tenant = requireTenant(tenantCode);
        BranchTable t = requireTableOf(tenant.getId(), tableCode);
        return orderService.tableActiveOrders(tenant.getId(), t.getId());
    }

    /** 주문 id 목록으로 조회(포장 등, 이 기기에서 넣은 주문). 다른 가게 주문은 걸러진다. */
    @Transactional(readOnly = true)
    public List<OrderSummary> ordersByIds(String tenantCode, List<Long> ids) {
        Tenant tenant = requireTenant(tenantCode);
        return orderService.ordersByIds(tenant.getId(), ids);
    }

    /** 포장 QR 진입 — 가게명 + 지금 포장주문을 받는지. false 면 손님 화면에 '정지'를 띄운다. */
    @Transactional
    public ShopTakeoutView takeout(String tenantCode) {
        Tenant tenant = requireTenant(tenantCode);
        return new ShopTakeoutView(tenant.getName(), tenant.getCode(),
                branchService.takeoutAvailable(tenant.getId()));
    }

    /** 손님 포장 주문 접수 — 테이블 없이 포장으로. 포장주문이 꺼져 있으면 거부(TAKEOUT_STOPPED). */
    @Transactional
    public OrderPlaced placeTakeoutOrder(String tenantCode, PlaceOrderRequest req) {
        Tenant tenant = requireTenant(tenantCode);
        if (!branchService.takeoutAvailable(tenant.getId())) {
            throw new ApiException(ErrorCode.TAKEOUT_STOPPED);
        }
        var lines = req.items().stream()
                .map(l -> new OrderLine(l.menuItemId(), l.menuName(),
                        Math.max(0, l.unitPrice()), Math.max(1, l.quantity()), l.optionsText()))
                .toList();
        OrderCreateRequest create = new OrderCreateRequest(
                null, "포장", "TAKEOUT", "TAKEOUT_QR", req.memo(), req.paymentMethod(), req.paymentKey(), lines);
        OrderDetail d = orderService.create(tenant.getId(), create);
        return new OrderPlaced(d.orderId(), d.orderNo(), d.totalAmount(), d.status(), d.paid(), d.paymentMethod());
    }

    /** 손님 주문 접수 — 테이블은 URL 로만 지정되고, 유형/경로는 서버가 강제한다(DINE_IN / TABLE_QR). */
    @Transactional
    public OrderPlaced placeOrder(String tenantCode, String tableCode, PlaceOrderRequest req) {
        Tenant tenant = requireTenant(tenantCode);
        BranchTable t = requireTableOf(tenant.getId(), tableCode);
        String label = (t.getLabel() == null || t.getLabel().isBlank()) ? "테이블" : t.getLabel();

        var lines = req.items().stream()
                .map(l -> new OrderLine(l.menuItemId(), l.menuName(),
                        Math.max(0, l.unitPrice()), Math.max(1, l.quantity()), l.optionsText()))
                .toList();

        OrderCreateRequest create = new OrderCreateRequest(
                t.getId(), label, "DINE_IN", "TABLE_QR", req.memo(), req.paymentMethod(), req.paymentKey(), lines);
        OrderDetail d = orderService.create(tenant.getId(), create);
        return new OrderPlaced(d.orderId(), d.orderNo(), d.totalAmount(), d.status(), d.paid(), d.paymentMethod());
    }

    // ===== 내부 =====

    private Tenant requireTenant(String tenantCode) {
        return tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND));
    }

    /** 테이블 코드로 찾고, 정말 이 가게 소속인지 확인한다. */
    private BranchTable requireTableOf(Long tenantId, String tableCode) {
        BranchTable t = tableRepository.findByCode(tableCode)
                .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND, "테이블을 찾을 수 없습니다."));
        boolean owned = branchRepository.findByTenantIdAndDeletedOrderByBranchNoAsc(tenantId, "N").stream()
                .anyMatch(b -> b.getId().equals(t.getBranchId()));
        if (!owned) {
            throw new ApiException(ErrorCode.BRANCH_NOT_FOUND, "테이블을 찾을 수 없습니다.");
        }
        return t;
    }
}
