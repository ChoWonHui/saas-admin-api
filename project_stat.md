# 프로젝트 현황 (project status)

> **최종 갱신: 2026-07-15**
> 대상: `saas-admin-api` (백엔드) + `saas-admin-web` (EXPRISM Admin 콘솔)
>
> | 문서 | 용도 |
> |---|---|
> | [`설계안_v2.md`](./멀티테넌트_홈페이지_상담_예약_SaaS_설계안_v2.md) | 확정 설계 (초기설계는 PostgreSQL·RLS 전제라 **폐기**) |
> | **`project_stat.md`** (이 문서) | **누적 현황** — 지금 무엇이 되고 무엇이 안 되는가 |
> | [`WORKLOG.md`](./WORKLOG.md) | **일일 작업 일지** — 날짜별 기록 |
> | [`CLAUDE.md`](./CLAUDE.md) | 저장소 작업 규칙 |
> | [`../saas-admin-web/CLAUDE.md`](../saas-admin-web/CLAUDE.md) | **화면 개발 규칙** — 모든 화면이 이 틀을 따른다 |

---

## 1. 한 줄 요약

**관리자 콘솔(EXPRISM Admin)이 브라우저에서 동작한다.** 내부 직원이 **사번**으로 로그인해
관리자 계정을 생성·수정·퇴사처리하고, 업체를 조회할 수 있다. nginx 가 정적 파일과 `/api` 를 함께 서빙한다.

설계안 §17 우선순위의 **1번(업체 등록)까지 완료**. 다만 **0번(테넌트 격리 기반)은 아직 없다.**

Java 파일 66개. 테스트 0개.

---

## 2. 전체 진행률

| 영역 | 상태 | 비고 |
|---|---|---|
| DB 스키마 | ✅ 완료 | 테이블 **17개** + 시드 (2026-07-15 에 관리자 3개 추가) |
| 스키마 관리 방식 | ⚠️ 변경됨 | **Liquibase 걷어냄 (2026-07-14).** 이제 JPA 엔티티 + `ddl-auto: update` |
| **관리자 계정 (사번 체계)** | ✅ 완료 | `admin_account`. 생성/조회/수정/**퇴사처리(소프트 삭제)**/비밀번호 초기화 |
| **관리자 인증 (사번 로그인)** | ✅ 완료 | `/api/auth/admin/*`. 토큰 회전 / 계정 잠금 / **강제 비밀번호 변경** |
| **관리자 콘솔 (프론트)** | ✅ 완료 | `saas-admin-web` (React+Vite) + nginx. 로그인·관리자 CRUD·업체 목록 |
| 업체 등록·조회·개설·중지 | ✅ 완료 (API) | slug 3중 검증 포함. **화면은 목록만** |
| 업체 사용자 로그인 (2단계) | ✅ 완료 (API) | 소속 목록 → 업체 선택 → 테넌트 토큰. 콘솔에서는 쓰지 않는다 |
| 감사 로그 | 🔶 부분 | 로그인/업체등록/관리자 로그인. before/after 스냅샷 미구현 |
| Swagger (springdoc) | ✅ 완료 | JWT 인증 스킴 등록. **운영에서는 꺼야 함** |
| **테넌트 격리 기반 (§17-0)** | ❌ 미착수 | **가장 중요한 미구현 항목. 아래 §7 참조** |
| 테스트 | ❌ 없음 | `src/test` 가 비어 있다. 검증은 Playwright 수동 스크립트로만 했다 |
| 업체 관리 화면 (등록·개설·중지) | ❌ 미착수 | API 는 있다. 화면만 붙이면 된다 |
| 고객용 API (사이트/예약/상담/게시판) | ❌ 미착수 | |
| 캐싱 (Caffeine + Redis) | ❌ 미착수 | 설계안 §6 |
| 파일 업로드 (S3 Pre-signed) | ❌ 미착수 | 설계안 §10 |

---

## 2-A. 관리자 계정 체계 (2026-07-15 신설) — 반드시 알고 있어야 할 것

**내부 직원과 업체 사용자는 테이블이 다르다.** 식별자 체계가 다르기 때문이다.

| | 내부 직원 (관리자) | 업체 사용자 |
|---|---|---|
| 테이블 | `admin_account` | `user_account` |
| **PK** | **`emp_no` (사번, CHAR(6))** | `user_id` (BIGINT) |
| 로그인 ID | **사번** — 260001 | 이메일 |
| 로그인 API | `POST /api/auth/admin/login` | `POST /api/auth/login` |
| 토큰 테이블 | `admin_refresh_token` | `refresh_token` |

```text
사번 = YY + 4자리 순번    260001 = 2026년 1번째 입사자
채번 = employee_no_seq 행을 SELECT … FOR UPDATE 로 잠그고 +1
       (MAX+1 방식은 동시 생성 시 같은 번호를 발급한다)
```

**이 두 가지가 깨지면 참조 무결성이 깨진다.**
1. **사번은 절대 바뀌지 않는다** — `@Column(updatable = false)`. Hibernate 가 UPDATE 문에 싣지 않는다.
2. **사번은 재사용하지 않는다** — 퇴사자 사번을 재발급하면 그 사람의 감사 기록·토큰이 새 사람에게 붙는다.
   퇴사처리를 물리 삭제가 아닌 **소프트 삭제(`is_deleted='Y'`)** 로 하는 이유가 이것이다.

