package com.saas.admin.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_plan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long id;

    @Column(name = "plan_code", nullable = false, length = 30)
    private String code;

    @Column(name = "plan_name", nullable = false, length = 50)
    private String name;

    @Column(name = "monthly_price", nullable = false)
    private int monthlyPrice;

    @Column(name = "max_staff", nullable = false)
    private int maxStaff;

    @Column(name = "max_page", nullable = false)
    private int maxPage;

    @Column(name = "max_storage_mb", nullable = false)
    private int maxStorageMb;

    @Column(name = "max_reservation_per_month", nullable = false)
    private int maxReservationPerMonth;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
