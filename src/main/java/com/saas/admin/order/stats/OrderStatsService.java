package com.saas.admin.order.stats;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.order.domain.Order;
import com.saas.admin.order.domain.OrderItem;
import com.saas.admin.order.domain.OrderStatus;
import com.saas.admin.order.repository.OrderItemRepository;
import com.saas.admin.order.repository.OrderRepository;
import com.saas.admin.order.stats.StatsDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 매출 통계 — 결제된(paid='Y', 취소 제외) 주문을 기간별로 집계한다.
 * 관리자(업체별)·업체(자기 가게) 콘솔이 같은 계산을 공유한다.
 */
@Service
@RequiredArgsConstructor
public class OrderStatsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;

    private static final int TOP_MENU_LIMIT = 10;
    private static final Map<String, String> PAY_LABEL = Map.of(
            "CARD", "카드", "KAKAO_PAY", "카카오페이", "NAVER_PAY", "네이버페이",
            "TOSS_PAY", "토스페이", "TRANSFER", "계좌이체", "CASH", "현금");

    /** 기간 매출 통계. from/to 는 날짜(포함). null 이면 오늘로부터 한 달 전 ~ 오늘. */
    @Transactional(readOnly = true)
    public OrderStats stats(Long tenantId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusMonths(1);
        if (start.isAfter(end)) {
            LocalDate tmp = start; start = end; end = tmp;
        }
        LocalDateTime f = start.atStartOfDay();
        LocalDateTime t = end.plusDays(1).atStartOfDay();

        // 결제된 주문 = 결제 플래그(선불 모델) 또는 레거시 status=PAID. 취소는 제외.
        List<Order> orders = orderRepository.findByTenantIdAndCreatedAtBetweenOrderByCreatedAtAsc(tenantId, f, t).stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED
                        && (o.isPaid() || o.getStatus() == OrderStatus.PAID))
                .toList();

        long total = orders.stream().mapToLong(Order::getTotalAmount).sum();
        long count = orders.size();
        long avg = count > 0 ? Math.round((double) total / count) : 0;

        // 일별 집계.
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>(); // [amount, count]
        for (Order o : orders) {
            LocalDate d = o.getCreatedAt().toLocalDate();
            long[] v = byDay.computeIfAbsent(d, k -> new long[2]);
            v[0] += o.getTotalAmount();
            v[1] += 1;
        }
        List<DailyPoint> daily = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            long[] v = byDay.getOrDefault(d, new long[2]);
            daily.add(new DailyPoint(d.toString(), v[0], v[1]));
        }

        // 결제수단별.
        Map<String, long[]> byMethod = new LinkedHashMap<>();
        for (Order o : orders) {
            String m = o.getPaymentMethod() == null ? "기타" : o.getPaymentMethod();
            long[] v = byMethod.computeIfAbsent(m, k -> new long[2]);
            v[0] += o.getTotalAmount();
            v[1] += 1;
        }
        List<MethodStat> methods = byMethod.entrySet().stream()
                .map(e -> new MethodStat(e.getKey(), PAY_LABEL.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(MethodStat::amount).reversed())
                .toList();

        // 인기 메뉴 TOP — 결제된 주문의 항목만.
        List<MenuStat> topMenus = List.of();
        if (!orders.isEmpty()) {
            List<Long> ids = orders.stream().map(Order::getId).toList();
            Map<String, long[]> byMenu = new LinkedHashMap<>(); // [qty, amount]
            for (OrderItem it : itemRepository.findByOrderIdInOrderByIdAsc(ids)) {
                long[] v = byMenu.computeIfAbsent(it.getMenuName(), k -> new long[2]);
                v[0] += it.getQuantity();
                v[1] += (long) it.getUnitPrice() * it.getQuantity();
            }
            topMenus = byMenu.entrySet().stream()
                    .map(e -> new MenuStat(e.getKey(), e.getValue()[0], e.getValue()[1]))
                    .sorted(Comparator.comparingLong(MenuStat::amount).reversed())
                    .limit(TOP_MENU_LIMIT)
                    .toList();
        }

        // 결제 목록(최신순).
        List<PaymentRow> payments = orders.stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .map(o -> new PaymentRow(
                        o.getId(),
                        o.getOrderNo(),
                        o.getCreatedAt().format(AT_FMT),
                        o.getTableLabel() != null ? o.getTableLabel()
                                : ("TAKEOUT".equals(o.getOrderType()) ? "포장" : "-"),
                        o.getTotalAmount(),
                        o.getPaymentMethod() == null ? "기타" : PAY_LABEL.getOrDefault(o.getPaymentMethod(), o.getPaymentMethod()),
                        o.getStatus().name()))
                .toList();

        return new OrderStats(start.toString(), end.toString(), total, count, avg, daily, methods, topMenus, payments);
    }

    /** 결제 취소(환불) — 그 업체의 주문인지 확인 후 취소 처리한다. 취소되면 매출/목록에서 빠진다. */
    @Transactional
    public void cancelPayment(Long tenantId, Long orderId) {
        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        if (!o.belongsTo(tenantId)) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND);
        }
        o.refund();
    }

    private static final java.time.format.DateTimeFormatter AT_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
}
