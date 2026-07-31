package com.saas.admin.order.dto;

import com.saas.admin.order.domain.Order;
import com.saas.admin.order.domain.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/** 주문 DTO. */
public final class OrderDtos {

    private OrderDtos() {
    }

    /** 날짜별 페이징 주문 목록 응답. content + 페이지 정보. */
    public record OrderPage(List<OrderSummary> content, String date, int page, int size,
                            long totalElements, int totalPages) {
        public static OrderPage of(org.springframework.data.domain.Page<OrderSummary> p, java.time.LocalDate date) {
            return new OrderPage(p.getContent(), date.toString(), p.getNumber(), p.getSize(),
                    p.getTotalElements(), p.getTotalPages());
        }
    }

    public record OrderItemView(Long menuItemId, String menuName, int unitPrice, int quantity,
                                String optionsText, int lineAmount) {
        public static OrderItemView of(OrderItem it) {
            return new OrderItemView(it.getMenuItemId(), it.getMenuName(), it.getUnitPrice(),
                    it.getQuantity(), it.getOptionsText(), it.lineAmount());
        }
    }

    public record OrderSummary(Long orderId, String orderNo, Long tableId, String tableLabel,
                               String status, String orderType, String channel, int totalAmount,
                               boolean paid, String paymentMethod, int itemCount, LocalDateTime createdAt,
                               List<OrderItemView> items) {
        public static OrderSummary of(Order o, List<OrderItem> items) {
            return new OrderSummary(o.getId(), o.getOrderNo(), o.getTableId(), o.getTableLabel(),
                    o.getStatus().name(), o.getOrderType(), o.getChannel(), o.getTotalAmount(),
                    o.isPaid(), o.getPaymentMethod(), items.size(), o.getCreatedAt(),
                    items.stream().map(OrderItemView::of).toList());
        }
    }

    public record OrderDetail(Long orderId, String orderNo, Long tableId, String tableLabel,
                              String status, String orderType, String channel, int totalAmount, String memo,
                              boolean paid, String paymentMethod, LocalDateTime paidAt,
                              LocalDateTime createdAt, LocalDateTime updatedAt, List<OrderItemView> items) {
        public static OrderDetail of(Order o, List<OrderItem> items) {
            return new OrderDetail(o.getId(), o.getOrderNo(), o.getTableId(), o.getTableLabel(),
                    o.getStatus().name(), o.getOrderType(), o.getChannel(), o.getTotalAmount(), o.getMemo(),
                    o.isPaid(), o.getPaymentMethod(), o.getPaidAt(),
                    o.getCreatedAt(), o.getUpdatedAt(), items.stream().map(OrderItemView::of).toList());
        }
    }

    @Schema(description = "주문 생성(손님 선불 결제 후). paymentMethod 가 있으면 결제완료·접수 상태로 만든다.")
    public record OrderCreateRequest(
            Long tableId,
            String tableLabel,
            String orderType,
            String channel,
            String memo,
            String paymentMethod,
            String paymentKey,
            @NotEmpty(message = "주문 항목은 최소 1개입니다.") @Valid List<OrderLine> items
    ) {
    }

    public record OrderLine(
            Long menuItemId,
            @NotBlank(message = "메뉴명은 필수입니다.") String menuName,
            int unitPrice,
            int quantity,
            String optionsText
    ) {
    }

    @Schema(description = "주문 상태 변경")
    public record StatusChangeRequest(@NotNull String status) {
    }
}
