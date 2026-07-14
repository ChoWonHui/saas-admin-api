package com.saas.admin.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 테넌트 구독.
 * active_marker 는 DB 의 STORED 생성 컬럼이라 매핑하지 않는다 —
 * "테넌트당 ACTIVE 구독 1건" 을 DB 가 물리적으로 강제한다. (설계안 §5.3)
 */
@Entity
@Table(name = "tenant_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private TenantSubscription(Long tenantId, Long planId) {
        this.tenantId = tenantId;
        this.planId = planId;
        this.status = SubscriptionStatus.ACTIVE;
        this.startedOn = LocalDate.now();
        this.autoRenew = true;
    }

    public static TenantSubscription start(Long tenantId, Long planId) {
        return new TenantSubscription(tenantId, planId);
    }
}
