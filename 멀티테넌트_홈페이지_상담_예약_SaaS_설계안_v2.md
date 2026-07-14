# 멀티테넌트 홈페이지·상담·예약 SaaS 설계안 v2

> v1 대비 변경점: slug 네임스페이스 충돌 방지, tenant_id 격리 강제 수단 구체화,
> 예약 중복 방지를 MVP로 승격, 파일/테넌트 상태/비회원 예약 정의 추가, 캐싱 전략 도입.
>
> **v2.1 (2026-07-13)**: 운영 DB 전제가 PostgreSQL → **기존 사내 MySQL 5.7.44** 로 확정되면서
> RLS 기반 격리안을 폐기했다. 격리는 Hibernate Filter + DB 계정 권한 분리 + CI 격리 테스트의
> 3단 방어로 대체한다 (§5).
>
> **v2.2 (2026-07-14)**: 마이그레이션 도구(Liquibase)를 **걷어냈다.** 스키마의 진실 공급원은
> **JPA 엔티티**이고, Hibernate `ddl-auto: update` 가 테이블을 만든다.
> 대가로 §5.3 의 STORED 생성 컬럼 기법을 **새 테이블에는 쓸 수 없다** (Hibernate 가 못 만든다).
> 기존 테이블의 제약은 그대로 살아 있다. 자세한 내용은 [`CLAUDE.md`](./CLAUDE.md) §1.

---

## 1. 서비스 개요

하나의 대표 도메인에서 업체별 경로를 다르게 제공하고, 경로와 매핑된 업체를 기준으로 홈페이지, 상담, 게시판, 예약관리 기능을 제공하는 멀티테넌트 SaaS 시스템이다.

```text
https://www.홍보.com/{tenantSlug}
```

`/#/경로` 방식은 사용하지 않는다. `#` 뒤의 값은 서버로 전달되지 않아 SEO, 서버 렌더링, 공유 미리보기, 로그 분석에 불리하다.

---

## 2. URL 네임스페이스 설계 (v2 신규)

`/{tenantSlug}`와 플랫폼 경로가 같은 최상위 네임스페이스를 공유하므로, 충돌 방지 규칙을 **업체 등록 시점에 강제**한다.

### 2.1 slug 규칙

```text
- 허용 문자 : 소문자 영문(a-z), 숫자(0-9), 하이픈(-)
- 길이      : 3 ~ 30자
- 시작/끝   : 영문 또는 숫자 (하이픈으로 시작·종료 불가)
- 연속 하이픈(--) 불가
- 한글 slug 금지 (퍼센트 인코딩으로 URL·로그·SEO 품질 저하)
```

한글 업체명은 `tenant_name`에 저장하고, slug는 별도로 영문 변환하여 사용한다.

```text
tenant_name : 맛있는식당
tenant_slug : delicious
```

### 2.2 예약어(Reserved Slug) 블랙리스트

아래 값은 slug로 등록할 수 없다. DB 테이블(`RESERVED_SLUG`)로 관리하여 운영 중 추가 가능하게 한다.

```text
admin, platform-admin, api, auth, login, logout, signup,
static, assets, public, _next, images, files, upload,
about, help, support, terms, privacy, pricing, blog,
health, metrics, robots.txt, sitemap.xml, favicon.ico,
www, mail, ftp, cdn, app, dashboard, settings, account
```

### 2.3 경로 우선순위

Next.js는 정적 세그먼트를 동적 세그먼트보다 우선 매칭하므로, 아래 순서가 보장된다.

```text
1. /admin/**            (업체 관리자)
2. /platform-admin/**   (플랫폼 관리자)
3. /api/**              (BFF)
4. /{tenantSlug}/**     (고객용 홍보 홈페이지)
```

단, 라우팅이 우연히 동작하는 것에 의존하지 않고 **등록 단계에서 블랙리스트 검증을 강제**한다.

---

## 3. 전체 서비스 영역

