package com.saas.admin.auth.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 리프레시 토큰. 원문이 아닌 SHA-256 해시만 저장한다 — DB 가 유출돼도 토큰을 재사용할 수 없다.
 * tenantId 가 NULL 이면 테넌트 컨텍스트가 없는 토큰(플랫폼 관리자 또는 업체 선택 전)이다.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_id")
    private Long tenantId;

    // CHAR(64) 컬럼이다. String 은 기본이 VARCHAR 라 ddl-auto=validate 가 타입 불일치로 막는다.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private RefreshToken(Long userId, Long tenantId, String tokenHash,
                         LocalDateTime expiresAt, String userAgent, String ipAddress) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public static RefreshToken issue(Long userId, Long tenantId, String tokenHash,
                                     LocalDateTime expiresAt, String userAgent, String ipAddress) {
        return new RefreshToken(userId, tenantId, tokenHash, expiresAt, userAgent, ipAddress);
    }

    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }

    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = LocalDateTime.now();
        }
    }
}