**JWT 에 `subjectType`(ADMIN/USER)이 실린다.** 두 테이블의 키가 다르므로, 이걸 보지 않으면 엉뚱한 사람을 가리킨다.
관리자 권한(`ROLE_PLATFORM_ADMIN`)은 **`subjectType=ADMIN` 이고 비밀번호를 바꾼 토큰에만** 부여된다.

**비밀번호는 만드는 사람이 정하지 않는다.**
생성·초기화 모두 `exprism1234!` 고정 → 첫 로그인 시 **강제 변경** → 변경 후 **모든 토큰 폐기 → 재로그인**.
기본 비밀번호 상태의 토큰은 `mustChangePassword=true` 라 `/api/platform-admin/**` 이 **403** 이다.
화면만 막는 것이 아니라 **서버가 막는다.**

> 기존 `user_account` 의 `jsj3216@gmail.com`(user_id=3)은 **DISABLED** 로 비활성화했다.
> 그 경로로는 이제 관리자 권한이 나오지 않는다.

---

## 2-B. 관리자 콘솔 (`saas-admin-web`) — 2026-07-15 신설

**EXPRISM Admin** — *We express your vision through innovation.*
React 18 + Vite. **아직 git 저장소가 아니다** (버전관리 필요).

```text
브라우저 → nginx :8080 ┬─ /       → saas-admin-web/dist   (React 빌드)
                       └─ /api/*  → Spring Boot :8089
```

정적 파일과 API 가 **같은 오리진**이라 CORS 설정이 아예 없다. 백엔드는 손대지 않았다.

```powershell
.\tools\nginx.ps1 start | stop | reload | status     # → http://localhost:8080
cd saas-admin-web; npm run dev                       # 개발(:5173, HMR)
cd saas-admin-web; npm run build; cd ..; .\tools\nginx.ps1 reload
```

**모든 화면은 [`saas-admin-web/CLAUDE.md`](../saas-admin-web/CLAUDE.md) 의 틀을 따른다.**
그리드(헤더 최소폭·표 내부 가로스크롤) / **"관리" 버튼 열 없음** / 더블클릭=수정 / 우클릭=메뉴 /
파괴적 동작은 대상을 명시한 확인창 / 용어는 "퇴사처리"(≠삭제).

---

## 3. 실행 환경 (실제 확인된 값)

### 3.1 JDK — `mvnw` 를 직접 쓰면 안 된다

Spring Boot 3.2 는 Java 17 이 최소 요구사항이라 Java 8 로는 **기동은커녕 빌드도 안 된다.**

**⚠️ 2026-07-15 에 바뀐 사실**: `C:\SHIS\jdk-17` 은 **더 이상 존재하지 않는다.** 이 경로를 하드코딩하던
`mvnw17.cmd` 와 `tools/db.ps1` 이 둘 다 죽었다. 지금은 **후보 경로를 순서대로 탐색**한다.
시스템 `JAVA_HOME` 도 이제 **JDK 17** 이다 ("시스템은 Java 8" 이라는 옛 전제는 더 이상 사실이 아니다).

| | 경로 | 상태 |
|---|---|---|
| JDK 17 | `C:\Program Files\Java\jdk-17` | **현재 사용 중.** 시스템 `JAVA_HOME` 도 여기다 |
| JDK 17 (옛 위치) | `C:\SHIS\jdk-17` | 사라짐. 스크립트가 여기부터 찾고, 없으면 위 경로로 넘어간다 |

> 다른 PC 라면 `.env` 나 환경변수에 `JAVA17_HOME` 을 지정하면 그쪽이 우선한다.

**기동 방법**

```powershell
.\run.ps1          # → http://localhost:8089
```

`run.ps1` / `mvnw17.cmd` 가 자기 프로세스 안에서만 `JAVA_HOME` 을 17 로 덮어쓰고 `mvnw` 에 위임한다.
**`.\mvnw` 를 직접 부르면 안 된다.**

### 3.1-A 접속 정보 — `.env` 를 쓰지 않는다 (2026-07-15, 사용자 결정)

**`application.yml` 에 DB 접속정보와 JWT 서명키를 직접 적는다.** `spring.config.import`(`.env` 로딩)는 제거했다.
어떤 PC / 어떤 경로 / 어떤 IDE 에서 실행하든 **아무 설정 없이 그냥 뜨게** 하기 위함이다.
`.env` 가 없어도, 작업 디렉터리가 어디든 기동된다. (검증: `.env` 를 치우고 저장소 루트에서 기동 성공)

> 🔴 **대가**: 비밀번호와 서명키가 **커밋되는 파일**에 들어갔다. 푸시하면 GitHub 에 그대로 공개된다.
> 이 위험을 명확히 알린 뒤 사용자가 선택한 것이다. §8 기술 부채의 최상단 항목이다.
>
> 옛 함정 (이제는 해당 없음): `optional:file:./.env` 의 `./` 는 **JVM 작업 디렉터리 기준**이라,
> IDE 가 작업 디렉터리를 저장소 루트로 잡으면 `.env` 를 조용히 건너뛰고
> `Could not resolve placeholder 'SAAS_JWT_SECRET'` 로 죽었다.

**포트가 이미 물려 있을 때** (`Port 8089 was already in use`)