### 3.1 고객용 홍보 홈페이지 `/{tenantSlug}`

- 업체 소개 / 서비스·메뉴 소개 / 게시판
- 상담 신청 / 예약 신청
- 오시는 길 / 연락처 / 카카오톡 연결

### 3.2 업체 관리자 `/admin`

- 홈페이지 내용 관리 / 게시판 관리
- 상담 내역 관리 / 예약 관리 / 고객 관리
- 직원 계정 관리 / 운영시간 관리 / 알림 관리

### 3.3 플랫폼 통합 관리자 `/platform-admin`

- 업체 등록 / 업체 계정 생성 / 경로(slug) 설정
- 요금제 관리 / 구독 관리 / 서비스 개설·중지
- 공지사항 관리 / 사용량 확인 / 권한 관리

---

## 4. 핵심 개념: Tenant

```text
/delicious
→ tenant_slug 조회 (캐시 우선)
→ tenant_id 확인
→ tenant.status 검사
→ 해당 업체 데이터만 조회
```

### TENANT

```text
tenant_id
tenant_code
tenant_name
tenant_slug          -- UNIQUE
status               -- ACTIVE / SUSPENDED / CLOSED / PENDING
plan_id
suspended_reason
suspended_at
created_at
updated_at
```

### 4.1 테넌트 상태별 동작 정의 (v2 신규)

| status | 고객 화면 | 업체 관리자 | 데이터 |
|---|---|---|---|
| `PENDING` | 404 | 로그인 가능, 공개 전 안내 | 유지 |
| `ACTIVE` | 정상 노출 | 정상 | 유지 |
| `SUSPENDED` | 안내 페이지(HTTP 503) — "일시적으로 이용할 수 없습니다" | 읽기 전용 | 유지 |
| `CLOSED` | 404 | 로그인 차단 | 보존 후 유예기간 경과 시 파기 |

- `SUSPENDED`는 요금 미납·운영 정지 상황으로, 404가 아닌 **503 + 안내 페이지**로 응답한다. 검색엔진이 색인을 삭제하지 않도록 하기 위함이다.
- `CLOSED`는 404를 반환하고, 데이터 파기 유예기간(예: 90일)을 `TENANT` 정책으로 관리한다.

---

## 5. 데이터 격리: tenant_id 강제 (v2 핵심 보강 / v2.1 MySQL 대응)

> **⚠️ 전제 변경 (2026-07-13 확정)**
> 실제 운영 DB는 PostgreSQL이 아니라 **기존 사내 MySQL 5.7.44 서버**이며,
> 그 안에 신규 스키마 `tenant_saas`를 만들어 사용하기로 결정했다.
> **MySQL 5.7에는 Row Level Security가 존재하지 않는다.** 아래 5.2의 원안(RLS)은 폐기하고,
> MySQL에서 실제로 가능한 대체 방어책으로 교체한다.
>
> 결과적으로 **DB 레벨 최후 방어선이 없다.** 이는 감수한 리스크이며, 애플리케이션 방어와
> 테스트를 그만큼 더 엄격하게 가져가야 한다. DB를 8.0 이상 또는 PostgreSQL로 올릴 수 있게 되면
> 5.2를 RLS로 되돌리는 것을 최우선 과제로 둔다.

"`WHERE tenant_id` 조건을 넣어야 한다"는 규칙은 사람이 지키는 순간 반드시 뚫린다.
가능한 계층 전부에서 구조적으로 막는다.

### 5.1 1차 방어 — 애플리케이션 (Hibernate Filter)

모든 업무 엔티티는 공통 `BaseTenantEntity`를 상속하고, Hibernate `@Filter`로 자동 필터링한다.

```java
@MappedSuperclass
@FilterDef(name = "tenantFilter",
           parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class BaseTenantEntity {
    @Column(nullable = false, updatable = false)
    private Long tenantId;
}
```

