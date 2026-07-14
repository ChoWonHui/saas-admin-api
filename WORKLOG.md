# 작업 일지

하루 단위 기록. **최신 날짜가 위로** 온다.
누적 현황은 [`project_stat.md`](./project_stat.md), 작업 규칙은 [`CLAUDE.md`](./CLAUDE.md) 를 본다.

각 항목은 **한 일 / 왜 / 막힌 것 / 남긴 결정** 을 남긴다.
"무엇을 했다" 보다 **"왜 그렇게 했고, 무엇을 몰랐다가 알게 됐는지"** 가 나중에 값어치가 있다.

---

## 2026-07-14 (월)

**한 줄**: 플랫폼 관리자 시스템(관리자 로그인 + 업체 등록)을 실제 MySQL 위에서 동작시켰다.
그 과정에서 **Liquibase 를 걷어내고 스키마 관리 방식을 JPA 엔티티 기반으로 전환**했다.

### 오늘 완료한 것

| 영역 | 내용 |
|---|---|
| 개발 환경 | JDK 17(Temurin) 설치. **시스템 `JAVA_HOME`(Java 8)은 건드리지 않고** 이 프로젝트만 17로 스위칭 |
| 인증 | 로그인 / 리프레시(토큰 회전) / 로그아웃 / 업체 선택(2단계) / 계정 잠금(5회 실패 → 15분) |
| 업체 등록 | 업체 + 대표 계정 + 구독을 **한 트랜잭션**에서 생성. slug 3중 검증 |
| 업체 관리 | 목록 / 단건 / 서비스 개설(ACTIVE) / 중지(SUSPENDED) |
| 감사 로그 | `LOGIN_SUCCESS` / `LOGIN_FAIL` / `TENANT_CREATE` / `TENANT_ACTIVATE` |
| Swagger | springdoc-openapi. JWT 인증 스킴 등록 |
| 스키마 관리 | **Liquibase 제거 → `ddl-auto: update`** (아래 참조) |

Java 파일 **51개**. 설계안 §17 우선순위의 **1번(업체 등록)까지 완료**.

### 실제 서버에서 검증한 것

코드만 쓴 게 아니라 **실제 MySQL 에 붙여 앱을 띄우고 API 를 호출한 뒤 DB 행을 직접 조회**했다.

```text
✅ 관리자 로그인 → JWT 발급
✅ 틀린 비밀번호 → 401 INVALID_CREDENTIALS
✅ 토큰 없이 관리자 API → 401 차단
✅ 업체 등록 → SHOP0001 / delicious / PENDING
✅ 예약어 slug 'admin' → 409 SLUG_RESERVED
✅ 한글 slug / 연속 하이픈 → 400 SLUG_INVALID_FORMAT
✅ slug 중복 → 409 SLUG_DUPLICATED
✅ 서비스 개설 → PENDING → ACTIVE
✅ 업체 대표가 플랫폼 관리자 API 접근 → 403 차단
✅ 업체 선택 → roleCode=TENANT_OWNER 토큰 발급
✅ 한글 저장 → tenant_name HEX = EBA79BEC9E88EB8A94EC8B9DEB8BB9 (맛있는식당)
✅ DB 생성 컬럼 제약 작동 (owner_marker=1 / active_marker=1)
```

### 오늘 내린 결정

**1. Liquibase 를 걷어냈다 (사용자 결정)**

SQL / changelog 를 쓰지 않기로 했다. 스키마의 진실 공급원은 **JPA 엔티티**가 되고,
Hibernate `ddl-auto: update` 가 테이블을 만든다. `db/changelog/` 폴더는 삭제했다.

- **잃은 것**: 새 테이블에 부분 유니크("테넌트당 1건") 제약을 걸 수 없다. 스키마 드리프트 감지 불가
  (`validate` 를 껐으므로). 롤백 수단 없음. 새 환경은 앱을 띄워서 만들어야 함.
- **살아남은 것**: 기존 14개 테이블의 FK 8개 / 유니크 10개 / STORED 생성 컬럼 3개가 **전부 유지**.
  `update` 는 기존 테이블을 건드리지 않는다 — 앱 기동 로그에 `ALTER`/`DROP` 이 단 한 줄도 없었다.
- **부수 효과**: 런타임 계정 `saas_app` 에 DDL 권한을 열어야 했다 (설계안 §5.2 가 막아두려던 것).
  범위는 `tenant_saas` 로 한정돼 크로스 DB 차단은 유효하다.

