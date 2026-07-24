package com.saas.admin.auth.domain;

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
 * 테넌트 소속(멤버십). <b>인가의 진실 공급원</b>이다.
 * JWT 의 tenantId 를 믿지 않고 매 요청 여기서 소속과 status 를 재검증한다. (설계안 §5.6, §11)
 * <p>
 * owner_marker 는 DB 의 STORED 생성 컬럼이라 매핑하지 않는다. (Hibernate 가 건드리면 안 됨)
 */
@Entity
@Table(name = "tenant_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_user_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "status", nullable = false)
    private TenantUserStatus status;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private TenantUser(Long tenantId, Long userId, Integer roleId) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.roleId = roleId;
        this.status = TenantUserStatus.ACTIVE;
        this.joinedAt = LocalDateTime.now();
    }

    /** 업체 등록 시 대표 계정을 소속시킨다. role_id = 2 는 DB 의 owner_marker 와 짝을 이룬다. */
    public static TenantUser createOwner(Long tenantId, Long userId) {
        return new TenantUser(tenantId, userId, Role.TENANT_OWNER_ID);
    }

    /** 직원 멤버십 생성(대표/매니저/직원 역할). */
    public static TenantUser of(Long tenantId, Long userId, Integer roleId) {
        return new TenantUser(tenantId, userId, roleId);
    }

    public void changeRole(Integer roleId) {
        if (roleId != null) this.roleId = roleId;
    }

    public void setStatus(TenantUserStatus status) {
        if (status != null) this.status = status;
    }

    public boolean isOwner() {
        return Role.TENANT_OWNER_ID.equals(this.roleId);
    }

    public boolean isActive() {
        return status == TenantUserStatus.ACTIVE;
    }
}