요청 진입 시 인터셉터가 `TenantContext`(ThreadLocal)에 `tenantId`를 세팅하고, 세션에 필터를 활성화한다.

```java
// 요청당 1회
Session session = em.unwrap(Session.class);
session.enableFilter("tenantFilter")
       .setParameter("tenantId", TenantContext.getTenantId());
```

### 5.2 2차 방어 — DB 계정 권한 분리 (RLS 대체)

MySQL 5.7에 RLS가 없으므로, DB가 해줄 수 있는 방어는 **접근 가능 범위를 계정으로 좁히는 것**뿐이다.
최소한 다른 서비스(`cwh` = 기존 food-biz DB)로의 크로스 DB 유출은 물리적으로 차단한다.

| 계정 | 권한 | 용도 |
|---|---|---|
| `saas_app` | `tenant_saas.*` 에 `ALL` (DDL 포함) | 애플리케이션 런타임 |
| `saas_migrate` | `tenant_saas.*` 에 `ALL` (DDL 포함) | Liquibase 전용이었으나 **현재 미사용** |

- 런타임 계정은 `tenant_saas` 외의 DB가 **보이지도 않는다.** (검증 완료: `SHOW DATABASES` → `tenant_saas`만 노출)
- 단, 이것은 **DB 간 격리**이지 **테넌트 간 격리가 아니다.** 테넌트 격리는 5.1과 5.4에 전적으로 의존한다.

> **⚠️ v2.2 변경**: 원안은 런타임 계정의 **DDL 권한을 막아** 애플리케이션이 스키마를 바꾸지 못하게 했다.
> 그러나 마이그레이션 도구를 걷어내고 `ddl-auto: update` 로 전환하면서, 앱이 테이블을 만들어야 하므로
> `saas_app` 에 DDL 권한을 열었다. **애플리케이션 버그가 스키마를 바꿀 수 있게 된 것은 감수한 리스크다.**
> 범위는 `tenant_saas` 로 한정돼 있어 크로스 DB 차단은 그대로 유효하다.

### 5.3 스키마 레벨 제약 (MySQL 5.7 특성 반영)

MySQL 5.7은 `CHECK` 제약을 파싱만 하고 **무시한다.** 부분 유니크 인덱스도 없다.
따라서 아래 두 가지 기법으로 무결성을 DB에 강제한다.

**(1) 상태값은 `ENUM`으로 선언한다.**
`sql_mode`에 `STRICT_TRANS_TABLES`가 켜져 있으므로, ENUM 범위를 벗어난 값은 INSERT 시점에 에러가 된다.
사실상 CHECK 제약의 대체재다.

**(2) 부분 유니크는 STORED 생성 컬럼으로 구현한다.**
NULL은 유니크 인덱스에서 서로 충돌하지 않는 성질을 이용한다.

```sql
-- "테넌트당 ACTIVE 구독은 최대 1건" 을 DB가 물리적으로 거부하게 만든다
active_marker TINYINT UNSIGNED GENERATED ALWAYS AS
    (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
UNIQUE KEY uk_subscription__one_active (tenant_id, active_marker)
```

같은 기법으로 "테넌트당 대표(TENANT_OWNER) 1명", "테넌트당 대표 도메인 1개"를 강제한다.

### 5.4 3차 방어 — 테스트 (RLS 부재를 메우는 핵심 수단)

DB 레벨 방어선이 없으므로, 이 항목은 **선택이 아니라 필수**다. CI에서 강제한다.

```text
- 모든 업무 테이블에 tenant_id NOT NULL 제약이 있는지 검사하는 스키마 테스트
- 테넌트 A 컨텍스트에서 테넌트 B 데이터 조회 시 0건인지 검증하는 격리 테스트
- ArchUnit: Repository 밖에서 EntityManager / 네이티브 쿼리 직접 사용 금지
- ArchUnit: 업무 엔티티는 반드시 BaseTenantEntity 를 상속
```

