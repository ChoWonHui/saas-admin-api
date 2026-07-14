package com.saas.admin.common.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 감사 로그 — RLS 부재를 메우는 "사후 추적" 수단. (설계안 §5.5)
 * tenantId 가 NULL 인 행은 플랫폼 관리자의 전역 작업이다.
 * <p>
 * before_json / after_json 컬럼은 의도적으로 매핑하지 않는다. MySQL 5.7 의 JSON 타입은
 * Hibernate 의 스키마 검증과 충돌하기 쉬운데, 현재 기록 대상(로그인/업체등록)에는 필요 없다.
 * 변경 전후 스냅샷이 필요해지는 시점에 타입을 검증하고 매핑한다.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 50)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "result", nullable = false)
    private AuditResult result;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AuditLog(Long tenantId, Long actorUserId, String actorRole, String action,
                     String resourceType, String resourceId, AuditResult result,
                     String message, String ipAddress, String userAgent) {
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.result = result == null ? AuditResult.SUCCESS : result;
        this.message = message;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}