```powershell
Get-NetTCPConnection -LocalPort 8089 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

### 3.1-b Swagger

```text
http://localhost:8089/swagger-ui.html      Swagger UI
http://localhost:8089/v3/api-docs          OpenAPI JSON
```

로그인 → `accessToken` 복사 → 우측 상단 **Authorize** 에 붙여넣으면 관리자 API 를 바로 호출할 수 있다.
`persist-authorization: true` 라 새로고침해도 토큰이 유지된다.

> **⚠️ 운영에서는 꺼야 한다.** API 구조·스키마·검증 규칙이 그대로 노출된다.
> `SAAS_SWAGGER_ENABLED=false` 로 끄면 `/swagger-ui`, `/v3/api-docs` 가 모두 404 가 된다.

### 3.1-c DB 접속 — `tools\db.ps1`

**이 PC 에는 `mysql` CLI 가 없다.** 매번 Java 파일을 새로 짜지 말고 이 도구를 쓴다.

```powershell
.\tools\db.ps1 "SHOW TABLES"
.\tools\db.ps1 "SELECT * FROM tenant"
.\tools\db.ps1 "ALTER TABLE tenant MODIFY contact_phone VARCHAR(30); DESC tenant"
.\tools\db.ps1 -File tools\sql\작업.sql
.\tools\db.ps1 -Admin "GRANT ..."          # 계정·권한 작업 (SAAS_ROOT_* 필요)
```

`.env` UTF-8 로딩 / JDBC 드라이버 확보 / UTF-8 출력을 전부 자동 처리한다. 한글이 깨지지 않는다.
기본 계정 `saas_app` 은 `tenant_saas` 에 DDL 포함 전 권한이 있어 `ALTER TABLE` 도 그냥 된다.

`ddl-auto: update` 로 안 되는 작업(타입 변경 / 컬럼 삭제 / 제약 추가 / STORED 생성 컬럼)은
전부 이 도구로 처리한다. 자세한 예시는 [`CLAUDE.md`](./CLAUDE.md) §0-A.

### 3.2 DB — 이미 구축되어 있다

```text
MySQL 5.7.44 @ 3.34.129.32:3306
└─ tenant_saas   (utf8mb4 / utf8mb4_unicode_ci)  ← 이 프로젝트
└─ cwh           (기존 food-biz)                  ← 이 프로젝트에서 접근 불가
```

- 업무 테이블 **14개**. Liquibase 이력 테이블(`DATABASECHANGELOG*`)은 도구를 걷어내며 삭제했다.
- 시드 완료: 예약어 slug 37건 / 역할 5종 / 권한 23종 / 역할-권한 45건 / 요금제 3종.
- `sql_mode` 에 `STRICT_TRANS_TABLES` 확인 — 설계안이 노린 **ENUM 의 CHECK 대체 효과가 실제로 작동**한다.
- FK 8개 / 유니크 제약 10개 / STORED 생성 컬럼 3개가 **전부 유지되고 있다.**
  (Liquibase 시절에 만들어진 것이고, `ddl-auto: update` 는 기존 테이블을 건드리지 않는다)

**계정 권한 (실측 확인)**

| 계정 | 권한 | 검증 결과 |
|---|---|---|
| `saas_app` | `tenant_saas.*` ALL | DDL 가능(테이블 생성/삭제 성공). `SHOW DATABASES` → `information_schema`, `tenant_saas` 만 보임. **`cwh` 는 보이지도 않음** |
| `saas_migrate` | `tenant_saas.*` ALL | Liquibase 전용이었으나 **현재 미사용** |

> `saas_app` 이 DDL 권한을 갖게 된 것은 `ddl-auto: update` 가 테이블을 만들어야 하기 때문이다.
> 설계안 §5.2 는 런타임 계정의 DDL 을 막아 두려 했으나, 마이그레이션 도구를 걷어내면서 불가피하게 열었다.
> **범위는 `tenant_saas` 로 한정**돼 있어 크로스 DB 격리는 그대로다.

접속 정보는 `.env` (gitignore 대상). MySQL 비밀번호 정책은 `MEDIUM` (8자+대소문자+숫자+특수문자).

### 3.3 부트스트랩된 플랫폼 관리자

```text
user_id = 3 / jsj3216@gmail.com / is_platform_admin = 1
```

`SAAS_ADMIN_BOOTSTRAP=true` 로 1회 생성 후 `false` 로 되돌려 놓았다.
비밀번호 해시를 마이그레이션 SQL 에 하드코딩하지 않으려고 환경변수로 주입받는 구조다.

---

## 4. 구현된 것 — 설계안 대응표

### 4.1 인증 (설계안 §11)

| 설계 항목 | 구현 |
|---|---|
| 이메일+비밀번호 로그인 | `POST /api/auth/login` |
| 소속 업체 목록 조회 → 업체 선택 → 테넌트 토큰 | `login` 이 `memberships[]` 반환 → `POST /api/auth/select-tenant` |
| 액세스 30분 / 리프레시 14일 | `application.yml` 의 `jwt.*` |
| 리프레시 토큰은 해시만 저장 | SHA-256 hex, `refresh_token.token_hash` |
| 매 요청 `TENANT_USER` 에서 소속 재검증 | `select-tenant` / `refresh` 에서 재조회 |
| 프론트가 보낸 `tenantId` 불신 | `tenant_user` 로 항상 재검증 |

**추가 구현 (스키마가 요구하던 것)**: 연속 로그인 실패 5회 → 15분 계정 잠금 (`login_fail_count`, `locked_until`).

**리프레시 토큰 회전**: 사용한 리프레시 토큰은 즉시 폐기하고 새 토큰을 발급한다.

### 4.2 업체 등록 (설계안 §2, §14, §17-1)

`POST /api/platform-admin/tenants` 한 번에 **업체 + 대표 계정 + 구독**을 한 트랜잭션으로 생성한다.

- 업체는 `PENDING` 으로 시작한다. 관리자가 명시적으로 개설해야 고객 화면에 노출된다.
- 대표 계정은 `role_id = 2` (TENANT_OWNER) 로 `tenant_user` 에 소속시킨다.
- 업체 코드는 `SHOP0001` 형식으로 자동 생성한다.

**slug 검증 — 등록 시점에 3중으로 막는다 (설계안 §2.2)**

| 상황 | 응답 |
|---|---|
| 형식 위반 (한글 / 연속 하이픈 / 3자 미만 / 하이픈 시작·종료) | `400 SLUG_INVALID_FORMAT` |
| 예약어 (`admin`, `api`, `login` … 37건) | `409 SLUG_RESERVED` |
| 이미 사용 중 | `409 SLUG_DUPLICATED` |

### 4.3 테넌트 상태 (설계안 §4.1)

`PENDING → ACTIVE → SUSPENDED → CLOSED` 전이를 **엔티티 안의 상태 머신에서만** 허용한다.
임의 전이는 `409 INVALID_STATUS_TRANSITION` 으로 막는다.

- `activate()` — PENDING/SUSPENDED 에서만 가능. `opened_at` 최초 1회 기록.
- `suspend()` — ACTIVE 에서만 가능. 고객 화면은 404 가 아니라 **503** (검색 색인 유지 목적).
- `close()` — `purge_after = 오늘 + 90일` 기록.

### 4.4 API 목록

전부 Swagger 에 문서화돼 있다 → `http://localhost:8089/swagger-ui.html`

