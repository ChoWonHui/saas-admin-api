# saas-admin-api

멀티테넌트 홈페이지·상담·예약 SaaS의 백엔드 API.
설계 문서: [`../멀티테넌트_홈페이지_상담_예약_SaaS_설계안_v2.md`](../멀티테넌트_홈페이지_상담_예약_SaaS_설계안_v2.md)

현재 범위: **플랫폼 통합 관리자(`/platform-admin`)** — 업체 등록, slug 매핑, 계정 생성,
요금제·구독, 서비스 개설/중지.

---

## 빠른 시작 — clone 하면 바로 뜬다

**필요한 것은 JDK 17 이상 하나뿐이다.** `.env` 도, 환경변수도, DB 설치도 필요 없다.
(접속 정보는 `application.yml` 에 들어 있고, DB 는 원격 서버다)

```powershell
git clone https://github.com/ChoWonHui/saas-admin-api.git
cd saas-admin-api
.\run.ps1                 # → http://localhost:8089  (Swagger: /swagger-ui.html)
```

JDK 17 이 없는 PC 라면 먼저 설치한다.

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

`run.ps1`(→ `mvnw17.cmd`)이 JDK 17+ 를 **자동으로 찾는다** — 탐색 순서:
`JAVA17_HOME` 환경변수 → `JAVA_HOME` 이 17+ 면 그대로 → PATH 의 `java` 가 17+ 면 그대로 →
흔한 설치 경로(Oracle / Temurin / Microsoft / Corretto / Zulu) 전수 탐색.
시스템 `JAVA_HOME` 이 Java 8 이어도 **건드리지 않고** 이 프로세스 안에서만 17 로 덮어쓴다.

특이한 경로에 설치했다면 `JAVA17_HOME` 만 지정하면 된다.

```powershell
$env:JAVA17_HOME = "D:\tools\jdk-21"    # 17 이상이면 아무 버전이나 된다
.\run.ps1
```

---

## 스택

| 영역 | 선택 | 비고 |
|---|---|---|
| Runtime | Java 17 / Spring Boot 3.2 / Maven | 기존 `food-biz-api` 컨벤션과 동일 |
| DB | MySQL 5.7.44 (기존 서버) | 신규 스키마 `tenant_saas` |
| 스키마 관리 | **JPA 엔티티 + `ddl-auto: update`** | 마이그레이션 도구를 쓰지 않는다 |
| 인증 | Spring Security + JWT | |

### 마이그레이션 도구를 쓰지 않는다

Liquibase를 쓰다가 **걷어냈다(2026-07-14).** SQL/changelog를 작성하지 않기로 했다.
스키마의 진실 공급원은 **JPA 엔티티**이고, Hibernate가 기동 시 테이블을 만든다.

새 기능이 필요하면 **엔티티만 작성하고 앱을 띄우면 된다.** SQL을 쓸 일이 없다.

> **`ddl-auto: update`의 한계는 알고 써야 한다.** 자세한 내용은 [`CLAUDE.md`](./CLAUDE.md) §1.
>
> - **추가만 한다.** 타입 변경·컬럼 삭제·이름 변경은 조용히 무시한다.
> - **기존 테이블은 건드리지 않는다.** 덕분에 손으로 만들어둔 FK 8개, 유니크 10개,
>   STORED 생성 컬럼 3개가 전부 살아 있다.
> - **새 테이블에는 제약이 안 붙는다.** 특히 "테넌트당 1건" 같은 부분 유니크는
>   Hibernate로 만들 수 없어(MySQL 5.7엔 부분 유니크 인덱스가 없다)
>   **애플리케이션 코드에서 막아야 하고, 동시 요청에는 뚫린다.**

---

## DB 구성

기존 사내 MySQL 서버 안에 **신규 스키마 `tenant_saas`** 를 만들어 쓴다.
기존 `cwh`(food-biz) 스키마와는 테이블을 섞지 않는다.

