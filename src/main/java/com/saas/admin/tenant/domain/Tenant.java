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

    /**
     * 소프트 삭제 표시. 'Y' / 'N'. 상태(CLOSED)와 별개다 — 목록에서 숨기고 복구할 수 있는
     * 관리자용 삭제여부다. 삭제된 업체는 조회 기본 목록에서 빠진다(includeDeleted 로만 보인다).
     */
    @Column(name = "is_deleted", nullable = false, length = 1)
    private String deleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

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
        this.deleted = "N";
    }

    public static Tenant register(String code, String name, String slug, Long planId, String ownerName,
                                  String businessNo, String contactPhone, String contactEmail, Long createdBy) {
        return new Tenant(code, name, slug, planId, ownerName, businessNo, contactPhone, contactEmail, createdBy);
    }

    /** 업체 정보만 등록(대표 계정 없이). 주소까지 함께 받는다. PENDING 으로 시작. */
    public static Tenant create(String code, String name, String slug, Long planId, String ownerName,
                                String businessNo, String contactPhone, String contactEmail,
                                String postalCode, String address, String addressDetail, Long createdBy) {
        Tenant t = new Tenant(code, name, slug, planId, ownerName, businessNo, contactPhone, contactEmail, createdBy);
        t.postalCode = postalCode;
        t.address = address;
        t.addressDetail = addressDetail;
        return t;
    }

    /**
     * 업체 정보 수정. slug·code·status 는 여기서 바꾸지 않는다(개설/중지는 별도, slug 는 URL 이라 불변).
     * 선택 항목은 빈 문자열이면 비운다(null). 요금제(planId)는 null 로 해제할 수 있다.
     */
    public void update(String name, Long planId, String ownerName, String businessNo,
                       String contactPhone, String contactEmail, String postalCode,
                       String address, String addressDetail, Long actorId) {
        if (name != null && !name.isBlank()) this.name = name;
        this.planId = planId;
        this.ownerName = blankToNull(ownerName);
        this.businessNo = blankToNull(businessNo);
        this.contactPhone = blankToNull(contactPhone);
        this.contactEmail = blankToNull(contactEmail);
        this.postalCode = blankToNull(postalCode);
        this.address = blankToNull(address);
        this.addressDetail = blankToNull(addressDetail);
        this.updatedBy = actorId;
    }

    /** 경로(slug) 변경. 형식·예약어·중복 검증은 서비스에서 끝낸 뒤 호출한다. */
    public void changeSlug(String slug) {
        this.slug = slug;
    }

    /** 소프트 삭제(삭제여부='Y'). 이미 삭제된 업체는 막는다. */
    public void markDeleted(Long actorId) {
        if (isDeleted()) {
            throw new ApiException(ErrorCode.TENANT_ALREADY_DELETED);
        }
        this.deleted = "Y";
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = actorId;
        this.updatedBy = actorId;
    }

    /** 삭제 복구. */
    public void restore(Long actorId) {
        this.deleted = "N";
        this.deletedAt = null;
        this.deletedBy = null;
        this.updatedBy = actorId;
    }

    public boolean isDeleted() {
        return "Y".equals(deleted);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
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
