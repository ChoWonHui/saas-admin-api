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
import com.saas.admin.tenant.domain.TenantStatus;
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
    private final com.saas.admin.tenant.repository.TenantPlanRepository tenantPlanRepository;
    private final TenantBranchRepository branchRepository;
    private final BranchTableRepository tableRepository;
    private final TenantBranchService branchService;
    private final TenantMenuService menuService;
    private final TenantHomeService homeService;
    private final OrderService orderService;
    /** 은행 표시명을 공통코드(BANK_CD)에서 찾는다. */
    private final com.saas.admin.code.repository.CommonCodeRepository codeRepository;

    /**
     * 이 업체가 손님 주문을 받을 수 있는가.
     *
     * 요금제(tenant_plan.allow_order)가 정한다. FREE 는 메뉴판만 제공하므로 false 다.
     *
     * 요금제가 비어 있으면 <b>받는다</b>. 요금제는 나중에 붙은 개념이라 예전 업체는 값이 없는데,
     * 여기서 막으면 잘 쓰고 있던 가게의 주문이 어느 날 조용히 끊긴다.
     * "주문을 막는다" 는 판단은 FREE 라고 명시된 경우에만 내린다.
     */
    private boolean orderAllowed(Tenant tenant) {
        if (tenant.getPlanId() == null) return true;
        return tenantPlanRepository.findById(tenant.getPlanId())
                .map(com.saas.admin.tenant.domain.TenantPlan::isAllowOrder)
                .orElse(true);
    }

    /** 공통코드 BANK_CD 그룹의 코드 그룹명. */
    private static final String BANK_GROUP = "BANK_CD";

    /**
     * 이 가게의 입금 계좌 안내.
     * <p>
     * 주문을 받는 가게(BASIC 이상)는 결제창에서 '계좌이체'를 고를 때 쓰고,
     * 주문이 잠긴 가게(FREE)는 메뉴를 눌렀을 때 같은 안내를 보여준다. 두 곳이 같은 값을 쓴다.
     * <p>
     * 은행명은 코드가 아니라 공통코드(BANK_CD)의 이름으로 바꿔 보낸다 — 손님에게 '0' 을 보여줄 수는 없다.
     * 은행·계좌번호 중 하나라도 비면 null 이라, 화면은 안내 자체를 띄우지 않는다.
     */
    private BankAccountView accountViewFor(Tenant tenant) {
        String bankName = tenant.getBankCode() == null ? null
                : codeRepository.findByGroupGroupCodeAndCode(BANK_GROUP, tenant.getBankCode())
                        .map(com.saas.admin.code.domain.CommonCode::getName)
                        .orElse(tenant.getBankCode());
        String holder = (tenant.getAccountHolder() == null || tenant.getAccountHolder().isBlank())
                ? tenant.getName()          // 예금주를 비워 두면 업체명으로 안내한다
                : tenant.getAccountHolder();
        BankAccountView view = new BankAccountView(bankName, tenant.getAccountNo(), holder);
        return view.usable() ? view : null;
    }

    private void requireOrderAllowed(Tenant tenant) {
        if (!orderAllowed(tenant)) {
            throw new ApiException(ErrorCode.ORDER_NOT_ALLOWED);
        }
    }

    /**
     * 결제 화면 푸터에 넣을 사업자 정보. tenant 에 저장된 값을 그대로 담는다(요금제·발행 무관).
     * 우편번호·주소·상세주소를 한 줄로 합치고, 빈 값은 null 로 내려 화면이 그 줄을 생략하게 한다.
     */
    private BusinessInfoView businessInfoFor(Tenant tenant) {
        return new BusinessInfoView(
                tenant.getName(),
                blankToNull(tenant.getOwnerName()),
                blankToNull(tenant.getBusinessNo()),
                blankToNull(tenant.getMailOrderSalesNo()),
                blankToNull(tenant.getContactPhone()),
                blankToNull(tenant.getContactEmail()),
                composeAddress(tenant));
    }

    /** 우편번호 + 주소 + 상세주소를 한 줄로. 전부 비면 null. */
    private String composeAddress(Tenant tenant) {
        StringBuilder sb = new StringBuilder();
        if (tenant.getPostalCode() != null && !tenant.getPostalCode().isBlank()) {
            sb.append('(').append(tenant.getPostalCode().trim()).append(") ");
        }
        if (tenant.getAddress() != null && !tenant.getAddress().isBlank()) {
            sb.append(tenant.getAddress().trim());
        }
        if (tenant.getAddressDetail() != null && !tenant.getAddressDetail().isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(tenant.getAddressDetail().trim());
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? null : out;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 가게 + 테이블 정보(화면 헤더용). */
    @Transactional(readOnly = true)
    public ShopTableView table(String tenantCode, String tableCode) {
        Tenant tenant = requireTenant(tenantCode);
        BranchTable t = requireTableOf(tenant.getId(), tableCode);
        String label = (t.getLabel() == null || t.getLabel().isBlank()) ? "테이블" : t.getLabel();
        return new ShopTableView(tenant.getName(), tenant.getCode(), t.getId(), t.getCode(), label, t.getSeats(),
                t.activeOrTrue(), orderAllowed(tenant), accountViewFor(tenant), businessInfoFor(tenant));
    }

    /** 가게 메인 페이지 콘텐츠(손님용). 미표시면 published=false 로 가게명만 온다. */
    @Transactional(readOnly = true)
    public HomeView home(String tenantCode) {
        Tenant tenant = requireTenant(tenantCode);
        return homeService.publicView(tenant.getId(), tenant.getName());
    }

    /** 이 가게(기본 지점)의 메뉴판. 아직 지점이 없는 새 가게는 빈 메뉴로 응답한다(쓰기 금지 경로). */
    @Transactional(readOnly = true)
    public MenuResponse menu(String tenantCode) {
        Tenant tenant = requireTenant(tenantCode);
        return branchService.findDefaultBranchId(tenant.getId())
                .map(branchId -> menuService.getMenu(tenant.getId(), branchId))
                .orElseGet(() -> new MenuResponse(List.of()));
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
                branchService.takeoutAvailable(tenant.getId()), orderAllowed(tenant),
                accountViewFor(tenant), businessInfoFor(tenant));
    }

    /** 손님 포장 주문 접수 — 테이블 없이 포장으로. 포장주문이 꺼져 있으면 거부(TAKEOUT_STOPPED). */
    @Transactional
    public OrderPlaced placeTakeoutOrder(String tenantCode, PlaceOrderRequest req) {
        Tenant tenant = requireTenant(tenantCode);
        requireOrderAllowed(tenant);
        if (!branchService.takeoutAvailable(tenant.getId())) {
            throw new ApiException(ErrorCode.TAKEOUT_STOPPED);
        }
        var lines = req.items().stream()
                .map(l -> new OrderLine(l.menuItemId(), l.menuName(),
                        Math.max(0, l.unitPrice()), Math.max(1, l.quantity()), l.optionsText()))
                .toList();
        OrderCreateRequest create = new OrderCreateRequest(
                null, "포장", "TAKEOUT", "TAKEOUT_QR", req.memo(), req.paymentMethod(), req.paymentKey(), null, lines);
        OrderDetail d = orderService.create(tenant.getId(), create);
        return new OrderPlaced(d.orderId(), d.orderNo(), d.totalAmount(), d.status(), d.paid(), d.paymentMethod());
    }

    /** 택배 진입 — 가게명 + 지금 택배주문을 받는지. false 면 손님 화면에 '택배 미제공'을 띄운다. */
    @Transactional
    public ShopParcelView parcel(String tenantCode) {
        Tenant tenant = requireTenant(tenantCode);
        return new ShopParcelView(tenant.getName(), tenant.getCode(),
                branchService.parcelEnabled(tenant.getId()), orderAllowed(tenant),
                accountViewFor(tenant), businessInfoFor(tenant));
    }

    /**
     * 손님 택배 주문 접수 — 테이블 없이 택배(배송지 입력)로.
     * 택배 받기가 꺼져 있으면 거부(PARCEL_STOPPED). 유형/경로는 서버가 강제한다(PARCEL / PARCEL_WEB).
     * 비로그인 경로다 — 손님이 보내는 것은 배송지와 주문 항목뿐이고, 가게는 URL 로만 지정된다.
     */
    @Transactional
    public OrderPlaced placeParcelOrder(String tenantCode, PlaceParcelOrderRequest req) {
        Tenant tenant = requireTenant(tenantCode);
        requireOrderAllowed(tenant);
        if (!branchService.parcelEnabled(tenant.getId())) {
            throw new ApiException(ErrorCode.PARCEL_STOPPED);
        }
        var lines = req.items().stream()
                .map(l -> new OrderLine(l.menuItemId(), l.menuName(),
                        Math.max(0, l.unitPrice()), Math.max(1, l.quantity()), l.optionsText()))
                .toList();
        OrderCreateRequest create = new OrderCreateRequest(
                null, "택배", "PARCEL", "PARCEL_WEB", req.memo(), req.paymentMethod(), req.paymentKey(),
                req.shipping(), lines);
        OrderDetail d = orderService.create(tenant.getId(), create);
        return new OrderPlaced(d.orderId(), d.orderNo(), d.totalAmount(), d.status(), d.paid(), d.paymentMethod());
    }

    /** 손님 주문 접수 — 테이블은 URL 로만 지정되고, 유형/경로는 서버가 강제한다(DINE_IN / TABLE_QR). */
    @Transactional
    public OrderPlaced placeOrder(String tenantCode, String tableCode, PlaceOrderRequest req) {
        Tenant tenant = requireTenant(tenantCode);
        requireOrderAllowed(tenant);
        BranchTable t = requireTableOf(tenant.getId(), tableCode);
        if (!t.activeOrTrue()) {
            throw new ApiException(ErrorCode.TABLE_DISABLED);
        }
        String label = (t.getLabel() == null || t.getLabel().isBlank()) ? "테이블" : t.getLabel();

        var lines = req.items().stream()
                .map(l -> new OrderLine(l.menuItemId(), l.menuName(),
                        Math.max(0, l.unitPrice()), Math.max(1, l.quantity()), l.optionsText()))
                .toList();

        OrderCreateRequest create = new OrderCreateRequest(
                t.getId(), label, "DINE_IN", "TABLE_QR", req.memo(), req.paymentMethod(), req.paymentKey(), null, lines);
        OrderDetail d = orderService.create(tenant.getId(), create);
        return new OrderPlaced(d.orderId(), d.orderNo(), d.totalAmount(), d.status(), d.paid(), d.paymentMethod());
    }

    // ===== 내부 =====

    /**
     * 손님에게 공개되는 가게는 '운영중(ACTIVE)'만이다.
     * 대기(PENDING)·중지(SUSPENDED)·폐업(CLOSED)이거나 코드가 없으면 '없는 가게'로 취급(404) →
     * 손님 화면은 이걸 받아 EXPRISM 회사 소개 페이지(root)로 보낸다.
     */
    private Tenant requireTenant(String tenantCode) {
        Tenant tenant = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new ApiException(ErrorCode.TENANT_NOT_FOUND);
        }
        return tenant;
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
