package com.saas.admin.order.domain;

import java.util.Map;
import java.util.Set;

/**
 * 주문 상태. 공통코드(ORDER_STATUS)와 같은 문자열을 쓴다(표시는 공통코드, 전이 검증은 여기).
 * 전이 규칙을 간단히 이 안에 둔다 — 아무 값으로나 못 바꾸게.
 */
public enum OrderStatus {

    WAITING("주문 대기"),
    RECEIVED("주문 접수"),
    COOKING("조리 중"),
    READY("조리 완료"),
    SERVING("서빙 중"),
    SERVED("서빙 완료"),
    PAYMENT_WAIT("결제 대기"),
    PARTIALLY_PAID("부분 결제"),
    PAID("결제 완료"),
    CANCEL_REQUESTED("취소 요청"),
    CANCELLED("주문 취소"),
    CLOSED("주문 종료");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    // 허용 전이. 여기 없는 전이는 막는다.
    // 선불(메뉴판 결제) 모델: 결제 완료된 주문이 RECEIVED(접수)로 생성되어 주방 흐름을 탄다.
    // 결제는 주문 생성 전에 끝나므로 상태 흐름에 결제 단계가 없다. 서빙 완료 후 '완료'로 닫는다.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.ofEntries(
            Map.entry(WAITING, Set.of(RECEIVED, CANCELLED)),
            Map.entry(RECEIVED, Set.of(COOKING, CANCELLED)),
            Map.entry(COOKING, Set.of(READY, CANCELLED)),
            Map.entry(READY, Set.of(SERVING, SERVED)),
            Map.entry(SERVING, Set.of(SERVED)),
            Map.entry(SERVED, Set.of(CLOSED)),
            Map.entry(CANCEL_REQUESTED, Set.of(CANCELLED, RECEIVED)),
            Map.entry(CANCELLED, Set.of()),
            Map.entry(CLOSED, Set.of()));

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