### 5.5 사후 추적 — 감사 로그

막지 못한 경우를 최소한 **발견은 할 수 있어야 한다.** `audit_log`에 테넌트 데이터 변경 작업을 모두 남긴다.
(`tenant_id`가 NULL인 행은 플랫폼 관리자의 전역 작업)

### 5.6 신뢰 경계

```text
프론트가 보낸 tenantId  → 절대 신뢰하지 않는다 (참고용 힌트로도 사용하지 않음)
URL의 tenantSlug        → 서버에서 tenant_id로 변환
JWT의 tenantId          → 서명 검증 후 사용, 단 요청 대상 리소스의 tenant_id와 일치 검증
```

---

## 6. 캐싱 전략 (v2 신규)

모든 고객 요청이 `slug → tenant_id` 조회를 거치므로 매 요청 DB 히트는 병목이 된다.

```text
[L1] 애플리케이션 로컬 캐시 (Caffeine)   TTL 60초
[L2] Redis                              TTL 10분
[L3] MySQL (tenant_saas)
```

| 캐시 키 | 값 | 무효화 시점 |
|---|---|---|
| `tenant:slug:{slug}` | tenant_id, status, plan_id | 업체 정보/상태 변경, slug 변경 |
| `site:page:{tenantId}:{pageSlug}` | 페이지+섹션 조합 결과 | 사이트 편집 저장 시 |
| `tenant:hours:{tenantId}` | 운영시간 | 운영시간 수정 시 |

- 무효화는 **업체 관리자 저장 API에서 명시적으로 evict**한다 (TTL 만료만 믿지 않는다).
- 고객용 페이지는 Next.js `revalidateTag`와 연동하여 ISR 캐시도 함께 무효화한다.

---

## 7. DB 구조 (v2.1 확정)

```text
기존 사내 MySQL 5.7.44 서버
└─ tenant_saas   ← 신규 스키마. 기존 cwh(food-biz) 와 완전 분리
   └─ 모든 업무 테이블에 tenant_id 포함 (RLS 는 5.7 미지원 → 5.2 참조)
```

기존 14개 테이블은 문자셋 `utf8mb4` / `utf8mb4_unicode_ci`, 엔진 `InnoDB`, `ROW_FORMAT=DYNAMIC` 이다.

> **v2.2**: 마이그레이션 도구를 쓰지 않는다. 스키마의 진실 공급원은 **JPA 엔티티**이고
> Hibernate `ddl-auto: update` 가 테이블을 만든다.
> 앞으로 Hibernate 가 만드는 테이블에는 위 설정과 §5.3 의 STORED 생성 컬럼이 **적용되지 않는다.**

### 주요 테이블

```text
TENANT
TENANT_DOMAIN
TENANT_PLAN
TENANT_SUBSCRIPTION
RESERVED_SLUG            -- v2 신규

USER_ACCOUNT
TENANT_USER
ROLE
PERMISSION
USER_ROLE

SITE
SITE_PAGE
SITE_SECTION
SITE_MENU
SITE_ASSET
SITE_THEME

BOARD
BOARD_POST
BOARD_COMMENT

CUSTOMER
CONSULTATION
RESERVATION
RESERVATION_SLOT         -- v2 신규 (중복 예약 방지)
RESERVATION_SERVICE
BUSINESS_HOUR
BUSINESS_HOLIDAY         -- v2 신규 (휴무일)

NOTIFICATION
AUDIT_LOG
```

### 공통 인덱스 원칙

모든 업무 테이블의 조회 인덱스는 **`tenant_id`를 선행 컬럼으로** 구성한다.

```sql
CREATE INDEX idx_reservation_tenant_date
  ON reservation (tenant_id, reservation_date, reservation_status);
```

---

## 8. 예약 시스템 (v2 재설계)

### 8.1 중복 예약 방지 — MVP 필수

