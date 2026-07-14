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
 * 사용자 계정 — 전역이며 테넌트에 종속되지 않는다. (002-auth.sql 참조)
 * 한 사람이 여러 업체에 소속될 수 있으므로 소속은 {@link TenantUser} 로 분리한다.
 */
@Entity
@Table(name = "user_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    /** 연속 로그인 실패가 이 횟수에 도달하면 계정을 일시 잠근다. */
    public static final int MAX_LOGIN_FAIL = 5;
    public static final int LOCK_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    // MySQL 의 ENUM 컬럼은 JDBC 가 CHAR 로 보고한다. @Enumerated(STRING) 만 쓰면
    // Hibernate 는 VARCHAR 를 기대하므로 ddl-auto=validate 가 타입 불일치로 기동을 막는다.
    // JdbcTypeCode 로 CHAR 를 명시해 스키마 검증을 통과시킨다. (다른 ENUM 컬럼도 동일)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "is_platform_admin", nullable = false)
    private boolean platformAdmin;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private UserAccount(String email, String passwordHash, String name, String phone, boolean platformAdmin) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.platformAdmin = platformAdmin;
        this.status = UserStatus.ACTIVE;
        this.loginFailCount = 0;
        this.passwordChangedAt = LocalDateTime.now();
    }

    public static UserAccount createPlatformAdmin(String email, String passwordHash, String name) {
        return new UserAccount(email, passwordHash, name, null, true);
    }

    public static UserAccount createTenantUser(String email, String passwordHash, String name, String phone) {
        return new UserAccount(email, passwordHash, name, phone, false);
    }

    /** 잠금 시간이 지났으면 스스로 풀린다. */
    public boolean isLocked() {
        if (lockedUntil == null) {
            return false;
        }
        return lockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isUsable() {
        return status == UserStatus.ACTIVE;
    }

    public void onLoginSuccess() {
        this.loginFailCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = LocalDateTime.now();
        if (this.status == UserStatus.LOCKED) {
            this.status = UserStatus.ACTIVE;
        }
    }

    public void onLoginFail() {
        this.loginFailCount++;
        if (this.loginFailCount >= MAX_LOGIN_FAIL) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
            this.loginFailCount = 0;
        }
    }
}
