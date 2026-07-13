--liquibase formatted sql

-- =============================================================================
-- 002: 인증 / 권한 — 계정, 역할, 권한, 테넌트 소속, 리프레시 토큰
--
-- 설계 원칙:
--  - user_account 는 테넌트에 종속되지 않는 전역 계정이다.
--    한 사람이 여러 업체에 소속될 수 있으므로(예: 프랜차이즈 점주), 소속은
--    tenant_user 로 분리한다. 로그인 → 소속 업체 목록 조회 → 업체 선택 흐름을 지원.
--    따라서 user_account 에는 tenant_id 가 없다. (의도적)
--  - 프론트가 보낸 tenantId 는 신뢰하지 않고, 항상 tenant_user 로 소속을 재검증한다.
-- =============================================================================


--changeset saas:002-01-user-account
--comment 사용자 계정 (전역, 테넌트 비종속)
CREATE TABLE user_account (
    user_id             BIGINT UNSIGNED                     NOT NULL AUTO_INCREMENT,
    email               VARCHAR(150)                        NOT NULL,
    password_hash       VARCHAR(100)                        NOT NULL COMMENT 'BCrypt',
    name                VARCHAR(50)                         NOT NULL,
    phone               VARCHAR(20)                         NULL,
    status              ENUM ('ACTIVE','LOCKED','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    is_platform_admin   TINYINT(1)                          NOT NULL DEFAULT 0 COMMENT '플랫폼 통합 관리자 여부. 테넌트 소속과 무관한 전역 권한',
    last_login_at       DATETIME(6)                         NULL,
    password_changed_at DATETIME(6)                         NULL,
    login_fail_count    INT UNSIGNED                        NOT NULL DEFAULT 0,
    locked_until        DATETIME(6)                         NULL COMMENT '로그인 연속 실패 시 잠금 해제 시각',
    created_at          DATETIME(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user__email (email),
    KEY idx_user__platform_admin (is_platform_admin)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '사용자 계정 (전역)';
--rollback DROP TABLE user_account;


--changeset saas:002-02-role
--comment 역할. scope=PLATFORM 은 테넌트 소속 없이 전역 동작, scope=TENANT 는 특정 테넌트 안에서만 유효.
CREATE TABLE role (
    role_id     INT UNSIGNED               NOT NULL AUTO_INCREMENT,
    role_code   VARCHAR(30)                NOT NULL COMMENT 'PLATFORM_ADMIN / TENANT_OWNER / ...',
    role_name   VARCHAR(50)                NOT NULL,
    scope       ENUM ('PLATFORM','TENANT') NOT NULL,
    description VARCHAR(255)               NULL,
    created_at  DATETIME(6)                NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_role__code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '역할';
--rollback DROP TABLE role;


--changeset saas:002-03-permission
--comment 권한 (resource + action 조합)
CREATE TABLE permission (
    permission_id   INT UNSIGNED NOT NULL AUTO_INCREMENT,
    permission_code VARCHAR(50)  NOT NULL COMMENT 'TENANT_CREATE / RESERVATION_APPROVE ...',
    permission_name VARCHAR(100) NOT NULL,
    resource        VARCHAR(30)  NOT NULL COMMENT 'TENANT / RESERVATION / BOARD ...',
    action          VARCHAR(20)  NOT NULL COMMENT 'READ / CREATE / UPDATE / DELETE / APPROVE',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_permission__code (permission_code),
    KEY idx_permission__resource (resource)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '권한';
--rollback DROP TABLE permission;


--changeset saas:002-04-role-permission
--comment 역할-권한 매핑
CREATE TABLE role_permission (
    role_id       INT UNSIGNED NOT NULL,
    permission_id INT UNSIGNED NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permission__permission (permission_id),
    CONSTRAINT fk_role_permission__role FOREIGN KEY (role_id) REFERENCES role (role_id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission__permission FOREIGN KEY (permission_id) REFERENCES permission (permission_id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '역할-권한 매핑';
--rollback DROP TABLE role_permission;


--changeset saas:002-05-tenant-user
--comment 테넌트 소속(멤버십). 인가의 진실 공급원 — JWT 의 tenantId 를 믿지 않고 매 요청 여기서 재검증한다.
-- owner_marker 는 role_id = 2 (TENANT_OWNER) 를 하드코딩한다. 004 시드의 role_id 고정과 짝을 이룬다.
CREATE TABLE tenant_user (
    tenant_user_id BIGINT UNSIGNED                       NOT NULL AUTO_INCREMENT,
    tenant_id      BIGINT UNSIGNED                       NOT NULL,
    user_id        BIGINT UNSIGNED                       NOT NULL,
    role_id        INT UNSIGNED                          NOT NULL,
    status         ENUM ('INVITED','ACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    invited_at     DATETIME(6)                           NULL,
    joined_at      DATETIME(6)                           NULL,
    created_at     DATETIME(6)                           NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)                           NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    owner_marker   TINYINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN role_id = 2 THEN 1 ELSE NULL END) STORED COMMENT '테넌트당 대표(TENANT_OWNER) 유일성 강제용',
    PRIMARY KEY (tenant_user_id),
    UNIQUE KEY uk_tenant_user__tenant_user (tenant_id, user_id),
    UNIQUE KEY uk_tenant_user__one_owner (tenant_id, owner_marker),
    KEY idx_tenant_user__user (user_id),
    KEY idx_tenant_user__role (role_id),
    CONSTRAINT fk_tenant_user__tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_tenant_user__user FOREIGN KEY (user_id) REFERENCES user_account (user_id),
    CONSTRAINT fk_tenant_user__role FOREIGN KEY (role_id) REFERENCES role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '테넌트 소속(멤버십)';
--rollback DROP TABLE tenant_user;


--changeset saas:002-06-refresh-token
--comment 리프레시 토큰. 원문이 아닌 SHA-256 해시만 저장한다 (DB 유출 시 토큰 재사용 방지).
CREATE TABLE refresh_token (
    token_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    tenant_id  BIGINT UNSIGNED NULL COMMENT '선택된 테넌트 컨텍스트. 플랫폼 관리자는 NULL',
    token_hash CHAR(64)        NOT NULL COMMENT 'SHA-256 hex',
    expires_at DATETIME(6)     NOT NULL,
    revoked_at DATETIME(6)     NULL,
    user_agent VARCHAR(255)    NULL,
    ip_address VARCHAR(45)     NULL COMMENT 'IPv6 대응 길이',
    created_at DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (token_id),
    UNIQUE KEY uk_refresh_token__hash (token_hash),
    KEY idx_refresh_token__user (user_id, revoked_at),
    KEY idx_refresh_token__expires (expires_at),
    CONSTRAINT fk_refresh_token__user FOREIGN KEY (user_id) REFERENCES user_account (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_token__tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '리프레시 토큰';
--rollback DROP TABLE refresh_token;