v1에서 "추후 확장"으로 분류했던 중복 예약 방지는 **예약 시스템의 본질 기능**이므로 1차 범위로 올린다.
동시에 두 고객이 같은 시간대를 신청하는 상황은 서비스 첫날부터 발생한다.

**슬롯 테이블 + DB 유니크 제약** 방식을 채택한다.

```text
RESERVATION_SLOT
────────────────────────
slot_id
tenant_id
slot_date
slot_time
capacity            -- 해당 슬롯 총 수용 가능 건수 (기본 1)
booked_count        -- 현재 확정/요청 건수
version             -- 낙관적 락
created_at
updated_at

UNIQUE (tenant_id, slot_date, slot_time)
```

예약 생성 트랜잭션:

```sql
-- 1) 슬롯 선점 (원자적 증가, capacity 초과 시 0 rows → 실패)
UPDATE reservation_slot
   SET booked_count = booked_count + 1,
       version = version + 1
 WHERE tenant_id = :tenantId
   AND slot_date = :date
   AND slot_time = :time
   AND booked_count < capacity;

-- 2) 0 rows affected → SlotUnavailableException (409 Conflict)
-- 3) 1 row  affected → RESERVATION insert
```

- 취소·거절 시 `booked_count`를 감소시킨다 (동일 트랜잭션).
- `capacity`는 좌석형(식당)/1:1형(미용실) 모두를 커버한다.
- 슬롯 행은 운영시간·휴무일 설정을 기준으로 **사전 생성**(예: 매일 새벽 배치로 향후 60일치)한다.

### 8.2 RESERVATION

```text
reservation_id
tenant_id
slot_id                  -- v2: 슬롯 참조
customer_id              -- NULL 허용 (비회원 예약)
is_guest                 -- v2: 비회원 여부 명시
reservation_date
reservation_time
party_size
reservation_status
customer_name
customer_phone           -- 암호화 저장
customer_phone_hash      -- 조회용 해시 (비회원 예약 조회)
memo
privacy_agreed_at        -- v2: 개인정보 수집 동의 시각
purge_after              -- v2: 파기 예정일
created_at
updated_at
```

### 8.3 비회원 예약 정책 (v2 신규)

MVP에서는 **비회원 예약을 허용**한다 (회원가입 강제는 전환율을 크게 떨어뜨림).

```text
- 예약 시 수집: 이름, 연락처, 인원, 날짜/시간, 요청사항
- 개인정보 수집·이용 동의 필수 (동의 시각 기록)
- customer_phone은 애플리케이션 레벨 암호화(AES-GCM) 후 저장
- 조회는 customer_phone_hash(HMAC-SHA256) + 예약번호 조합으로 수행
- 예약 완료일 기준 보유기간 경과 시 자동 파기 (배치)
```

### 8.4 예약 상태

```text
REQUESTED → CONFIRMED → COMPLETED
         ↘ REJECTED
         ↘ CANCELLED
CONFIRMED → NO_SHOW
```

상태 전이는 **서비스 계층의 상태 머신에서만 허용**하고, 임의 전이를 차단한다.

### 8.5 추후 확장

```text
직원별 예약 / 서비스별 소요시간 / 예약금·선결제
카카오 알림톡 / SMS / 구글 캘린더 연동 / 자동 리마인드
```

---

## 9. 홈페이지 구성 방식

업체별 HTML을 개별 저장하지 않고, 페이지와 섹션을 조합한다.

### SITE_PAGE

```text
page_id / tenant_id / page_name / page_slug / page_type
display_order / is_published / created_at / updated_at
```

### SITE_SECTION

```text
section_id / tenant_id / page_id / section_type
section_data_json / display_order / is_visible
created_at / updated_at
```

`section_type`은 열거형으로 고정하고, 타입별 JSON 스키마를 서버에서 검증한다.

```text
HERO / ABOUT / SERVICE_LIST / GALLERY / BOARD_PREVIEW
CONSULT_FORM / RESERVATION_CTA / MAP / CONTACT / KAKAO_LINK
```