접속 정보는 `application.yml` 에 직접 들어 있다 (사용자 결정 2026-07-15 — 어느 PC 에서든
설정 없이 뜨게 하기 위함. 대가는 저장소에 비밀번호가 노출되는 것이며, 인지하고 선택했다).

계정은 용도별로 분리되어 있다. MySQL 5.7에는 Row Level Security가 없어 DB 레벨 테넌트 격리가
불가능하므로, **최소한 크로스 DB 유출만이라도 계정 권한으로 차단**하기 위함이다.

| 계정 | 권한 | 용도 |
|---|---|---|
| `saas_app` | `tenant_saas.*` ALL | 애플리케이션 런타임. `cwh` 접근 불가 |
| `saas_migrate` | `tenant_saas.*` ALL | Liquibase 전용이었으나 **현재 미사용** |

`saas_app`이 DDL 권한을 갖는 이유는 `ddl-auto: update`가 테이블을 만들어야 하기 때문이다.
**범위는 `tenant_saas`로 한정돼 있어 `cwh`는 보이지도 않는다** (실측 확인).

---

## JDK — `mvnw` 대신 `mvnw17` 을 쓴다

Spring Boot 3.x 는 **Java 17 이 최소 요구사항**이라 Java 8 로는 빌드조차 안 된다.
시스템 `JAVA_HOME` 이 Java 8 인 PC(메인 프로젝트 food-biz 용)도 있으므로, `mvnw` 를 직접 부르지 않고
`mvnw17.cmd` 를 쓴다. **JDK 17+ 를 자동 탐색**해 자기 프로세스 안에서만 `JAVA_HOME` 을 덮어쓴다 —
시스템 환경변수는 건드리지 않으므로 Java 8 프로젝트와 공존한다.

```powershell
.\mvnw17.cmd -v     # 첫 줄에 [mvnw17] JAVA_HOME = ... 과 Java version: 17+ 가 찍히면 정상
```

탐색 순서와 `JAVA17_HOME` 재정의 방법은 위 **빠른 시작** 절 참조.
`Maven`/`java` 를 직접 쓰고 싶다면 `JAVA_HOME` 만 JDK 17+ 로 잡고 `.\mvnw.cmd` 를 불러도 된다 —
`mvnw17.cmd` 는 편의 래퍼일 뿐, 없어도 되게 만들어져 있다.

### IntelliJ

IDE 는 시스템 `JAVA_HOME` 을 따라가지 않고 프로젝트별 설정을 쓴다. 이 프로젝트를 열고 아래 두 곳을
설치된 JDK 17+ 로 지정한다.

- **File → Project Structure → Project SDK** → JDK 17+ (SDK 목록에 없으면 `Add JDK` 로 추가)
- **Settings → Build Tools → Maven → Runner → JRE** → 위 SDK

---

## 로컬 실행

```powershell
.\run.ps1                 # → http://localhost:8089  (Swagger: /swagger-ui.html)
```

이게 전부다. `.env` 는 필요 없다 — 접속 정보는 `application.yml` 에 들어 있다.
기동 시 Hibernate 가 없는 테이블·컬럼을 만든다 (`ddl-auto: update`).

직접 하려면 `.\mvnw17.cmd spring-boot:run` (또는 `JAVA_HOME` 이 17+ 면 `.\mvnw.cmd spring-boot:run`).

---

## API (현재 구현된 범위)

