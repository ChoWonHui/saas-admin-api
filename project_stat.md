# 프로젝트 현황 (project status)

> **최종 갱신: 2026-07-14**
> 대상: `saas-admin-api` — 멀티테넌트 홈페이지·상담·예약 SaaS 백엔드
>
> | 문서 | 용도 |
> |---|---|
> | [`설계안_v2.md`](./멀티테넌트_홈페이지_상담_예약_SaaS_설계안_v2.md) | 확정 설계 (초기설계는 PostgreSQL·RLS 전제라 **폐기**) |
> | **`project_stat.md`** (이 문서) | **누적 현황** — 지금 무엇이 되고 무엇이 안 되는가 |
> | [`WORKLOG.md`](./WORKLOG.md) | **일일 작업 일지** — 날짜별 기록 |
> | [`CLAUDE.md`](./CLAUDE.md) | 저장소 작업 규칙 |

---

## 1. 한 줄 요약

**플랫폼 관리자 시스템이 실제 MySQL 위에서 동작한다.** 관리자가 로그인해서 업체를 등록·개설·중지할 수 있다.
설계안 §17 우선순위의 **1번(업체 등록)까지 완료**. 다만 **0번(테넌트 격리 기반)은 아직 없다.**

Java 파일 51개. 테스트 0개.

---

## 2. 전체 진행률

| 영역 | 상태 | 비고 |
|---|---|---|
| DB 스키마 | ✅ 완료 | 테이블 14개 + 시드. 실제 서버 반영됨 |
| 스키마 관리 방식 | ⚠️ 변경됨 | **Liquibase 걷어냄 (2026-07-14).** 이제 JPA 엔티티 + `ddl-auto: update` |
| 플랫폼 관리자 인증 | ✅ 완료 | 로그인 / 리프레시(회전) / 로그아웃 / 계정 잠금 |
| 업체 등록·조회·개설·중지 | ✅ 완료 | slug 3중 검증 포함 |
| 업체 사용자 로그인 (2단계) | ✅ 완료 | 소속 목록 → 업체 선택 → 테넌트 토큰 |
| 감사 로그 | 🔶 부분 | 로그인/업체등록만. before/after 스냅샷 미구현 |
| Swagger (springdoc) | ✅ 완료 | JWT 인증 스킴 등록. **운영에서는 꺼야 함** |
| **테넌트 격리 기반 (§17-0)** | ❌ 미착수 | **가장 중요한 미구현 항목. 아래 §7 참조** |
| 테스트 | ❌ 없음 | `src/test` 가 비어 있다. 설계안 §5.4 위반 |
| 업체 관리자 API | ❌ 미착수 | |
| 고객용 API (사이트/예약/상담/게시판) | ❌ 미착수 | |
| 캐싱 (Caffeine + Redis) | ❌ 미착수 | 설계안 §6 |
| 파일 업로드 (S3 Pre-signed) | ❌ 미착수 | 설계안 §10 |
| 프론트엔드 (Next.js) | ❌ 미착수 | 별도 저장소 |

---

## 3. 실행 환경 (실제 확인된 값)

### 3.1 JDK — `mvnw` 를 직접 쓰면 안 된다

개발 PC 의 시스템 `JAVA_HOME` 은 **메인 프로젝트(food-biz / Java 8 / Zulu 8)** 가 쓰고 있어 바꿀 수 없다.
Spring Boot 3.2 는 Java 17 이 최소 요구사항이라 Java 8 로는 **기동은커녕 빌드도 안 된다.**

| | 경로 | 쓰는 곳 |
|---|---|---|
| JDK 8 (Zulu) | `C:\SHIS\zulu8.40.0.25-ca-jdk8.0.222-win_x64` | 시스템 `JAVA_HOME` — 건드리지 말 것 |
| JDK 17 (Temurin 17.0.19) | `C:\SHIS\jdk-17` | 이 프로젝트 전용 |

> JDK 17 은 MSI/winget 이 아니라 **zip 압축 해제**로 설치했다. 설치 프로그램이 `PATH` / `JAVA_HOME` 을
> 자동으로 바꿔 Java 8 환경을 깨뜨리는 것을 막기 위해서다.

**기동 방법**

```powershell
.\run.ps1          # → http://localhost:8089
```

`run.ps1` / `mvnw17.cmd` 가 자기 프로세스 안에서만 `JAVA_HOME` 을 17 로 덮어쓰고 `mvnw` 에 위임한다.
시스템 환경변수는 전혀 바뀌지 않는다. **`.\mvnw` 를 직접 부르면 Java 8 로 돌아 실패한다.**

**IDE 의 Run 버튼으로도 뜬다.** `application.yml` 의 `spring.config.import` 가 `.env` 를 직접 읽는다.

```yaml
spring:
  config:
    import: optional:file:./.env[.properties]
```

이게 없으면 IDE 실행 시 `Could not resolve placeholder 'SAAS_JWT_SECRET'` 로 기동이 실패한다.
셸에 실제 환경변수가 있으면 그쪽이 우선하므로, 운영 배포 시 환경변수로 덮어쓰는 방식은 그대로 유효하다.

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
| **테스트 0개** | `src/test` 가 비어 있다. 설계안 §5.4 는 "선택이 아니라 필수" 라고 못 박았다 | 높음 |
| **격리 기반 부재** | §7 참조 | 높음 |
| **운영 DB 비밀번호 평문 노출** | `food-biz-api/README.md` 에 `ALL PRIVILEGES WITH GRANT OPTION` 계정의 비밀번호가 평문으로 적혀 있다. 원격 저장소에 올라가 있다면 커밋 이력 정리 + 비밀번호 교체 필요 | 높음 |
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
0. 테넌트 격리 기반 구축          ← 업무 테이블 만들기 전에 반드시
   TenantContext + BaseTenantEntity + Hibernate @Filter + ArchUnit + 격리 테스트

1. 현재 구현분의 테스트 작성
   로그인 / 계정 잠금 / slug 검증 / 권한 분리(403) / 상태 전이

2. slug → tenant 매핑 + 캐시 (설계안 §6)

3. 업체 관리자 API
   자사 정보 조회·수정 / 직원 계정 관리

4. 홈페이지 (site) — 페이지·섹션 구조

5. 운영시간·휴무일 → 슬롯 생성 배치

6. 예약 (슬롯 선점 + 중복 방지)
```

---

## 10. 파일 변경 이력 (2026-07-14 작업분 — **아직 미커밋**)

날짜별 상세 경위는 [`WORKLOG.md`](./WORKLOG.md) 를 본다.

**새로 만든 것**

```text
mvnw17.cmd                  JDK 17 로 Maven 을 돌리는 래퍼 (ASCII 전용)
run.ps1                     .env 주입 + 앱 기동
CLAUDE.md                   저장소 작업 규칙 (마감 루틴 / 스키마 방침 / 함정 / 격리 경고)
project_stat.md             이 문서 (누적 현황)
WORKLOG.md                  일일 작업 일지
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
