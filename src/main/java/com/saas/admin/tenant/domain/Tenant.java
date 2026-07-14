package com.saas.admin.tenant.domain;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 테넌트(업체) — 모든 업무 데이터의 격리 기준. (001-03 참조)
 * 업체명은 한글을 허용하고, URL 경로로 쓰이는 slug 는 영문으로 따로 둔다. (설계안 §2.1)
 */
@Entity
@Table(name = "tenant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant {

    /** CLOSED 후 데이터 파기까지의 유예기간. (설계안 §4.1) */
    public static final int PURGE_GRACE_DAYS = 90;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_id")
    private Long id;

    @Column(name = "tenant_code", nullable = false, length = 20)
    private String code;

    @Column(name = "tenant_name", nullable = false, length = 100)
    private String name;

    @Column(name = "tenant_slug", nullable = false, length = 30)
    private String slug;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "status", nullable = false)
    private TenantStatus status;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "owner_name", length = 50)
    private String ownerName;

    @Column(name = "business_no", length = 20)
    private String businessNo;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "address")
    private String address;

    @Column(name = "address_detail")
    private String addressDetail;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspended_reason")
    private String suspendedReason;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "purge_after")
    private LocalDate purgeAfter;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    private Tenant(String code, String name, String slug, Long planId, String ownerName,
                   String businessNo, String contactPhone, String contactEmail, Long createdBy) {
        this.code = code;
        this.name = name;
        this.slug = slug;
        this.planId = planId;
        this.ownerName = ownerName;
        this.businessNo = businessNo;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.createdBy = createdBy;
        // 등록 직후에는 공개하지 않는다. 플랫폼 관리자가 명시적으로 개설(ACTIVE)해야 노출된다.
        this.status = TenantStatus.PENDING;
    }

    public static Tenant register(String code, String name, String slug, Long planId, String ownerName,
                                  String businessNo, String contactPhone, String contactEmail, Long createdBy) {
        return new Tenant(code, name, slug, planId, ownerName, businessNo, contactPhone, contactEmail, createdBy);
    }

    /** 서비스 개설. PENDING/SUSPENDED 에서만 가능하다. */
    public void activate(Long actorId) {
        if (status != TenantStatus.PENDING && status != TenantStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    status + " 상태의 업체는 개설할 수 없습니다.");
        }
        if (this.openedAt == null) {
            this.openedAt = LocalDateTime.now();
        }
        this.status = TenantStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspendedReason = null;
        this.updatedBy = actorId;
    }

    /** 서비스 중지. 고객 화면은 404 가 아니라 503 을 낸다 — 검색 색인을 잃지 않기 위함이다. */
    public void suspend(String reason, Long actorId) {
        if (status != TenantStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "ACTIVE 상태의 업체만 중지할 수 있습니다.");
        }
        this.status = TenantStatus.SUSPENDED;
        this.suspendedAt = LocalDateTime.now();
        this.suspendedReason = reason;
        this.updatedBy = actorId;
    }

    /** 서비스 해지. 유예기간 경과 후 데이터를 파기한다. */
    public void close(Long actorId) {
        if (status == TenantStatus.CLOSED) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION, "이미 해지된 업체입니다.");
        }
        this.status = TenantStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        this.purgeAfter = LocalDate.now().plusDays(PURGE_GRACE_DAYS);
        this.updatedBy = actorId;
    }
}
