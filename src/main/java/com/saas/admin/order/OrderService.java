package com.saas.admin.order;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.order.domain.Order;
import com.saas.admin.order.domain.OrderItem;
import com.saas.admin.order.domain.OrderStatus;
import com.saas.admin.order.dto.OrderDtos.*;
import com.saas.admin.order.repository.OrderItemRepository;
import com.saas.admin.order.repository.OrderRepository;
import com.saas.admin.notify.TenantNotifySocketHandler;
import com.saas.admin.payment.PaymentGateway;
import com.saas.admin.tenant.TenantBranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 주문 — 목록/상세/생성/상태변경. 상태 전이 검증은 Order(OrderStatus)가 담당. */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final TenantBranchService branchService;
    private final PaymentGateway paymentGateway;
    private final TenantNotifySocketHandler tenantNotifyHandler;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Transactional(readOnly = true)
    public List<OrderSummary> list(Long tenantId, String statusCsv) {
        List<Order> orders;
        List<OrderStatus> statuses = parseStatuses(statusCsv);
        if (statuses.isEmpty()) {
            orders = orderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        } else {
            orders = orderRepository.findByTenantIdAndStatusInOrderByCreatedAtDesc(tenantId, statuses);
        }
        // 항목을 한 번에 조회해 주문별로 묶는다(N+1 회피). 목록 카드에 그대로 보여준다.
        Map<Long, List<OrderItem>> byOrder = orders.isEmpty() ? Map.of()
                : itemRepository.findByOrderIdInOrderByIdAsc(orders.stream().map(Order::getId).toList()).stream()
                    .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream()
                .map(o -> OrderSummary.of(o, byOrder.getOrDefault(o.getId(), List.of())))
                .toList();
    }

    /** 진행 중(활성) 주문만 — 테이블 현황판용. 종료/취소/결제완료 제외라 건수가 유계다. */
    @Transactional(readOnly = true)
    public List<OrderSummary> listActive(Long tenantId) {
        return withItems(orderRepository.findByTenantIdAndStatusNotInOrderByCreatedAtAsc(
                tenantId, List.of(OrderStatus.CLOSED, OrderStatus.CANCELLED, OrderStatus.PAID)));
    }

    /**
     * 날짜별 페이징 주문 목록. 전체를 한 번에 끌어오지 않고 하루치를 페이지 단위로 가져온다.
     * date 가 null 이면 오늘. statusCsv=ALL/빈값이면 상태 무관.
     */
    @Transactional(readOnly = true)
    public OrderPage listByDate(Long tenantId, String statusCsv, LocalDate date, int page, int size) {
        LocalDate day = date != null ? date : LocalDate.now();
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        int pageSize = size <= 0 ? 20 : Math.min(size, 200);
        Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize, Sort.by("createdAt").descending());

        List<OrderStatus> statuses = parseStatuses(statusCsv);
        Page<Order> pageOrders = statuses.isEmpty()
                ? orderRepository.findByTenantIdAndCreatedAtBetween(tenantId, from, to, pageable)
                : orderRepository.findByTenantIdAndStatusInAndCreatedAtBetween(tenantId, statuses, from, to, pageable);

        Map<Long, List<OrderItem>> byOrder = pageOrders.isEmpty() ? Map.of()
                : itemRepository.findByOrderIdInOrderByIdAsc(pageOrders.getContent().stream().map(Order::getId).toList()).stream()
                    .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return OrderPage.of(
                pageOrders.map(o -> OrderSummary.of(o, byOrder.getOrDefault(o.getId(), List.of()))),
                day);
    }

    /** 손님용 — 이 테이블의 '진행 중' 주문(종료/취소 전). QR 메뉴판의 '내 주문 내역'. */
    @Transactional(readOnly = true)
    public List<OrderSummary> tableActiveOrders(Long tenantId, Long tableId) {
        // 진행 중 = 종료/취소/(레거시)결제완료 를 제외. 선불 모델은 RECEIVED~SERVED 가 착석 중.
        List<Order> orders = orderRepository.findByTenantIdAndTableIdAndStatusNotInOrderByCreatedAtAsc(
                tenantId, tableId, List.of(OrderStatus.CLOSED, OrderStatus.CANCELLED, OrderStatus.PAID));
        return withItems(orders);
    }

    /** 손님용 — 주문 id 목록으로 조회(포장 등 테이블이 없을 때, 이 기기에서 넣은 주문). */
    @Transactional(readOnly = true)
    public List<OrderSummary> ordersByIds(Long tenantId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Order> orders = orderRepository.findAllById(ids).stream()
                .filter(o -> o.belongsTo(tenantId))
                .sorted(java.util.Comparator.comparing(Order::getCreatedAt))
                .toList();
        return withItems(orders);
    }

    /** 주문 목록에 항목을 한 번에 붙여 OrderSummary 로. */
    private List<OrderSummary> withItems(List<Order> orders) {
        Map<Long, List<OrderItem>> byOrder = orders.isEmpty() ? Map.of()
                : itemRepository.findByOrderIdInOrderByIdAsc(orders.stream().map(Order::getId).toList()).stream()
                    .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream()
                .map(o -> OrderSummary.of(o, byOrder.getOrDefault(o.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetail get(Long tenantId, Long orderId) {
        Order o = requireOwned(tenantId, orderId);
        return OrderDetail.of(o, itemRepository.findByOrderIdOrderByIdAsc(orderId));
    }

    @Transactional
    public OrderDetail create(Long tenantId, OrderCreateRequest req) {
        Long branchId = branchService.defaultBranchId(tenantId);
        int total = req.items().stream().mapToInt(l -> Math.max(0, l.unitPrice()) * Math.max(1, l.quantity())).sum();

        // 선불 결제 — 게이트웨이 승인/검증을 먼저 받는다. 지금은 모의(항상 승인), 나중에 실제 PG 로 교체.
        PaymentGateway.PaymentResult pay = paymentGateway.confirm(
                new PaymentGateway.PaymentConfirm(tenantId, null, req.paymentMethod(), total, req.paymentKey()));
        if (!pay.approved()) {
            throw new ApiException(ErrorCode.PAYMENT_FAILED, pay.message());
        }

        // 결제 완료(paid)·접수(RECEIVED)로 생성 — 거래번호까지 기록.
        Order order = orderRepository.save(Order.createPrepaid(
                tenantId, branchId, req.tableId(), req.tableLabel(),
                nextOrderNo(tenantId), req.orderType(), req.channel(), total, req.memo(),
                pay.method(), pay.approvedAt(), pay.transactionId()));

        List<OrderItem> items = req.items().stream()
                .map(l -> {
                    OrderItem it = OrderItem.of(l.menuItemId(), l.menuName(), l.unitPrice(), l.quantity(), l.optionsText());
                    return it;
                })
                .toList();
        items.forEach(it -> { it.assignOrderId(order.getId()); itemRepository.save(it); });

        // 손님 결제 완료 → 커밋 후 사장님 주문관리 화면에 실시간 푸시(롤백 시 헛알림 방지).
        pushNewOrderAfterCommit(tenantId, order.getId(), req.tableLabel(), total, req.orderType());
        return OrderDetail.of(order, items);
    }

    private void pushNewOrderAfterCommit(Long tenantId, Long orderId, String tableLabel, int amount, String orderType) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tenantNotifyHandler.pushNewOrder(tenantId, orderId, tableLabel, amount, orderType);
                }
            });
        } else {
            tenantNotifyHandler.pushNewOrder(tenantId, orderId, tableLabel, amount, orderType);
        }
    }

    @Transactional
    public OrderDetail changeStatus(Long tenantId, Long orderId, String next) {
        Order o = requireOwned(tenantId, orderId);
        o.changeStatus(parseStatus(next));
        return OrderDetail.of(o, itemRepository.findByOrderIdOrderByIdAsc(orderId));
    }

    // ===== 내부 =====

    private Order requireOwned(Long tenantId, Long orderId) {
        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        if (!o.belongsTo(tenantId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "다른 가게의 주문입니다.");
        }
        return o;
    }

    private String nextOrderNo(Long tenantId) {
        LocalDate today = LocalDate.now();
        long todayCount = orderRepository.countByTenantIdAndCreatedAtBetween(
                tenantId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        return today.format(DAY) + "-" + String.format("%04d", todayCount + 1);
    }

    private OrderStatus parseStatus(String s) {
        try {
            return OrderStatus.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION, "알 수 없는 상태: " + s);
        }
    }

    private List<OrderStatus> parseStatuses(String csv) {
        if (csv == null || csv.isBlank() || "ALL".equalsIgnoreCase(csv)) return List.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return OrderStatus.valueOf(s.toUpperCase()); } catch (Exception e) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