플랫폼 관리자 시스템 — **관리자 로그인 + 업체 등록**까지 동작한다. (설계안 §17 의 1번)

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| POST | `/api/auth/login` | 없음 | 이메일+비밀번호. 5회 연속 실패 시 15분 잠금 |
| POST | `/api/auth/refresh` | 없음 | 리프레시 토큰 회전 (쓴 토큰은 즉시 폐기) |
| POST | `/api/auth/select-tenant` | 필요 | 업체 선택 → 테넌트 컨텍스트 토큰 재발급 |
| POST | `/api/auth/logout` | 필요 | 해당 사용자의 리프레시 토큰 전부 폐기 |
| GET | `/api/auth/me` | 필요 | 현재 토큰의 주체 |
| POST | `/api/platform-admin/tenants` | `PLATFORM_ADMIN` | **업체 등록** — 업체 + 대표 계정 + 구독을 한 트랜잭션에 |
| GET | `/api/platform-admin/tenants` | `PLATFORM_ADMIN` | 업체 목록 (`?status=PENDING`) |
| GET | `/api/platform-admin/tenants/{id}` | `PLATFORM_ADMIN` | 업체 단건 |
| POST | `/api/platform-admin/tenants/{id}/activate` | `PLATFORM_ADMIN` | 서비스 개설 (PENDING/SUSPENDED → ACTIVE) |
| POST | `/api/platform-admin/tenants/{id}/suspend` | `PLATFORM_ADMIN` | 서비스 중지 (ACTIVE → SUSPENDED) |

### 로그인 흐름 — 2단계다 (설계안 §11)

한 사람이 여러 업체에 소속될 수 있으므로(프랜차이즈 점주), 로그인만으로는 테넌트가 정해지지 않는다.

```text
POST /api/auth/login
  → accessToken (tenantId 없음) + memberships[]
  → 플랫폼 관리자면 memberships 는 비어 있고, 이 토큰으로 바로 /api/platform-admin/** 사용

POST /api/auth/select-tenant  { tenantId }
  → tenant_user 에서 소속을 재검증한 뒤 tenantId 가 담긴 accessToken 재발급
```

프론트가 보낸 `tenantId` 는 **어떤 경우에도 신뢰하지 않는다.** 매번 `tenant_user` 로 소속을 다시 확인한다.

### 업체 등록 시 slug 검증

등록 시점에 3중으로 막는다. 라우팅이 우연히 동작하는 데 기대지 않는다. (설계안 §2.2)

| 상황 | 응답 |
|---|---|
| 형식 위반 (한글, 연속 하이픈, 3자 미만 …) | `400 SLUG_INVALID_FORMAT` |
| 예약어 (`admin`, `api`, `login` … 37건) | `409 SLUG_RESERVED` |
| 이미 사용 중 | `409 SLUG_DUPLICATED` |

