package com.saas.admin.auth.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    /**
     * ⚠️ 004 시드에 고정된 role_id 다. tenant_user.owner_marker 생성 컬럼이
     * role_id = 2 (TENANT_OWNER) 를 하드코딩하므로 이 값을 바꾸면
     * "테넌트당 대표 1명" 제약이 조용히 깨진다. (002-05 / 004-02 참조)
     */
    public static final Integer PLATFORM_ADMIN_ID = 1;
    public static final Integer TENANT_OWNER_ID = 2;

    @Id
    @Column(name = "role_id")
    private Integer id;

    @Column(name = "role_code", nullable = false, length = 30)
    private String code;

    @Column(name = "role_name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "scope", nullable = false)
    private RoleScope scope;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
