package com.saas.admin.order.domain;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 주문(헤더). 업체(tenant)·지점·테이블에 종속. 손님 QR 주문이 이걸 만들고,
 * 사장님 주문관리 화면이 상태를 바꾼다. 상세 항목은 {@link OrderItem}.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    /** 주문한 테이블(포장이면 null). */
    @Column(name = "table_id")
    private Long tableId;

    /** 테이블 이름 스냅샷(예: "3번"). */
    @Column(name = "table_label", length = 40)
    private String tableLabel;

    /** 사람이 읽는 주문번호(예: 20260724-0012). */
    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    /** 주문 유형(공통코드 ORDER_TYPE). MVP는 DINE_IN. */
    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    /** 접수 경로(공통코드 ORDER_CHANNEL). */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "memo", length = 300)
    private String memo;

    /** 선불 결제 완료 여부. 선불 모델에선 주문 생성 시 이미 결제가 끝나 true 로 만들어진다. */
    @Column(name = "paid", nullable = false, length = 1, columnDefinition = "CHAR(1) NOT NULL DEFAULT 'N'")
    private String paid;

    /** 결제 수단(공통코드 PAYMENT_METHOD). 예: CARD / KAKAO_PAY / NAVER_PAY. */
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    /** 결제 게이트웨이 거래번호(모의는 MOCK-…, 실제 PG는 승인번호/결제키). 환불·조회의 키. */
    @Column(name = "payment_txn_id", length = 60)
    private String paymentTxnId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Order create(Long tenantId, Long branchId, Long tableId, String tableLabel,
                               String orderNo, String orderType, String channel, int totalAmount, String memo) {
        Order o = new Order();
        o.tenantId = tenantId;
        o.branchId = branchId;
        o.tableId = tableId;
        o.tableLabel = tableLabel;
        o.orderNo = orderNo;
        o.status = OrderStatus.WAITING;
        o.orderType = (orderType == null || orderType.isBlank()) ? "DINE_IN" : orderType;
        o.channel = (channel == null || channel.isBlank()) ? "TABLE_QR" : channel;
        o.totalAmount = totalAmount;
        o.memo = (memo == null || memo.isBlank()) ? null : memo.trim();
        o.paid = "N";
        return o;
    }

    /**
     * 선불 주문 생성 — 손님이 메뉴판에서 결제를 마친 뒤 만들어진다.
     * 이미 결제가 끝났으므로 결제완료(paid) 상태로, 주방 흐름의 첫 단계인 RECEIVED(접수)로 바로 들어간다.
     */
    public static Order createPrepaid(Long tenantId, Long branchId, Long tableId, String tableLabel,
                                      String orderNo, String orderType, String channel, int totalAmount,
                                      String memo, String paymentMethod, LocalDateTime paidAt, String paymentTxnId) {
        Order o = create(tenantId, branchId, tableId, tableLabel, orderNo, orderType, channel, totalAmount, memo);
        o.status = OrderStatus.RECEIVED;
        o.paid = "Y";
        o.paymentMethod = (paymentMethod == null || paymentMethod.isBlank()) ? "CARD" : paymentMethod.trim();
        o.paidAt = paidAt;
        o.paymentTxnId = paymentTxnId;
        return o;
    }

    public boolean isPaid() {
        return "Y".equals(paid);
    }

    /** 상태 변경 — 허용된 전이만. 위반 시 예외. */
    public void changeStatus(OrderStatus next) {
        if (this.status == next) return;
        if (!this.status.canTransitionTo(next)) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    this.status.getDescription() + " → " + next.getDescription() + " 로는 바꿀 수 없습니다.");
        }
        this.status = next;
    }

    /** 결제 처리 — 계산 화면에서 테이블을 결제하면 호출. 종료/취소된 주문은 결제할 수 없다. */
    public void markPaid() {
        if (this.status == OrderStatus.CANCELLED || this.status == OrderStatus.CLOSED) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    this.status.getDescription() + " 주문은 결제할 수 없습니다.");
        }
        this.status = OrderStatus.PAID;
    }

    /** 결제 취소(환불) — 결제 목록에서 결제를 무른다. 진행 상태와 무관하게 취소로 만들고 결제 해제. */
    public void refund() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION, "이미 취소된 주문입니다.");
        }
        this.status = OrderStatus.CANCELLED;
        this.paid = "N";
    }

    public boolean belongsTo(Long tenantId) {
        return this.tenantId.equals(tenantId);
    }
}
