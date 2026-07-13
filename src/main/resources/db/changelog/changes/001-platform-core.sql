--liquibase formatted sql

-- =============================================================================
-- 001: 플랫폼 코어 — 테넌트 / 요금제 / 구독 / 도메인 / 사용량
--
-- 대상: MySQL 5.7.44 (InnoDB, utf8mb4)
--
-- [MySQL 5.7 제약 사항과 대응]
--  1) CHECK 제약은 파싱만 하고 무시된다.
--     → 상태값은 ENUM 으로 선언한다. sql_mode 에 STRICT_TRANS_TABLES 가 있으므로
--       ENUM 범위를 벗어난 값은 INSERT 시점에 에러가 된다. (실질적 CHECK 대체)
--  2) Row Level Security 가 없다.
--     → 테넌트 격리는 애플리케이션(Hibernate @Filter) + 전용 DB 계정 권한 분리로 방어한다.
--       DB 레벨 최후 방어선이 없으므로 통합 격리 테스트를 CI 필수로 둔다.
--  3) 부분 유니크 인덱스(partial unique index)가 없다.
--     → STORED 생성 컬럼 + UNIQUE 조합으로 "테넌트당 활성 구독 1건" 등을 강제한다.
--       (NULL 은 유니크 인덱스에서 충돌하지 않는 성질을 이용)
--
-- MySQL 은 DDL 이 트랜잭션이 아니므로 테이블 하나당 changeset 하나로 쪼갠다.
-- 중간에 실패해도 성공한 changeset 은 기록되어 재실행이 안전하다.
-- =============================================================================