| 메서드 | 경로 | 인증 |
|---|---|---|
| POST | `/api/auth/login` | 없음 |
| POST | `/api/auth/refresh` | 없음 |
| POST | `/api/auth/select-tenant` | 필요 |
| POST | `/api/auth/logout` | 필요 |
| GET | `/api/auth/me` | 필요 |
| POST | `/api/platform-admin/tenants` | `PLATFORM_ADMIN` |
| GET | `/api/platform-admin/tenants` | `PLATFORM_ADMIN` |
| GET | `/api/platform-admin/tenants/{id}` | `PLATFORM_ADMIN` |
| POST | `/api/platform-admin/tenants/{id}/activate` | `PLATFORM_ADMIN` |
| POST | `/api/platform-admin/tenants/{id}/suspend` | `PLATFORM_ADMIN` |

### 4.5 패키지 구조

```text
com.saas.admin
├─ auth          -- 인증, JWT, 계정, 소속, 부트스트랩
│  ├─ domain     UserAccount / Role / TenantUser / RefreshToken
│  ├─ jwt        JwtTokenProvider / JwtAuthenticationFilter / AuthPrincipal
│  ├─ repository
│  └─ dto
├─ tenant        -- 업체, slug, 요금제, 구독
│  ├─ domain     Tenant / TenantPlan / ReservedSlug / TenantSubscription / SlugPolicy
│  ├─ repository
│  └─ dto
└─ common
   ├─ audit      AuditLog / AuditService
   ├─ config     SecurityConfig
   └─ error      ErrorCode / ApiException / GlobalExceptionHandler
```

> 설계안 §16 은 패키지를 `com.service` 로 적었으나 실제는 `com.saas.admin` 이다.
> 모듈형 모놀리스로 확장하면 `admin` 아래에 고객용 예약 API 가 들어가는 어색한 구조가 되므로,
> 코드가 더 늘기 전에 `com.saas` 로 정리할 것을 권한다. (§8 참조)

---

## 5. 실제 서버에서 검증한 결과

코드만 작성한 게 아니라 **실제 MySQL 에 붙여 앱을 띄우고 API 를 호출한 뒤, DB 행을 직접 조회해 확인했다.**

| 검증 | 결과 |
|---|---|
| Hibernate `ddl-auto: update` 기동 | ✅ 기존 스키마를 그대로 인정. `ALTER`/`DROP` **단 한 줄도 실행 안 함** |
| 관리자 로그인 | ✅ JWT 발급 |
| 틀린 비밀번호 | ✅ `401 INVALID_CREDENTIALS` |
| 토큰 없이 관리자 API | ✅ `401` 차단 |
| 업체 등록 | ✅ `SHOP0001` / `delicious` / `PENDING` |
| 예약어 slug `admin` | ✅ `409 SLUG_RESERVED` |
| 한글 slug / 연속 하이픈 | ✅ `400 SLUG_INVALID_FORMAT` |
| slug 중복 | ✅ `409 SLUG_DUPLICATED` |
| 서비스 개설 | ✅ `PENDING → ACTIVE` |
| **업체 대표가 플랫폼 관리자 API 접근** | ✅ `403` 차단 |
| 업체 선택 → 테넌트 컨텍스트 토큰 | ✅ `roleCode=TENANT_OWNER` |
| 한글 저장 | ✅ `tenant_name` HEX = `EBA79BEC9E88EB8A94EC8B9DEB8BB9` (= `맛있는식당`) |

**DB 생성 컬럼 제약이 실제로 작동함을 확인**

- `tenant_user.owner_marker = 1` → 테넌트당 대표(TENANT_OWNER) 1명 강제
- `tenant_subscription.active_marker = 1` → 테넌트당 ACTIVE 구독 1건 강제

