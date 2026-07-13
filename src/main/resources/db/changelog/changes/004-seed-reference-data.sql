--liquibase formatted sql

-- =============================================================================
-- 004: 참조 데이터 시드 — 예약어 slug / 역할 / 권한 / 요금제
--
-- [중요] role_id 는 명시적으로 고정한다.
--   002 의 tenant_user.owner_marker 생성 컬럼이 role_id = 2 (TENANT_OWNER) 를
--   하드코딩하므로, 이 값이 바뀌면 "테넌트당 대표 1명" 제약이 조용히 깨진다.
--   role 시드를 수정할 때는 002-05 의 owner_marker 정의도 함께 확인할 것.
-- =============================================================================


--changeset saas:004-01-seed-reserved-slug
--comment 예약어 slug — 업체가 이 경로를 선점하면 플랫폼 라우팅이 깨진다.
INSERT INTO reserved_slug (slug, reason) VALUES
    ('admin',          '업체 관리자 경로'),
    ('platform-admin', '플랫폼 관리자 경로'),
    ('api',            'API 경로'),
    ('auth',           '인증 경로'),
    ('login',          '인증 경로'),
    ('logout',         '인증 경로'),
    ('signup',         '인증 경로'),
    ('oauth',          '인증 경로'),
    ('static',         '정적 리소스'),
    ('assets',         '정적 리소스'),
    ('public',         '정적 리소스'),
    ('_next',          'Next.js 예약'),
    ('images',         '정적 리소스'),
    ('files',          '정적 리소스'),
    ('upload',         '정적 리소스'),
    ('about',          '플랫폼 소개 페이지'),
    ('help',           '플랫폼 페이지'),
    ('support',        '플랫폼 페이지'),
    ('terms',          '플랫폼 페이지'),
    ('privacy',        '플랫폼 페이지'),
    ('pricing',        '플랫폼 페이지'),
    ('blog',           '플랫폼 페이지'),
    ('health',         '헬스체크'),
    ('metrics',        '모니터링'),
    ('actuator',       'Spring Actuator'),
    ('robots.txt',     '크롤러 예약'),
    ('sitemap.xml',    '크롤러 예약'),
    ('favicon.ico',    '브라우저 예약'),
    ('www',            '서브도메인 충돌'),
    ('mail',           '서브도메인 충돌'),
    ('cdn',            '서브도메인 충돌'),
    ('app',            '예약'),
    ('dashboard',      '예약'),
    ('settings',       '예약'),
    ('account',        '예약'),
    ('suspended',      '정지 안내 페이지'),
    ('not-found',      '오류 페이지');
--rollback DELETE FROM reserved_slug;


--changeset saas:004-02-seed-role
--comment 역할. role_id 고정 — 002-05 owner_marker 가 role_id=2 에 의존한다.
INSERT INTO role (role_id, role_code, role_name, scope, description) VALUES
    (1, 'PLATFORM_ADMIN', '플랫폼 관리자', 'PLATFORM', '전체 테넌트 관리. 업체 등록/정지, 요금제, 공지'),
    (2, 'TENANT_OWNER',   '업체 대표',     'TENANT',   '자사 전체 관리 + 직원 계정 관리'),
    (3, 'TENANT_MANAGER', '업체 매니저',   'TENANT',   '자사 운영 관리 (예약/상담/게시판/홈페이지)'),
    (4, 'TENANT_STAFF',   '업체 직원',     'TENANT',   '예약/상담 조회 및 처리'),
    (5, 'CUSTOMER',       '고객',          'TENANT',   '본인 예약/상담만 조회');
--rollback DELETE FROM role;


