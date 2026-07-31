package com.saas.admin.payment;

import java.time.LocalDateTime;

/**
 * 결제 게이트웨이 — 손님이 메뉴판에서 결제한 뒤, 주문이 생성되기 직전에 승인/검증을 받는 지점.
 * <p>
 * <b>지금은 {@link MockPaymentGateway} 가 무조건 승인</b>한다(모의결제). 나중에 실제 PG
 * (토스페이먼츠·카카오페이·나이스페이 등)를 붙일 때는 이 인터페이스의 구현만 하나 추가하고
 * {@code payment.gateway} 설정값을 바꾸면 된다. 주문·주방·화면 등 나머지 선불 흐름은 그대로 동작한다.
 * <p>
 * 실제 PG 연동 시 흐름: 손님 앱이 PG 결제창에서 결제키(paymentKey)를 받아 주문 요청에 실어 보내면,
 * 서버가 이 {@code confirm} 에서 PG 승인 API 로 최종 검증·매입한다.
 */
public interface PaymentGateway {

    /** 결제 승인/검증. 승인되면 거래번호가 담긴 결과를 돌려준다. 실패면 approved=false. */
    PaymentResult confirm(PaymentConfirm req);

    /** 승인 요청 — 누구의(테넌트) 얼마짜리(amount) 결제를 어떤 수단(method)으로. paymentKey 는 실제 PG의 결제키(모의는 없어도 됨). */
    record PaymentConfirm(Long tenantId, String orderNo, String method, int amount, String paymentKey) {
    }

    /** 승인 결과 — 거래번호(transactionId)와 확정 수단·시각. */
    record PaymentResult(boolean approved, String transactionId, String method,
                         LocalDateTime approvedAt, String message) {

        public static PaymentResult approved(String transactionId, String method) {
            return new PaymentResult(true, transactionId, method, LocalDateTime.now(), "결제 승인");
        }

        public static PaymentResult declined(String message) {
            return new PaymentResult(false, null, null, null, message);
        }
    }
}