**감사 로그 적재 확인**: `LOGIN_SUCCESS`, `LOGIN_FAIL`, `TENANT_CREATE`, `TENANT_ACTIVATE`

### 5.1 관리자 콘솔 — 브라우저(Playwright)로 실제 화면을 밟아 검증 (2026-07-15)

API 호출만이 아니라 **브라우저를 띄워 클릭하고, DB 행을 다시 조회해** 확인했다.

| 검증 | 결과 |
|---|---|
| 사번 로그인 (nginx :8080 경유) | ✅ CORS 설정 없이 동작 (같은 오리진) |
| 관리자 생성 → 사번 자동 채번 | ✅ 260002 → 260003 … 순번 증가 |
| 더블클릭 → 수정 창 / 우클릭 → 메뉴 | ✅ (관리 버튼 열 없음) |
| 퇴사처리 확인창 | ✅ **"260006 홍길동님을 퇴사처리 하시겠습니까?"** — 대상이 찍힌다 |
| 퇴사처리 → 리프레시 토큰 폐기 | ✅ DB 확인: 해당 사번의 살아있던 토큰 2건이 즉시 `revoked` |
| 퇴사한 계정으로 로그인 | ✅ 차단 |
| 자기 자신 퇴사처리 / 중복 퇴사처리 / 마지막 관리자 | ✅ 전부 `409` 로 차단 |
| 초기 비밀번호(`exprism1234!`) 로그인 | ✅ 강제 변경 화면으로. **`/admins` 로 우회해도 튕겨나옴** |
| 신규/확인 불일치 | ✅ 경고 + 변경 버튼 비활성 |
| 변경 완료 | ✅ 자동 로그인 안 됨 → **"변경된 비밀번호로 로그인하세요"** → 새 비밀번호로 재로그인 성공 |
| 그리드 785px (좁은 창) | ✅ 헤더가 세로로 쪼개지지 않고, **페이지는 가로로 밀리지 않는다** (표만 스크롤) |
| API 15개 시나리오 | ✅ 전부 통과 |

> 검증은 **Playwright 수동 스크립트**로 했다. `src/test` 는 여전히 비어 있다 — 자동화된 회귀 테스트가 없다.

---

## 6. 알아둬야 할 함정 (다시 밟지 말 것)

### 6.1 기존 테이블의 ENUM / CHAR 컬럼

기존 14개 테이블은 손으로 만들어져 `ENUM` 과 `CHAR(n)` 을 쓴다.
JDBC 드라이버가 이들을 `Types#CHAR` 로 보고하는데 Java 의 `String` / `enum` 은 기본이 `VARCHAR` 다.

`ddl-auto: validate` 를 쓰던 시절엔 이 불일치로 **앱이 기동조차 못 했다.**

```text
Schema-validation: wrong column type encountered in column [token_hash] in table [refresh_token];
found [char (Types#CHAR)], but expecting [varchar(64) (Types#VARCHAR)]
```

→ `@JdbcTypeCode(SqlTypes.CHAR)` 로 해결했다. **지금도 그대로 두어야 한다.**
`validate` 는 껐지만, 타입이 어긋나면 바인딩이 깨지기 때문이다.

```java
@Enumerated(EnumType.STRING)
@JdbcTypeCode(SqlTypes.CHAR)   // 기존 ENUM 컬럼용
@Column(name = "status", nullable = false)
private TenantStatus status;
```

> **⚠️ 앞으로 Hibernate 가 새로 만드는 테이블에는 붙이지 않는다.**
> Hibernate 는 enum 을 `VARCHAR(255)` 로 만든다.
> **기존 테이블 = `CHAR`, 신규 테이블 = `VARCHAR`** 로 갈린다는 점을 기억할 것.

### 6.2 STORED 생성 컬럼은 매핑하지 않는다

`tenant_user.owner_marker`, `tenant_subscription.active_marker`, `tenant_domain.primary_marker` 는
DB 가 계산하는 생성 컬럼이다. **엔티티에 매핑하면 Hibernate 가 INSERT 를 시도해 깨진다.**

### 6.3 PowerShell 5.1 의 한글 인코딩 (개발 중 실제로 데이터가 깨졌던 건)

| 증상 | 원인 | 대응 |
|---|---|---|
| 저장된 값이 `?????` | `Invoke-RestMethod` 가 문자열 `-Body` 를 **ASCII** 로 인코딩해 전송 | UTF-8 바이트로 직접 넘긴다 |
| 응답이 `ë§ìë...` | PS 가 응답을 **ISO-8859-1** 로 해석 (표시만 깨짐, 데이터는 정상) | 무시하거나 콘솔 인코딩 설정 |
| `.env` 의 한글이 깨진 채 주입 | `Get-Content` 기본값이 **ANSI(CP949)** | `-Encoding UTF8` 필수 |

```powershell
# 한글이 포함된 요청은 이렇게 보내야 한다
$bytes = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json))
Invoke-RestMethod -Uri $url -Method Post -Body $bytes `
    -ContentType "application/json; charset=utf-8" -Headers $H