--changeset saas:001-01-tenant-plan
--comment 요금제
CREATE TABLE tenant_plan (
    plan_id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_code                 VARCHAR(30)     NOT NULL COMMENT '요금제 코드 (FREE / BASIC / PRO)',
    plan_name                 VARCHAR(50)     NOT NULL COMMENT '요금제 표시명',
    monthly_price             INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '월 요금(원)',
    max_staff                 INT UNSIGNED    NOT NULL DEFAULT 3 COMMENT '직원 계정 수 상한',
    max_page                  INT UNSIGNED    NOT NULL DEFAULT 5 COMMENT '홈페이지 페이지 수 상한',
    max_storage_mb            INT UNSIGNED    NOT NULL DEFAULT 500 COMMENT '스토리지 상한(MB)',
    max_reservation_per_month INT UNSIGNED    NOT NULL DEFAULT 100 COMMENT '월 예약 건수 상한',
    is_active                 TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '신규 가입 가능 여부',
    display_order             INT             NOT NULL DEFAULT 0,
    created_at                DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (plan_id),
    UNIQUE KEY uk_tenant_plan__code (plan_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '요금제';
--rollback DROP TABLE tenant_plan;


--changeset saas:001-02-reserved-slug
--comment 예약어 slug 블랙리스트 — /{tenantSlug} 와 /admin, /api 등 플랫폼 경로의 충돌 방지
CREATE TABLE reserved_slug (
    reserved_slug_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    slug             VARCHAR(50)  NOT NULL COMMENT '선점된 경로값 (소문자)',
    reason           VARCHAR(100) NULL COMMENT '예약 사유',
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (reserved_slug_id),
    UNIQUE KEY uk_reserved_slug__slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '예약어 slug 블랙리스트';
--rollback DROP TABLE reserved_slug;


--changeset saas:001-03-tenant
--comment 테넌트(업체) — 모든 업무 데이터의 격리 기준
-- status 별 동작:
--   PENDING   : 고객 화면 404, 관리자 로그인 가능(공개 전)
--   ACTIVE    : 정상
--   SUSPENDED : 고객 화면 503 + 안내 페이지 (404 아님 — 검색 색인 유지)
--   CLOSED    : 고객 화면 404, 관리자 로그인 차단, purge_after 경과 시 파기
CREATE TABLE tenant (
    tenant_id        BIGINT UNSIGNED                                NOT NULL AUTO_INCREMENT,
    tenant_code      VARCHAR(20)                                    NOT NULL COMMENT '업체 코드 (SHOP0001)',
    tenant_name      VARCHAR(100)                                   NOT NULL COMMENT '업체명 (한글 허용)',
    tenant_slug      VARCHAR(30)                                    NOT NULL COMMENT 'URL 경로. 영소문자/숫자/하이픈만, 3~30자',
    status           ENUM ('PENDING','ACTIVE','SUSPENDED','CLOSED') NOT NULL DEFAULT 'PENDING',
    plan_id          BIGINT UNSIGNED                                NULL,
    owner_name       VARCHAR(50)                                    NULL COMMENT '대표자명',
    business_no      VARCHAR(20)                                    NULL COMMENT '사업자등록번호',
    contact_phone    VARCHAR(20)                                    NULL,
    contact_email    VARCHAR(150)                                   NULL,
    postal_code      VARCHAR(10)                                    NULL,
    address          VARCHAR(255)                                   NULL,
    address_detail   VARCHAR(255)                                   NULL,
    latitude         DECIMAL(10, 7)                                 NULL,
    longitude        DECIMAL(10, 7)                                 NULL,
    opened_at        DATETIME(6)                                    NULL COMMENT 'ACTIVE 최초 전환 시각',
    suspended_at     DATETIME(6)                                    NULL,
    suspended_reason VARCHAR(255)                                   NULL,
    closed_at        DATETIME(6)                                    NULL,
    purge_after      DATE                                           NULL COMMENT 'CLOSED 후 데이터 파기 예정일',
    created_at       DATETIME(6)                                    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)                                    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by       BIGINT UNSIGNED                                NULL COMMENT '등록한 플랫폼 관리자 user_id',
    updated_by       BIGINT UNSIGNED                                NULL,
    PRIMARY KEY (tenant_id),
    UNIQUE KEY uk_tenant__slug (tenant_slug),
    UNIQUE KEY uk_tenant__code (tenant_code),
    KEY idx_tenant__status (status),
    KEY idx_tenant__plan (plan_id),
    CONSTRAINT fk_tenant__plan FOREIGN KEY (plan_id) REFERENCES tenant_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '테넌트(업체)';
--rollback DROP TABLE tenant;


--changeset saas:001-04-tenant-subscription
--comment 구독. active_marker 는 부분 유니크 인덱스 대용 — 테넌트당 ACTIVE 구독 1건을 DB가 물리적으로 강제한다.
CREATE TABLE tenant_subscription (
    subscription_id BIGINT UNSIGNED                                          NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT UNSIGNED                                          NOT NULL,
    plan_id         BIGINT UNSIGNED                                          NOT NULL,
    status          ENUM ('TRIAL','ACTIVE','PAST_DUE','CANCELLED','EXPIRED') NOT NULL DEFAULT 'TRIAL',
    started_on      DATE                                                     NOT NULL,
    ends_on         DATE                                                     NULL COMMENT 'NULL 이면 무기한',
    auto_renew      TINYINT(1)                                               NOT NULL DEFAULT 1,
    cancelled_at    DATETIME(6)                                              NULL,
    cancel_reason   VARCHAR(255)                                             NULL,
    created_at      DATETIME(6)                                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)                                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active_marker   TINYINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED COMMENT '활성 구독 유일성 강제용',
    PRIMARY KEY (subscription_id),
    UNIQUE KEY uk_subscription__one_active (tenant_id, active_marker),
    KEY idx_subscription__tenant_status (tenant_id, status),
    KEY idx_subscription__plan (plan_id),
    CONSTRAINT fk_subscription__tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_subscription__plan FOREIGN KEY (plan_id) REFERENCES tenant_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '테넌트 구독';
--rollback DROP TABLE tenant_subscription;


--changeset saas:001-05-tenant-domain
--comment 커스텀 도메인 (MVP 제외 기능이나 스키마는 미리 분리). primary_marker 로 테넌트당 대표 도메인 1개를 강제.
CREATE TABLE tenant_domain (
    domain_id           BIGINT UNSIGNED                           NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT UNSIGNED                           NOT NULL,
    domain_name         VARCHAR(191)                              NOT NULL COMMENT '예: delicious-store.com',
    domain_type         ENUM ('SUBPATH','CUSTOM')                 NOT NULL DEFAULT 'CUSTOM',
    verification_status ENUM ('PENDING','VERIFIED','FAILED')      NOT NULL DEFAULT 'PENDING',
    verification_token  VARCHAR(100)                              NULL COMMENT 'DNS TXT 소유 검증 토큰',
    verified_at         DATETIME(6)                               NULL,
    ssl_status          ENUM ('NONE','PENDING','ISSUED','FAILED') NOT NULL DEFAULT 'NONE',
    is_primary          TINYINT(1)                                NOT NULL DEFAULT 0,
    created_at          DATETIME(6)                               NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)                               NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    primary_marker      TINYINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN is_primary = 1 THEN 1 ELSE NULL END) STORED COMMENT '대표 도메인 유일성 강제용',
    PRIMARY KEY (domain_id),
    UNIQUE KEY uk_domain__name (domain_name),
    UNIQUE KEY uk_domain__one_primary (tenant_id, primary_marker),
    KEY idx_domain__tenant (tenant_id),
    CONSTRAINT fk_domain__tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '테넌트 도메인';
--rollback DROP TABLE tenant_domain;


--changeset saas:001-06-tenant-usage-daily
--comment 일별 사용량 — 플랫폼 관리자의 사용량 확인 및 요금제 상한 검사용. 배치가 매일 집계해 upsert.
CREATE TABLE tenant_usage_daily (
    usage_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT UNSIGNED NOT NULL,
    stat_date          DATE            NOT NULL,
    reservation_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    consultation_count INT UNSIGNED    NOT NULL DEFAULT 0,
    board_post_count   INT UNSIGNED    NOT NULL DEFAULT 0,
    storage_used_mb    INT UNSIGNED    NOT NULL DEFAULT 0,
    page_view_count    INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (usage_id),
    UNIQUE KEY uk_usage__tenant_date (tenant_id, stat_date),
    KEY idx_usage__date (stat_date),
    CONSTRAINT fk_usage__tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC COMMENT = '테넌트 일별 사용량';
--rollback DROP TABLE tenant_usage_daily;
