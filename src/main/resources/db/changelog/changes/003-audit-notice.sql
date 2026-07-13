--liquibase formatted sql

-- =============================================================================
-- 003: 감사 로그 / 플랫폼 공지
--
-- audit_log 는 MySQL 5.7 에 RLS 가 없어 사라진 DB 레벨 방어선을 보완하는
-- "사후 추적" 수단이다. 테넌트 데이터에 대한 모든 변경 작업을 남긴다.
-- tenant_id 가 NULL 인 행은 플랫폼 관리자의 전역 작업(업체 등록/정지 등)이다.
-- =============================================================================


--changeset saas:003-01-audit-log
--comment 감사 로그. FK 를 걸지 않는다 — 대상 레코드가 삭제된 뒤에도 로그는 남아야 하고,
-- 로그 적재가 참조 무결성 검사 때문에 느려지거나 실패해서는 안 된다.
CREATE TABLE audit_log (
    audit_id      BIGINT UNSIGNED            NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT UNSIGNED            NULL COMMENT 'NULL = 플랫폼 전역 작업',
    actor_user_id BIGINT UNSIGNED            NULL COMMENT 'NULL = 시스템/배치',
    actor_role    VARCHAR(30)                NULL,
    action        VARCHAR(50)                NOT NULL COMMENT 'TENANT_CREATE / TENANT_SUSPEND / LOGIN_FAIL ...',
    resource_type VARCHAR(50)                NULL,
    resource_id   VARCHAR(50)                NULL,
    before_json   JSON                       NULL,
    after_json    JSON                       NULL,
    result        ENUM ('SUCCESS','FAILURE') NOT NULL DEFAULT 'SUCCESS',
    message       VARCHAR(500)               NULL,
    ip_address    VARCHAR(45)                NULL,
    user_agent    VARCHAR(255)               NULL,
    created_at    DATETIME(6)                NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (audit_id),
    KEY idx_audit__tenant_created (tenant_id, created_at),
    KEY idx_audit__actor_created (actor_user_id, created_at),
    KEY idx_audit__action_created (action, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '감사 로그';
--rollback DROP TABLE audit_log;


--changeset saas:003-02-platform-notice
--comment 플랫폼 공지사항
CREATE TABLE platform_notice (
    notice_id    BIGINT UNSIGNED                         NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200)                            NOT NULL,
    content      TEXT                                    NOT NULL,
    notice_type  ENUM ('GENERAL','MAINTENANCE','URGENT') NOT NULL DEFAULT 'GENERAL',
    is_published TINYINT(1)                              NOT NULL DEFAULT 0,
    published_at DATETIME(6)                             NULL,
    starts_at    DATETIME(6)                             NULL COMMENT '노출 시작',
    ends_at      DATETIME(6)                             NULL COMMENT '노출 종료',
    created_by   BIGINT UNSIGNED                         NULL,
    created_at   DATETIME(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)                             NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (notice_id),
    KEY idx_notice__published (is_published, starts_at, ends_at),
    CONSTRAINT fk_notice__creator FOREIGN KEY (created_by) REFERENCES user_account (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '플랫폼 공지사항';
--rollback DROP TABLE platform_notice;