```

> **애플리케이션은 처음부터 정상이었다.** 깨진 것은 전부 PowerShell 클라이언트 쪽이었다.
> `.cmd` 배치 파일도 마찬가지 — `cmd.exe` 가 UTF-8 을 CP949 로 잘못 읽으므로 `mvnw17.cmd` 는 **ASCII 전용**으로 작성했다.

### 6.4 스키마를 바꿀 때 — `ddl-auto: update` 는 "추가" 만 한다

**엔티티만 고치고 앱을 다시 띄우면 된다.** Hibernate 가 없는 테이블·컬럼을 만든다.

그러나 `update` 는 **추가만 한다.** 아래는 **조용히 무시하고 아무 에러도 내지 않는다.**

```text
컬럼 타입 변경 / 컬럼 삭제 / 이름 변경 / 제약 추가·삭제
```

엔티티에서 `String` 을 `int` 로 바꿔도 DB 는 그대로다. 앱은 정상 기동하고, 런타임에 이상하게 깨진다.
이런 변경이 필요하면 **DB 에 직접 `ALTER TABLE` 을 실행해야 한다.**
(`validate` 를 쓰던 시절엔 이런 불일치를 기동 시점에 잡아줬으나, 지금은 감지 수단이 없다.)

**🚨 새로 만드는 테이블에는 제약이 안 붙는다**

| 필요한 것 | Hibernate 로 가능한가 |
|---|---|
| 유니크 제약 | ✅ `@Table(uniqueConstraints = @UniqueConstraint(...))` |
| 외래키 | ✅ 단 `@ManyToOne` 을 써야 한다. `Long tenantId` 스타일이면 안 생긴다 |
| **부분 유니크** ("테넌트당 1건") | ❌ **불가능** |

부분 유니크가 불가능한 이유: MySQL 5.7 에는 부분 유니크 인덱스가 없어 **STORED 생성 컬럼**으로
우회해야 하는데, Hibernate 는 그 개념 자체를 모른다.
→ **애플리케이션 코드에서 막아야 하고, 동시 요청에는 뚫린다.**
정말 DB 레벨 강제가 필요하면 `ALTER TABLE` 로 직접 붙인다.

**기존 테이블의 제약은 살아 있다**

`update` 가 기존 테이블을 건드리지 않기 때문이다. Liquibase 를 걷어낸 뒤 앱을 띄웠을 때
Hibernate 는 `ALTER` / `DROP` 을 **단 한 줄도 실행하지 않았다** (기동 로그로 확인).

```text
FK 8개 / 유니크 제약 10개 / STORED 생성 컬럼 3개  →  전부 유지됨
  tenant_user.owner_marker           테넌트당 대표(TENANT_OWNER) 1명
  tenant_subscription.active_marker  테넌트당 ACTIVE 구독 1건
  tenant_domain.primary_marker       테넌트당 대표 도메인 1개