--changeset saas:004-03-seed-permission
--comment 권한 목록
INSERT INTO permission (permission_code, permission_name, resource, action) VALUES
    ('TENANT_CREATE',        '업체 등록',      'TENANT',        'CREATE'),
    ('TENANT_READ_ALL',      '전체 업체 조회', 'TENANT',        'READ'),
    ('TENANT_UPDATE_ALL',    '전체 업체 수정', 'TENANT',        'UPDATE'),
    ('TENANT_SUSPEND',       '업체 사용 중지', 'TENANT',        'SUSPEND'),
    ('PLAN_MANAGE',          '요금제 관리',    'PLAN',          'MANAGE'),
    ('SUBSCRIPTION_MANAGE',  '구독 관리',      'SUBSCRIPTION',  'MANAGE'),
    ('NOTICE_MANAGE',        '공지사항 관리',  'NOTICE',        'MANAGE'),
    ('USAGE_READ',           '사용량 조회',    'USAGE',         'READ'),
    ('AUDIT_READ',           '감사 로그 조회', 'AUDIT',         'READ'),
    ('TENANT_SELF_READ',     '자사 정보 조회', 'TENANT',        'READ_SELF'),
    ('TENANT_SELF_UPDATE',   '자사 정보 수정', 'TENANT',        'UPDATE_SELF'),
    ('STAFF_MANAGE',         '직원 계정 관리', 'STAFF',         'MANAGE'),
    ('SITE_READ',            '홈페이지 조회',  'SITE',          'READ'),
    ('SITE_UPDATE',          '홈페이지 수정',  'SITE',          'UPDATE'),
    ('BOARD_READ',           '게시판 조회',    'BOARD',         'READ'),
    ('BOARD_MANAGE',         '게시판 관리',    'BOARD',         'MANAGE'),
    ('CONSULTATION_READ',    '상담 조회',      'CONSULTATION',  'READ'),
    ('CONSULTATION_MANAGE',  '상담 처리',      'CONSULTATION',  'MANAGE'),
    ('RESERVATION_READ',     '예약 조회',      'RESERVATION',   'READ'),
    ('RESERVATION_APPROVE',  '예약 승인/거절', 'RESERVATION',   'APPROVE'),
    ('RESERVATION_MANAGE',   '예약 관리',      'RESERVATION',   'MANAGE'),
    ('BUSINESS_HOUR_MANAGE', '운영시간 관리',  'BUSINESS_HOUR', 'MANAGE'),
    ('CUSTOMER_READ',        '고객 조회',      'CUSTOMER',      'READ');
--rollback DELETE FROM permission;


--changeset saas:004-04-seed-role-permission
--comment 역할-권한 매핑
INSERT INTO role_permission (role_id, permission_id)
SELECT 1, permission_id FROM permission
WHERE permission_code IN (
    'TENANT_CREATE','TENANT_READ_ALL','TENANT_UPDATE_ALL','TENANT_SUSPEND',
    'PLAN_MANAGE','SUBSCRIPTION_MANAGE','NOTICE_MANAGE','USAGE_READ','AUDIT_READ'
);

INSERT INTO role_permission (role_id, permission_id)
SELECT 2, permission_id FROM permission
WHERE permission_code IN (
    'TENANT_SELF_READ','TENANT_SELF_UPDATE','STAFF_MANAGE',
    'SITE_READ','SITE_UPDATE','BOARD_READ','BOARD_MANAGE',
    'CONSULTATION_READ','CONSULTATION_MANAGE',
    'RESERVATION_READ','RESERVATION_APPROVE','RESERVATION_MANAGE',
    'BUSINESS_HOUR_MANAGE','CUSTOMER_READ','AUDIT_READ'
);

INSERT INTO role_permission (role_id, permission_id)
SELECT 3, permission_id FROM permission
WHERE permission_code IN (
    'TENANT_SELF_READ',
    'SITE_READ','SITE_UPDATE','BOARD_READ','BOARD_MANAGE',
    'CONSULTATION_READ','CONSULTATION_MANAGE',
    'RESERVATION_READ','RESERVATION_APPROVE','RESERVATION_MANAGE',
    'BUSINESS_HOUR_MANAGE','CUSTOMER_READ'
);

INSERT INTO role_permission (role_id, permission_id)
SELECT 4, permission_id FROM permission
WHERE permission_code IN (
    'TENANT_SELF_READ','SITE_READ','BOARD_READ',
    'CONSULTATION_READ','CONSULTATION_MANAGE',
    'RESERVATION_READ','RESERVATION_APPROVE'
);

INSERT INTO role_permission (role_id, permission_id)
SELECT 5, permission_id FROM permission
WHERE permission_code IN ('SITE_READ','BOARD_READ');
--rollback DELETE FROM role_permission;


--changeset saas:004-05-seed-tenant-plan
--comment 요금제
INSERT INTO tenant_plan
    (plan_code, plan_name, monthly_price, max_staff, max_page, max_storage_mb,
     max_reservation_per_month, is_active, display_order)
VALUES
    ('FREE',  '무료',   0,     1,  3,  200,   50,   1, 1),
    ('BASIC', '베이직', 29000, 5,  10, 2000,  500,  1, 2),
    ('PRO',   '프로',   79000, 20, 30, 10000, 5000, 1, 3);
--rollback DELETE FROM tenant_plan;
