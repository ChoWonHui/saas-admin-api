package com.saas.admin.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 모의 결제 게이트웨이 — 실제 청구 없이 항상 승인한다. 개발·데모·선불 흐름 검증용.
 * <p>
 * {@code payment.gateway=mock}(기본값)일 때 활성화된다. 실제 PG 구현을 추가하고
 * {@code payment.gateway=toss} 처럼 바꾸면 이 목은 비활성화되고 그 구현이 주입된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult confirm(PaymentConfirm req) {
        String method = (req.method() == null || req.method().isBlank()) ? "CARD" : req.method().trim();
        // 모의 거래번호. 실제 PG 라면 승인 응답의 거래키/승인번호가 들어갈 자리.
        String txn = "MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        log.info("[모의결제] 승인 tenant={} amount={} method={} txn={}", req.tenantId(), req.amount(), method, txn);
        return new PaymentResult(true, txn, method, LocalDateTime.now(), "모의 결제 승인");
    }
}