```

### 6.5 `role_id = 2` 하드코딩

`tenant_user.owner_marker` 생성 컬럼이 `role_id = 2` (TENANT_OWNER) 를 **SQL 안에 하드코딩**한다.
`004` 시드의 `role_id` 고정값과 짝을 이룬다. **역할 시드를 수정하면 "테넌트당 대표 1명" 제약이 조용히 깨진다.**
Java 쪽에서는 `Role.TENANT_OWNER_ID` 상수로 이 의존을 명시해 두었다.

---

## 7. 가장 중요한 미구현 — 테넌트 격리 기반 (설계안 §17-0)

**MySQL 5.7 에는 Row Level Security 가 없다. DB 레벨 최후 방어선이 없다.**
따라서 아래 3단 방어 중 **현재 작동하는 것은 2번뿐**이다.

| 방어선 | 설계안 | 현재 상태 |
|---|---|---|
| 1차 — 애플리케이션 (Hibernate `@Filter`) | §5.1 | ❌ **없음** |
| 2차 — DB 계정 권한 분리 | §5.2 | ✅ 작동 확인 (단, **DB 간 격리이지 테넌트 간 격리가 아니다**) |
| 3차 — CI 격리 테스트 / ArchUnit | §5.4 | ❌ **없음** (`src/test` 가 비어 있다) |

지금은 업무 테이블(예약·상담·게시판·고객)이 아직 없어서 **당장 유출될 데이터가 없다.**
그러나 설계안 §17 이 0번을 1번보다 앞에 둔 이유가 바로 이것이다.

> 0번을 1번보다 먼저 두는 이유: 격리 기반 없이 업무 테이블을 만들기 시작하면
> 나중에 전 테이블에 tenant_id 격리를 소급 적용하는 대공사가 된다.

**업무 테이블을 만들기 전에 아래를 먼저 구축해야 한다.**

```text
1. TenantContext (ThreadLocal) + 요청 인터셉터
2. BaseTenantEntity + Hibernate @FilterDef / @Filter
3. 세션마다 enableFilter("tenantFilter") 활성화
4. ArchUnit: 업무 엔티티는 반드시 BaseTenantEntity 상속
5. ArchUnit: Repository 밖에서 EntityManager / 네이티브 쿼리 직접 사용 금지
6. 통합 격리 테스트: 테넌트 A 컨텍스트에서 테넌트 B 데이터 조회 시 0건
```

pom.xml 에 ArchUnit(1.2.1) 의존성은 이미 들어가 있으나 **테스트 코드는 한 줄도 없다.**

---

## 8. 기술 부채 / 정리 필요 항목

| 항목 | 내용 | 시급도 |
|---|---|---|
| 🔴 **JWT 서명키가 예시값** | `application.yml` 의 `jwt.secret` 이 `change-me-to-a-random-32byte-or-longer-secret-value` 그대로다. **이 값을 아는 사람은 사번·비밀번호 없이 관리자 토큰을 위조**해 전 API 를 호출한다. 랜덤 32바이트 이상으로 즉시 교체할 것 | **매우 높음** |
| 🔴 **비밀 정보가 커밋 대상 파일에** | `application.yml` 에 DB 비밀번호(`saas_app`)와 JWT 서명키가 평문. 이 저장소는 GitHub(`ChoWonHui/saas-admin-api`)로 푸시된다. DB 는 공인 IP 에 `@'%'` 로 열려 있다. **한 번 푸시되면 커밋 이력에 영구히 남는다** (사용자가 위험을 알고 선택한 구성) | **매우 높음** |
| 관리자 비밀번호가 소스에 | `LoginRequest.java` 의 Swagger `example` 에 실제 비밀번호가 평문으로 박혀 있다. 더미로 교체 + 비밀번호 변경 필요 | 높음 |
| **`saas-admin-web` 이 git 밖** | 프론트엔드 전체가 버전관리되지 않는다. 별도 저장소로 낼지, 백엔드 저장소에 포함할지 정할 것 | 높음 |
| **테스트 0개** | `src/test` 가 비어 있다. 설계안 §5.4 는 "선택이 아니라 필수" 라고 못 박았다. 검증은 Playwright 수동 스크립트로만 했다 | 높음 |
| **격리 기반 부재** | §7 참조 | 높음 |
| 테스트 데이터 잔여 | `admin_account` 8건 중 **6건이 검증 잔여물** (260002·260004·260005·260006 퇴사처리됨 / 260003 중지 / 260007 검증용 / 260008). 정리 + 채번(`employee_no_seq`) 초기화 필요 | 중간 |
| 260001 비밀번호 미상 | 사용자가 콘솔에서 직접 변경했다. 필요하면 다른 관리자로 로그인해 **비밀번호 초기화**(→ `exprism1234!`) 하면 된다 | 낮음 |
| **운영 DB 비밀번호 평문 노출** | `food-biz-api/README.md` 에 `ALL PRIVILEGES WITH GRANT OPTION` 계정(`jsj3216`)의 비밀번호가 평문으로 적혀 있다. 원격 저장소에 올라가 있다면 커밋 이력 정리 + 비밀번호 교체 필요 | 높음 |
| **마이그레이션 도구 부재** (사용자 결정) | Liquibase 를 걷어냈다. 대가: ① 새 테이블에 부분 유니크 제약을 걸 수 없다 ② 스키마 드리프트를 감지할 수단이 없다(`validate` 를 껐으므로) ③ 새 환경(스테이징/CI 테스트 DB)은 앱을 띄워서 만들어야 한다 ④ 롤백 수단이 없다 | 감수함 |
| 런타임 계정 DDL 권한 | `saas_app` 이 `tenant_saas` 에 DDL 권한을 갖는다. 설계안 §5.2 가 막아두려던 것이나 `ddl-auto: update` 때문에 불가피하게 열었다. 애플리케이션 버그가 스키마를 바꿀 수 있다 | 감수함 |
| 패키지명 `com.saas.admin` | 모듈형 모놀리스로 가면 `com.saas` 가 맞다. 코드가 더 늘기 전에 정리 | 중간 |
| 감사 로그 JSON 컬럼 미매핑 | `before_json` / `after_json` 을 매핑하지 않았다. 변경 전후 스냅샷이 필요해지면 매핑 | 중간 |
| `ej.pem` | 36바이트짜리 깨진 파일 (`decode blob failed: invalid format`). SSH 키가 아니다. 삭제 필요 | 낮음 |
| MySQL 5.7 자체 | 2023-10 EOL. 보안 패치 없음. RLS·CHECK·CTE·윈도우 함수 불가 | 별도 과제 |
| Redis / Caffeine 캐시 | 설계안 §6. slug → tenant_id 조회가 매 요청 DB 히트 | 나중 |

---

## 9. 다음 단계 (권장 순서)

```text
0-A. 🔴 JWT 서명키 교체 + 비밀 정보 정리     ← 다른 무엇보다 먼저. 지금은 토큰 위조가 가능하다
0-B. 테스트 데이터 정리 (260002~260008) + 채번 초기화

0. 테넌트 격리 기반 구축          ← 업무 테이블 만들기 전에 반드시
   TenantContext + BaseTenantEntity + Hibernate @Filter + ArchUnit + 격리 테스트

1. 현재 구현분의 테스트 작성
   로그인 / 계정 잠금 / slug 검증 / 권한 분리(403) / 상태 전이 / 사번 채번 동시성

2. 업체 관리 화면 (등록·개설·중지) — API 는 이미 있다. 관리자 화면과 같은 틀로

3. slug → tenant 매핑 + 캐시 (설계안 §6)

4. 업체 관리자 API
   자사 정보 조회·수정 / 직원 계정 관리

5. 홈페이지 (site) — 페이지·섹션 구조

