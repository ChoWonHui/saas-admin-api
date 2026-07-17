package com.saas.admin.adminaccount.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 관리자 리프레시 토큰.
 * <p>
 * 업체 사용자용 {@code refresh_token} 과 테이블을 나눈다. 그쪽은 user_account 에 FK 로 묶여 있어
 * 관리자 ID 를 넣을 수 없다. 한 컬럼이 두 테이블을 가리키게 만들면 FK 를 포기해야 한다.
 * <p>
 * 토큰 원문은 저장하지 않는다. SHA-256 해시만 둔다. (기존 refresh_token 과 동일한 방식)
 */
@Entity
@Table(
        name = "admin_refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_admin_refresh__hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_admin_refresh__emp_no", columnList = "emp_no, revoked_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long id;

    /** 토큰 주인의 사번. admin_account 의 PK 를 그대로 가리킨다. */
    @Column(name = "emp_no", nullable = false, length = 6)
    private String empNo;

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

    public static AdminRefreshToken issue(String empNo, String tokenHash, LocalDateTime expiresAt,
                                          String ipAddress, String userAgent) {
        AdminRefreshToken token = new AdminRefreshToken();
        token.empNo = empNo;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.ipAddress = ipAddress;
        token.userAgent = userAgent;
        return token;
    }

    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }

    public void revoke() {
        if (revokedAt == null) {
            this.revokedAt = LocalDateTime.now();
        }
    }
}