**2. 설계 문서의 낡은 잔재를 걷어냈다**

설계안 v2 에 PostgreSQL·RLS 전제가 6곳 남아 있어(§6 캐싱, §11 권한, §15 배포, §17, §19)
그대로 따라 구현하면 없는 기능을 만들려다 막히는 상태였다. v2.1 / v2.2 변경 이력을 최상단에 추가.

### 오늘 막혔던 것 (다시 밟지 말 것)

**1. `ej.pem` 은 SSH 키가 아니다**
36바이트짜리 깨진 파일로, 내용이 `decode blob failed: invalid format` 이었다.
다행히 3306 포트가 직접 열려 있어 SSH 터널 없이 JDBC 로 붙었다.

**2. MySQL ENUM / CHAR + Hibernate `validate` → 앱 기동 실패**
JDBC 가 `ENUM`·`CHAR(n)` 을 `Types#CHAR` 로 보고하는데 Java `String`/`enum` 은 기본이 `VARCHAR`.
→ `@JdbcTypeCode(SqlTypes.CHAR)` 로 해결.

**3. PowerShell 5.1 한글 인코딩 — 실제로 DB 에 `?????` 가 저장됐다**
`Invoke-RestMethod` 가 문자열 `-Body` 를 **ASCII** 로 전송한다. 서버에 닿기 전에 이미 `?` 로 바뀐다.
`.env` 도 `Get-Content` 기본값이 **ANSI(CP949)** 라 한글이 깨진 채 주입됐다.
→ **애플리케이션은 처음부터 정상이었다.** 깨진 것은 전부 PowerShell 클라이언트 쪽.
   `.cmd` 파일도 `cmd.exe` 가 UTF-8 을 CP949 로 읽어 깨지므로 **ASCII 전용**으로 작성.

**4. `liquibase:diff` 는 DB 를 파괴할 뻔했다** (걷어내기 전 겪은 것)
안전장치 없이 돌리니 changeset 47개가 나왔는데, `dropTable permission`(시드 68행 소멸),
유니크 제약 10개 삭제(`uk_tenant__slug` 포함 → slug 중복이 뚫린다), 외래키 8개 삭제가 들어 있었다.
diff 는 "Hibernate 가 아는 것이 전부" 라고 가정한다. **생성 결과를 그대로 적용하면 안 된다.**

**5. IDE 의 Run 버튼으로는 앱이 안 떴다**
`Could not resolve placeholder 'SAAS_JWT_SECRET'` — IDE 는 `.env` 를 모른다.
→ `spring.config.import: optional:file:./.env[.properties]` 로 Spring 이 `.env` 를 직접 읽게 했다.
   이제 환경변수 주입 없이도 기동된다. (실제 환경변수가 있으면 그쪽이 우선)

### 내일 할 일 (권장 순서)

```text
0. 테넌트 격리 기반          ← 업무 테이블(예약/상담/게시판)을 만들기 전에 반드시
   TenantContext + BaseTenantEntity + Hibernate @Filter + ArchUnit + 격리 테스트
1. 현재 구현분의 테스트 작성  ← src/test 가 아직 완전히 비어 있다
2. 업체 관리자 API
```

> ⚠️ **격리 기반이 없다.** 3단 방어 중 작동하는 것은 DB 계정 분리(2차)뿐이고,
> 그것은 **DB 간 격리이지 테넌트 간 격리가 아니다.**
> 이 상태로 업무 테이블을 쌓으면 나중에 전 테이블 소급 적용 대공사가 된다.

### 남은 위험

| 항목 | 내용 |
|---|---|
| 🔴 **운영 DB 비밀번호 평문 노출** | `food-biz-api/README.md` 에 `ALL PRIVILEGES WITH GRANT OPTION` 계정의 비밀번호가 평문으로 적혀 있다. 원격 저장소에 올라가 있다면 **비밀번호 교체 + 커밋 이력 정리 필요** |
| 🔴 테스트 0개 | `src/test` 가 비어 있다 |
| 🟡 Swagger 예시에 실제 비밀번호 | `LoginRequest` 의 `@Schema(example=...)` 에 관리자 실제 비밀번호가 들어 있다. 커밋 전 더미로 교체할 것 |
| 🟡 `ej.pem` | 깨진 파일. 삭제 필요 |