```json
{
  "title": "정성을 다하는 맛있는식당",
  "description": "신선한 재료로 매일 준비합니다.",
  "buttonText": "예약하기",
  "buttonLink": "/reservation",
  "imageAssetId": 3021
}
```

> v2 변경: `imageUrl` 문자열 직접 저장 대신 `imageAssetId`로 `SITE_ASSET`을 참조한다.
> URL을 직접 넣으면 외부 URL 삽입(SSRF·XSS)과 에셋 추적 불가 문제가 생긴다.

---

## 10. 파일·에셋 관리 (v2 신규)

### SITE_ASSET

```text
asset_id / tenant_id / file_name / storage_key
content_type / file_size / width / height
uploaded_by / created_at
```

### 저장소 규칙

```text
버킷 경로 : s3://{bucket}/tenants/{tenantId}/{yyyy}/{MM}/{uuid}.{ext}
```

- **테넌트별 경로 격리**를 강제하고, 다운로드 시에도 `tenant_id` 소유권을 검증한다.
- 업로드는 서버가 발급한 **Pre-signed URL**로 처리한다 (백엔드 트래픽 절감).
- 허용 확장자/MIME 화이트리스트: `jpg, jpeg, png, webp, gif, pdf`
- 최대 크기: 이미지 10MB / 문서 20MB
- 업로드 직후 서버가 실제 매직 넘버를 검사한다 (확장자 위조 차단).
- 공개 에셋은 CDN 경유, 비공개 문서는 만료 시간이 있는 서명 URL로만 제공한다.

---

## 11. 로그인 및 권한

### 인증 방식

```text
이메일 + 비밀번호 로그인
→ 소속 업체 목록 조회
→ 업체 선택
→ 해당 tenant 컨텍스트의 액세스 토큰 발급
```

업체코드는 소속 식별용이며 인증 수단이 아니다.

### 권한

```text
PLATFORM_ADMIN   -- 전 테넌트 접근 (Hibernate 테넌트 필터를 활성화하지 않는다)
TENANT_OWNER     -- 자사 전체 + 직원 계정 관리
TENANT_MANAGER   -- 자사 운영 (예약/상담/게시판/사이트)
TENANT_STAFF     -- 자사 예약·상담 조회 및 처리
CUSTOMER         -- 본인 예약/상담만
```

### 토큰

```json
{
  "sub": "501",
  "tenantId": 10001,
  "role": "TENANT_OWNER",
  "exp": 1770000000
}
```

- 액세스 토큰 만료 30분, 리프레시 토큰 14일(HttpOnly Secure 쿠키).
- 요청마다 `TENANT_USER`에서 **실제 소속 여부와 status를 재검증**한다 (토큰 발급 이후 권한 회수 반영).
- 테넌트 전환은 재로그인 없이 **토큰 재발급**으로 처리한다.

---

## 12. Next.js 라우팅

```text
app/
├─ (tenant)/[tenantSlug]/
│  ├─ page.tsx
│  ├─ about/page.tsx
│  ├─ board/page.tsx
│  ├─ reservation/page.tsx
│  └─ consultation/page.tsx
├─ admin/
│  ├─ login/page.tsx
│  ├─ dashboard/page.tsx
│  ├─ reservations/page.tsx
│  └─ site-editor/page.tsx
├─ platform-admin/
└─ (system)/
   ├─ suspended/page.tsx      -- v2: SUSPENDED 안내 (503)
   └─ not-found.tsx
```

미들웨어에서 slug를 선검증한다.

```text
middleware.ts
→ 예약어 매칭 시 통과 (정적 라우트로)
→ slug 형식 검증 실패 시 404
→ 캐시에서 tenant 조회
→ status 검사 → SUSPENDED면 /suspended 리라이트(503)
→ 헤더에 x-tenant-id 주입하여 서버 컴포넌트로 전달
```

