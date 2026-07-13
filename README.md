# saas-admin-api

멀티테넌트 홈페이지·상담·예약 SaaS의 백엔드 API.
설계 문서: [`../멀티테넌트_홈페이지_상담_예약_SaaS_설계안_v2.md`](../멀티테넌트_홈페이지_상담_예약_SaaS_설계안_v2.md)

현재 범위: **플랫폼 통합 관리자(`/platform-admin`)** — 업체 등록, slug 매핑, 계정 생성,
요금제·구독, 서비스 개설/중지.

---

## 스택

| 영역 | 선택 | 비고 |
|---|---|---|
| Runtime | Java 17 / Spring Boot 3.2 / Maven | 기존 `food-biz-api` 컨벤션과 동일 |
| DB | MySQL 5.7.44 (기존 서버) | 신규 스키마 `tenant_saas` |
| 마이그레이션 | **Liquibase** | Flyway 아님 — 아래 참조 |
| 인증 | Spring Security + JWT | |

### 왜 Flyway가 아니라 Liquibase인가

Flyway **Community Edition은 모든 버전에서 MySQL 5.7 지원을 중단**했다(5.7은 2023-10 EOL).
`flyway:migrate` 실행 시 `Flyway Teams Edition or MySQL upgrade required` 로 차단된다.
8.x까지 내려도 동일하며, 구버전은 Spring Boot 3.2와 클래스패스 충돌까지 난다.
Liquibase는 MySQL 5.7을 제한 없이 지원하므로 이쪽을 택했다.

> DB를 8.0 이상으로 올리면 Flyway로 되돌리는 것도 가능하다.

---

## DB 구성

기존 사내 MySQL 서버 안에 **신규 스키마 `tenant_saas`** 를 만들어 쓴다.
기존 `cwh`(food-biz) 스키마와는 테이블을 섞지 않는다.

접속 정보(호스트·계정·비밀번호)는 저장소에 두지 않는다. 전부 `.env`로 주입한다.

계정은 용도별로 분리되어 있다. MySQL 5.7에는 Row Level Security가 없어 DB 레벨 테넌트 격리가
불가능하므로, **최소한 크로스 DB 유출만이라도 계정 권한으로 차단**하기 위함이다.

| 계정 | 권한 | 용도 |
|---|---|---|
| `saas_app` | `tenant_saas.*` DML만 | 애플리케이션 런타임. DDL 불가, `cwh` 접근 불가 |
| `saas_migrate` | `tenant_saas.*` ALL | Liquibase 전용 |

---

## 로컬 실행

```bash
cp .env.example .env      # 실제 값 채우기 (.env 는 커밋 금지)
set -a && . ./.env && set +a

./mvnw liquibase:update   # 마이그레이션만 실행
./mvnw spring-boot:run    # 앱 실행 (기동 시 Liquibase 자동 실행)
```

### 마이그레이션 상태 확인 / 롤백

```bash
./mvnw liquibase:status
./mvnw liquibase:rollback -Dliquibase.rollbackCount=1
```

---

## 스키마

`src/main/resources/db/changelog/` 아래 changelog로 관리한다.
Hibernate `ddl-auto`는 `validate`로 고정 — **스키마의 진실 공급원은 Liquibase다.**

| 파일 | 내용 |
|---|---|
| `001-platform-core.sql` | `tenant`, `tenant_plan`, `reserved_slug`, `tenant_subscription`, `tenant_domain`, `tenant_usage_daily` |
| `002-auth.sql` | `user_account`, `role`, `permission`, `role_permission`, `tenant_user`, `refresh_token` |
| `003-audit-notice.sql` | `audit_log`, `platform_notice` |
| `004-seed-reference-data.sql` | 예약어 slug 37건, 역할 5종, 권한 23종, 요금제 3종 |

### MySQL 5.7 대응 기법 (스키마를 고칠 때 반드시 알아야 함)

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