6. 운영시간·휴무일 → 슬롯 생성 배치 → 예약 (슬롯 선점 + 중복 방지)
```

---

## 10. 파일 변경 이력 (**전부 아직 미커밋**)

날짜별 상세 경위는 [`WORKLOG.md`](./WORKLOG.md) 를 본다.

### 2026-07-15 작업분

**새로 만든 것 (백엔드 15개 파일)**

```text
src/main/java/com/saas/admin/adminaccount/         관리자 계정 (사번 체계) — 14개
  domain/       AdminAccount(PK=emp_no) / AdminStatus / EmployeeNoSeq / AdminRefreshToken
  repository/   AdminAccountRepository / EmployeeNoSeqRepository / AdminRefreshTokenRepository
  dto/          AdminDtos
  EmployeeNoService      사번 채번 (SELECT … FOR UPDATE)
  AdminAuthService       사번 로그인 / 토큰 회전 / 로그아웃
  AdminAccountService    CRUD / 퇴사처리(소프트 삭제) / 비밀번호 초기화·변경
  AdminAuthController    /api/auth/admin/*
  AdminAccountController /api/platform-admin/admins
  AdminAccountBootstrap  최초 관리자 1명 (닭과 달걀 해소)
src/main/java/com/saas/admin/auth/jwt/SubjectType.java   ADMIN / USER 구분
```

**새로 만든 것 (프론트 + 도구 — git 밖)**

```text
saas-admin-web/            EXPRISM Admin 콘솔 (React 18 + Vite)
  src/api/client.js        토큰 보관 + 401 자동 재발급. 화면은 fetch 를 직접 부르지 않는다
  src/auth/AuthContext.jsx 로그인 / 비밀번호 변경 / 재로그인 안내
  src/components/Shell.jsx 상단바 + 내비게이션
  src/pages/               LoginPage(사번) / PasswordChangePage(강제변경) / AdminsPage / TenantsPage
  src/index.css            스타일 전부 (프레임워크 없음)
  CLAUDE.md                🚨 화면 개발 규칙 — 모든 화면이 이 틀을 따른다
tools/nginx.ps1            nginx start|stop|reload|status
tools/nginx-1.28.0/        nginx (conf/saas-admin.conf: :8080 정적 + /api → :8089)
```

**고친 것**

```text
application.yml            .env 로딩 제거 → 접속정보·JWT 하드코딩. 관리자 부트스트랩 설정 추가
AuthPrincipal              subjectType / empNo / mustChangePassword 추가
JwtTokenProvider           관리자 토큰(subject=사번) 발급. 클레임 3개 추가
JwtAuthenticationFilter    ADMIN 토큰 + 비밀번호 변경 완료 상태만 ROLE_PLATFORM_ADMIN
SecurityConfig             /api/auth/admin/login, /refresh 공개
ErrorCode                  관리자 관련 코드 9개 추가 (용어: "삭제" → "퇴사처리")
mvnw17.cmd, tools/db.ps1   JDK 17 경로 자동 탐색 (C:\SHIS\jdk-17 소멸 대응)
run.ps1                    .env 의존 제거
CLAUDE.md                  화면 규칙 문서 링크 + 용어 규칙
```

**DB 변경**

```text
신설:  admin_account (PK=emp_no) / admin_refresh_token (FK CASCADE) / employee_no_seq
변경:  user_account 의 jsj3216@gmail.com(user_id=3) → DISABLED, is_platform_admin=0
       saas_app 비밀번호를 서버에서 재설정 (.env 값과 어긋나 있었다)
```

### 2026-07-14 작업분

```text
mvnw17.cmd                  JDK 17 로 Maven 을 돌리는 래퍼 (ASCII 전용)
run.ps1                     앱 기동
CLAUDE.md                   저장소 작업 규칙 (마감 루틴 / 스키마 방침 / 함정 / 격리 경고)
project_stat.md             이 문서 (누적 현황)
WORKLOG.md                  일일 작업 일지
tools/db.ps1                DB 접속·SQL 실행 도구 (mysql CLI 가 없어서 만들었다)
src/main/java/com/saas/admin/auth/     (18개 파일)
src/main/java/com/saas/admin/tenant/   (13개 파일)
src/main/java/com/saas/admin/common/   ( 9개 파일 — OpenApiConfig 포함)
```

**삭제한 것**

```text
src/main/resources/db/changelog/       Liquibase changelog 전부 (001~004.sql + master.yaml)
DB 의 DATABASECHANGELOG / DATABASECHANGELOGLOCK 테이블
```

> 업무 테이블 14개와 데이터는 **그대로 두었다.** 지운 것은 Liquibase 이력 테이블 2개뿐이다.
> changelog 는 git 이력(`8fee050`)에 남아 있어 필요하면 복구할 수 있다.

**수정한 것**

```text
pom.xml                     liquibase-core + liquibase-maven-plugin 제거,
                            springdoc-openapi-starter-webmvc-ui 2.3.0 추가
application.yml             Liquibase 설정 제거, ddl-auto: validate → update,
                            spring.config.import 로 .env 직접 로딩, springdoc 설정,
                            포트 8081 → 8089
SecurityConfig.java         Swagger 경로 permitAll
AuthController.java         @Tag / @Operation 문서화
PlatformTenantController.java  @Tag / @Operation 문서화
README.md                   JDK 설정 / API 목록 / 한글 인코딩 함정 / 스키마 방침 갱신
설계안_v2.md                PostgreSQL·RLS 잔재 6곳 제거, v2.1 / v2.2 변경 이력 추가,
                            중복 절 번호(5.4) 수정
```

**커밋 전 정리할 것**

```text
⚠️ LoginRequest.java 의 @Schema(example=...) 에 관리자 실제 비밀번호가 들어 있다 → 더미로 교체
⚠️ ej.pem — 깨진 파일 (SSH 키 아님) → 삭제
```