---

## 13. 커스텀 도메인 확장

### TENANT_DOMAIN

```text
domain_id / tenant_id / domain_name / domain_type
verification_status / verification_token
is_primary / ssl_status / created_at / updated_at
```

MVP 제외. 도입 시 도메인 소유 검증(TXT 레코드) → SSL 자동 발급 순서로 진행한다.

---

## 14. 초기 MVP 범위

### 고객 화면

```text
업체 메인 / 업체 소개 / 서비스 소개 / 게시판
상담 신청 / 예약 신청(중복 방지 포함) / 예약 조회(비회원) / 오시는 길
```

### 업체 관리자

```text
로그인 / 대시보드 / 기본정보 수정
홈페이지 문구·이미지 수정 / 운영시간·휴무일 설정
게시판 관리 / 상담 관리 / 예약 승인·거절
```

### 플랫폼 관리자

```text
업체 등록 / 대표 계정 생성 / slug 설정(예약어 검증)
요금제 설정 / 서비스 사용 중지(SUSPENDED)
```

### 초기 제외

```text
온라인 결제 / 카카오 알림톡 / 복잡한 권한 체계
업체별 독립 DB / 커스텀 도메인 / 다국어 / 고급 페이지 빌더
직원별 예약 배정 / 예약금
```

---

## 15. 기술 스택

### Frontend

```text
Next.js (App Router) / TypeScript / React Query / Tailwind CSS
```

### Backend

```text
Spring Boot 3.2 / Java 17 / Maven
Spring Security / JPA + Hibernate Filter / QueryDSL / JWT
스키마: JPA 엔티티 + ddl-auto=update (마이그레이션 도구 없음 — v2.2)
```

### Database / Cache / Storage

```text
MySQL 5.7.44  — 기존 서버의 신규 스키마 tenant_saas
                (RLS 미지원. 격리는 5.2 / 5.3 / 5.4 참조)
Redis         — 테넌트 메타·페이지 캐시
AWS S3 또는 Cloudflare R2
```

> **기술 부채로 명시해 둔다**: MySQL 5.7은 2023-10 EOL이다. 보안 패치가 끊겼고,
> RLS·CHECK 제약·CTE·윈도우 함수를 쓸 수 없으며, Flyway 같은 표준 도구가 지원을 끊고 있다.
> 멀티테넌트 SaaS의 격리 요구사항과 특히 상성이 나쁘다.
> **MySQL 8.0 업그레이드를 별도 과제로 등록할 것을 권한다.**

### Deployment

```text
Frontend : Vercel
Backend  : AWS / Railway / Render
Database : 기존 사내 MySQL 5.7.44 서버의 tenant_saas 스키마
```

### 아키텍처

```text
모듈형 모놀리스 (Modular Monolith)
```

> **스택 관련 참고**: 개발 인원이 2명 이하라면 Next.js 풀스택(API Routes + Prisma) 단일 앱도
> 유효한 선택지다. Next.js SSR → Spring API 왕복과 양쪽 인증 토큰 관리 비용이 사라진다.
> 본 문서는 Spring Boot 분리 구성을 기준으로 작성했으나, 팀 규모 확정 후 재검토를 권한다.

---

## 16. 백엔드 모듈

```text
com.service
├─ tenant          -- 테넌트, slug, 상태, 예약어
├─ auth            -- 인증, 토큰, 테넌트 컨텍스트
├─ user            -- 계정, 소속, 권한
├─ site            -- 페이지, 섹션, 테마
├─ board
├─ customer
├─ consultation
├─ reservation     -- 예약, 슬롯, 운영시간, 휴무일
├─ notification
├─ subscription
├─ file            -- 업로드, 서명 URL, 에셋
└─ common          -- TenantContext, 필터, 예외, 감사로그
```

모듈 내부: `controller / service / domain / repository / dto / mapper`