> **한글 요청 주의 (PowerShell)**: PS 5.1 의 `Invoke-RestMethod` 는 문자열 `-Body` 를 ASCII 로
> 인코딩해서 보낸다. 업체명 같은 한글이 서버에 닿기 전에 `?` 로 바뀐다.
> UTF-8 바이트로 직접 넘겨야 한다.
>
> ```powershell
> $bytes = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json))
> Invoke-RestMethod -Uri $url -Method Post -Body $bytes `
>     -ContentType "application/json; charset=utf-8" -Headers $H
> ```

---

## 스키마

**진실 공급원은 JPA 엔티티다.** 마이그레이션 파일은 없다.
기동 시 Hibernate(`ddl-auto: update`)가 없는 테이블·컬럼을 만든다.

### 스키마를 바꿀 때

**엔티티만 고치고 앱을 다시 띄운다.** 그게 전부다.

다만 `update`는 **추가만 한다.** 아래는 안 된다 — 조용히 무시하고 아무 에러도 내지 않는다.

```text
컬럼 타입 변경 / 컬럼 삭제 / 이름 변경 / 제약 추가·삭제
```

이런 변경은 SQL 클라이언트로 직접 실행한다 (아래 **DB 접속** 참조).

---

## DB 접속

저장소에 전용 도구를 두지 않는다 — **표준 SQL 클라이언트를 쓴다**
(IntelliJ Database / DBeaver / HeidiSQL / mysql CLI 아무거나).
접속 정보는 `application.yml` 의 `spring.datasource.*` 에 있다.

계정 `saas_app`은 `tenant_saas`에 **DDL 포함 전 권한**이 있어 `ALTER TABLE`도 그냥 된다.

> **부분 유니크("테넌트당 1건")가 필요하면** STORED 생성 컬럼을 직접 붙여야 한다.
> MySQL 5.7엔 부분 유니크 인덱스가 없어 이게 유일한 수단이고, Hibernate는 만들지 못한다.
> 예시는 [`CLAUDE.md`](./CLAUDE.md) §0-A.

### 현재 테이블 (14개)

Liquibase 시절에 만들어진 것들이라 **FK·유니크·STORED 생성 컬럼이 전부 붙어 있다.**
`ddl-auto: update`는 기존 테이블을 건드리지 않으므로 이 제약들은 그대로 유지된다.

| 그룹 | 테이블 |
|---|---|
| 플랫폼 코어 | `tenant`, `tenant_plan`, `reserved_slug`, `tenant_subscription`, `tenant_domain`, `tenant_usage_daily` |
| 인증 | `user_account`, `role`, `permission`, `role_permission`, `tenant_user`, `refresh_token` |
| 감사·공지 | `audit_log`, `platform_notice` |

시드 데이터: 예약어 slug 37건, 역할 5종, 권한 23종, 역할-권한 45건, 요금제 3종.

> **⚠️ 앞으로 Hibernate가 만드는 새 테이블에는 이 제약들이 안 붙는다.**
> 특히 `owner_marker` 같은 **부분 유니크("테넌트당 1건")는 Hibernate로 만들 수 없다.**
> MySQL 5.7엔 부분 유니크 인덱스가 없어 STORED 생성 컬럼으로 우회해야 하는데,
> Hibernate는 그 개념을 모른다. 애플리케이션 코드로 막아야 하고 **동시 요청에는 뚫린다.**
> 자세한 내용은 [`CLAUDE.md`](./CLAUDE.md) §1.

### MySQL 5.7 대응 기법 (기존 테이블을 이해하려면 알아야 함)

**1. `CHECK` 제약은 무시된다** → 상태값은 `ENUM`으로 선언한다.
`sql_mode`에 `STRICT_TRANS_TABLES`가 있어 ENUM 범위 밖 값은 INSERT 시점에 에러가 난다.

**2. 부분 유니크 인덱스가 없다** → STORED 생성 컬럼으로 구현한다.
NULL은 유니크 인덱스에서 충돌하지 않는 성질을 이용한다.

```sql
active_marker TINYINT UNSIGNED GENERATED ALWAYS AS
    (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
UNIQUE KEY uk_subscription__one_active (tenant_id, active_marker)
```

이 방식으로 아래 세 가지를 DB가 물리적으로 거부한다.

- 테넌트당 `ACTIVE` 구독 2건 (`tenant_subscription.active_marker`)
- 테넌트당 `TENANT_OWNER` 2명 (`tenant_user.owner_marker`)
- 테넌트당 대표 도메인 2개 (`tenant_domain.primary_marker`)

> ⚠️ `tenant_user.owner_marker`는 `role_id = 2`(TENANT_OWNER)를 **하드코딩**한다.
> `004` 시드의 `role_id` 고정값과 짝을 이룬다. 역할 시드를 수정하면 이 제약이 조용히 깨진다.

---

## 테넌트 격리 (필독)

MySQL 5.7에는 RLS가 없다. **DB 레벨 최후 방어선이 없다.**
따라서 아래 규칙은 지켜지지 않으면 곧바로 타 업체 데이터 노출로 이어진다.

1. 업무 엔티티는 반드시 `BaseTenantEntity`를 상속하고 Hibernate `@Filter`를 탄다.
2. Repository 밖에서 `EntityManager`·네이티브 쿼리를 직접 쓰지 않는다.
3. 프론트가 보낸 `tenantId`는 신뢰하지 않는다. URL의 `tenantSlug` → 서버에서 `tenant_id` 변환,
   JWT의 `tenantId`는 매 요청 `tenant_user`에서 소속을 재검증한다.
4. 위 규칙은 ArchUnit + 통합 격리 테스트로 CI에서 강제한다.
