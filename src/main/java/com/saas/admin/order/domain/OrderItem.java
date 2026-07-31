package com.saas.admin.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문 항목(메뉴 1줄). 메뉴명·가격은 주문 시점 스냅샷으로 저장(메뉴가 바뀌어도 주문 내역은 고정). */
@Entity
@Table(name = "orders_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 참조 메뉴(스냅샷이 진실이라 nullable). */
    @Column(name = "menu_item_id")
    private Long menuItemId;

    @Column(name = "menu_name", nullable = false, length = 100)
    private String menuName;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** 옵션 요약 스냅샷(예: "소스: 매운맛, 사이즈: 곱빼기"). */
    @Column(name = "options_text", length = 300)
    private String optionsText;

    public static OrderItem of(Long menuItemId, String menuName, int unitPrice, int quantity, String optionsText) {
        OrderItem it = new OrderItem();
        it.menuItemId = menuItemId;
        it.menuName = menuName;
        it.unitPrice = unitPrice;
        it.quantity = Math.max(1, quantity);
        it.optionsText = (optionsText == null || optionsText.isBlank()) ? null : optionsText.trim();
        return it;
    }

    public void assignOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public int lineAmount() {
        return unitPrice * quantity;
    }
}