---

## 17. 구현 우선순위

```text
0. 공통 기반: TenantContext + Hibernate Filter + 격리 테스트(ArchUnit 포함)   ← v2 최우선
                (RLS 는 MySQL 5.7 미지원 — 5.2 / 5.4 참조)
1. 업체 등록 (slug 검증 + 예약어 블랙리스트)
2. slug → tenant 매핑 + 캐시
3. 업체별 홈페이지 출력 (페이지·섹션)
4. 업체 관리자 로그인 (소속 검증)
5. 홈페이지 정보 수정 + 캐시 무효화
6. 운영시간·휴무일 설정 → 슬롯 생성 배치
7. 예약 신청 (슬롯 선점 + 중복 방지)
8. 예약 목록 조회 / 승인·거절
9. 상담 신청·조회
10. 게시판 관리
11. 파일 업로드 (Pre-signed URL)
```

> 0번을 1번보다 먼저 두는 이유: 격리 기반 없이 업무 테이블을 만들기 시작하면
> 나중에 전 테이블에 tenant_id 격리를 소급 적용하는 대공사가 된다.
> DB 레벨 최후 방어선(RLS)이 없는 만큼, 이 순서를 어기면 되돌릴 비용이 더 크다.

---

## 18. 핵심 설계 원칙 (v2)

1. 모든 업체별 데이터에 `tenant_id`를 포함하고 `NOT NULL`로 강제한다.
2. `tenant_id` 격리는 **Hibernate 필터 + DB 계정 권한 분리 + CI 격리 테스트**로 보장한다.
   (MySQL 5.7에 RLS가 없어 DB 레벨 최후 방어선이 없다 — 5.2 참조)
3. 프론트에서 받은 `tenant_id`는 어떤 경우에도 신뢰하지 않는다.
4. URL은 `tenantSlug`로 받고 서버에서 `tenant_id`로 변환한다.
5. slug는 영소문자·숫자·하이픈만 허용하고, 예약어 블랙리스트를 등록 시점에 강제한다.
6. 초기에는 단일 DB, 단일 스키마(`tenant_saas`)를 사용한다.
7. 마이크로서비스보다 모듈형 모놀리스로 시작한다.
8. 홈페이지는 페이지와 섹션 조합으로 구성하고, 업체별 HTML을 개별 관리하지 않는다.
9. **예약 중복 방지는 MVP 필수**이며, DB 제약과 원자적 슬롯 선점으로 보장한다.
10. 개인정보(연락처)는 암호화 저장하고 보유기간과 파기 정책을 함께 설계한다.
11. 테넌트 상태(`SUSPENDED`/`CLOSED`)별 응답 동작을 명시적으로 정의한다.
12. 파일은 테넌트별 경로로 격리하고 서명 URL로만 접근시킨다.
13. slug·페이지 조회는 캐시를 경유하고, 무효화는 저장 시점에 명시적으로 수행한다.
14. 커스텀 도메인과 구독 결제를 고려해 관련 테이블을 미리 분리해 둔다.

---

## 19. 1차 목표

```text
격리 기반 구축 (Hibernate Filter + DB 계정 권한 분리 + CI 격리 테스트)
→ 업체 등록 + slug 매핑
→ 업체별 홈페이지 출력
→ 업체 관리자 로그인
→ 상담·예약 접수 (중복 방지 포함)
→ 업체 관리자에서 확인·승인
```

이 흐름이 완성되면 SaaS의 기본 골격이 완성된다.

---

## 20. 개발 전 남은 산출물

```text
1. ERD (테이블 간 FK 관계 확정)
2. 권한 매트릭스 (역할 × 리소스 × CRUD)
3. API 목록 (엔드포인트, 인증 요구사항, 요청/응답 스키마)
4. 화면 목록 및 와이어프레임
5. section_type별 JSON 스키마 정의
6. 개인정보 처리방침 및 보유기간 정책
```
