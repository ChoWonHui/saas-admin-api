# 작업 일지

하루 단위 기록. **최신 날짜가 위로** 온다.
누적 현황은 [`project_stat.md`](./project_stat.md), 작업 규칙은 [`CLAUDE.md`](./CLAUDE.md) 를 본다.

각 항목은 **한 일 / 왜 / 막힌 것 / 남긴 결정** 을 남긴다.
"무엇을 했다" 보다 **"왜 그렇게 했고, 무엇을 몰랐다가 알게 됐는지"** 가 나중에 값어치가 있다.

---

## 2026-07-15 (수)

**한 줄**: **관리자 콘솔(EXPRISM Admin)을 nginx + React 로 세우고**, 내부 직원 계정을 `user_account` 에서
분리해 **사번 체계(admin_account)** 로 재설계했다. 로그인부터 퇴사처리까지 브라우저로 검증했다.

### 오늘 완료한 것

| 영역 | 내용 |
|---|---|
| 프론트엔드 | `saas-admin-web` 신설 (React 18 + Vite). 로그인 / 관리자 CRUD / 업체 목록 |
| nginx | `tools/nginx-1.28.0` + `tools/nginx.ps1`(start·stop·reload·status). :8080 정적 + `/api`→:8089 프록시 |
| **관리자 계정 분리** | `admin_account` 신설. 내부 직원은 **사번(260001)** 으로 로그인. `user_account`(업체 사용자, 이메일)와 완전 분리 |
| 사번 채번 | `employee_no_seq` + `SELECT … FOR UPDATE`. YY + 4자리 (260001 = 2026년 1번째) |
| 관리자 CRUD | 생성(사번 자동채번) / 조회 / 수정 / **퇴사처리(소프트 삭제)** / 비밀번호 초기화 |
| 비밀번호 정책 | 생성·초기화 시 **`exprism1234!` 고정** → 첫 로그인 시 **강제 변경** → 변경 후 **재로그인 강제** |
| 브랜딩 | EXPRISM / *We express your vision through innovation.* |
| 화면 규칙 | [`saas-admin-web/CLAUDE.md`](../saas-admin-web/CLAUDE.md) — **모든 화면이 따라야 하는 틀** |

### 실제 서버에서 검증한 것 (Playwright, 브라우저 실행)

✅ 사번 로그인 → 관리자 목록 (nginx :8080 경유, CORS 설정 없이 동작)
✅ 관리자 생성 → 사번 자동 채번(260002, 260003 … 순번 증가 확인)
✅ 더블클릭 → 수정 창 / 우클릭 → 메뉴(수정·비밀번호 초기화·퇴사처리)
✅ 퇴사처리 확인창에 **대상(사번+이름)** 이 찍히고, 실행 시 목록에서 사라짐
✅ 퇴사처리 시 **리프레시 토큰 2건이 즉시 폐기**됨 (DB 확인)
✅ 자기 자신 퇴사처리 차단 / 이미 퇴사한 계정 재처리 차단 / 마지막 관리자 보호
✅ 초기 비밀번호 로그인 → 강제 변경 화면. **`/admins` 로 우회해도 튕겨나옴**
✅ 신규/확인 불일치 시 변경 버튼 비활성 → 일치 시 변경 → **"변경된 비밀번호로 로그인하세요"** 안내
✅ 옛 비밀번호 차단, 새 비밀번호로 재로그인 성공
✅ API 15개 시나리오 (사번 로그인·틀린 비번·없는 사번·CRUD·삭제 가드) 전부 통과

### 오늘 내린 결정

**1. 관리자를 `user_account` 에서 분리했다** (`admin_account` 신설)
한국 ERP 관례대로 내부 직원은 사번으로 움직인다. 업체 사용자(이메일)와 식별자 체계가 다르다.
- **얻은 것**: 두 체계가 섞이지 않는다. 로그인 경로도 분리(`/api/auth/admin/login`).
- **잃은 것**: 토큰 테이블이 하나 더 늘었다(`admin_refresh_token`). 기존 `refresh_token` 은
  `user_account` 에 FK 로 묶여 있어 관리자 ID 를 넣을 수 없었다.
- 기존 `jsj3216@gmail.com`(user_account, user_id=3)은 **DISABLED** 로 비활성화.

**2. PK 를 `admin_id`(대리키)가 아니라 `emp_no`(사번)로 했다**
처음엔 `admin_id BIGINT` 로 만들었다가 걷어냈다. **사번이 곧 사람의 키**라는 사용자 결정.
- **전제 두 가지가 깨지면 참조 무결성이 깨진다**: ①사번은 절대 안 바뀐다(`updatable=false` 로 못 박음)
  ②사번은 재사용하지 않는다(퇴사자 사번을 재발급하면 그 사람의 감사 기록이 새 사람에게 붙는다)
- JWT subject 도 사번, API 경로도 `/admins/260002`. 접근 로그만 봐도 누구인지 읽힌다.

**3. 비밀번호를 만드는 사람이 정하지 않는다**
생성·초기화 모두 `exprism1234!` 고정 → 본인이 첫 로그인 때 반드시 바꾼다.
- **남의 비밀번호를 아는 사람이 없어야 한다**는 것이 이유.
- 기본 비밀번호는 공개된 값이므로 **서버가 직접 막는다** — JWT 에 `mustChangePassword` 를 실어
  그 상태의 토큰에는 `ROLE_PLATFORM_ADMIN` 을 **아예 부여하지 않는다**. 화면 우회 불가(403).
- 변경 후 모든 토큰을 폐기 → 재로그인 강제. 새 비밀번호를 한 번 더 입력하게 해 기억을 굳힌다.

**4. 접속 정보를 `application.yml` 에 하드코딩했다** (사용자 결정)
"어떤 PC / 어떤 경로에서든 그냥 실행" 을 위해 `.env` 의존을 걷어냈다.
- ⚠️ **DB 비밀번호와 JWT 서명키가 커밋되는 파일에 들어갔다.** 푸시하면 GitHub 에 공개된다.
  위험을 명확히 알린 뒤 사용자가 선택한 것이다. (아래 "남은 위험")

### 오늘 막혔던 것 (다시 밟지 말 것)

**1. `Could not resolve placeholder 'SAAS_JWT_SECRET'` — `.env` 를 못 찾은 게 아니라 "작업 디렉터리"가 문제였다**
`spring.config.import: optional:file:./.env` 의 `./` 는 **JVM 작업 디렉터리 기준**이다.
IDE 가 작업 디렉터리를 저장소 루트로 잡으면 `saas-admin-api/.env` 를 못 본다. `optional:` 이라 조용히 건너뛴다.
→ 지금은 하드코딩으로 이 문제 자체가 사라졌다.

**2. `C:\SHIS\jdk-17` 이 사라졌다**
`mvnw17.cmd`, `tools/db.ps1` 이 이 경로를 하드코딩하고 있어 둘 다 죽었다. 실제 JDK 17 은
`C:\Program Files\Java\jdk-17`. → **후보 경로 탐색**으로 바꿨다. 시스템 `JAVA_HOME` 도 이제 17 이다
(CLAUDE.md §2 의 "시스템 JAVA_HOME 은 Java 8" 전제가 더 이상 사실이 아니다).

**3. DB 가 `saas_app` 을 거부했다 (`Access denied … 1045`)**
IP/방화벽 문제로 오해했으나, **서버의 비밀번호가 `.env` 값과 어긋나 있었을 뿐**이다.
두 계정(`saas_app`, `saas_migrate`)이 동시에 거부된 것이 단서였다 — 계정은 `@'%'`, 잠김도 만료도 아니었다.
→ 관리자 계정으로 `ALTER USER … IDENTIFIED BY` 하여 서버를 `.env` 값에 맞췄다.

**4. PowerShell 5.1 + 한글 `.ps1` = 파싱 에러 (또 겪었다)**
`.ps1` 을 **BOM 없이** UTF-8 로 저장하면 PS 5.1 이 CP949 로 읽어 한글 주석이 깨지고 **문법 오류**가 난다.
→ **한글이 든 `.ps1` 은 반드시 UTF-8 BOM 으로 저장**한다. (CLAUDE.md §5 의 연장선)

**5. nginx 가 성공 메시지를 stderr 로 낸다**
`nginx -t` 의 "syntax is ok" 도 stderr 다. `$ErrorActionPreference='Stop'` 아래에서 PowerShell 이
이를 오류로 감싸 스크립트가 죽었다. → 네이티브 호출을 감싸고 **종료 코드로만** 성패를 판단한다.

**6. 목록이 0건으로 나왔다 — 응답이 배열이 아니라 `Page` 였다**
`/api/platform-admin/tenants` 는 `{content: [...], totalElements}` 를 준다. 프론트가 배열로 가정했다.
→ **서버 응답 형태를 가정하지 말 것.**

**7. 그리드가 좁은 창에서 "연 / 락 / 처" 로 쪼개졌다**
표의 자동 레이아웃은 줄바꿈이 허용되면 열을 **글자 한 개 폭**까지 줄인다.
→ 모든 셀 `white-space: nowrap` + 표를 `overflow-x` 컨테이너로 감싼다.
`min-width: max-content` 는 쓰지 말 것 — 넓은 화면에서도 불필요한 가로 스크롤이 생긴다.

### 내일 할 일

```text
1. 테스트 데이터 정리 (260002~260008) + 채번 초기화     ← 지금 8건 중 6건이 검증 잔여물
2. 🔴 JWT 서명키 교체 (아직 'change-me-...' 예시값)     ← 이게 새면 토큰 위조로 전 API 장악
3. 업체 화면을 관리자 화면과 같은 틀로 (우클릭 메뉴·확인창)
```

### 남은 위험

| 위험 | 내용 | 심각도 |
|---|---|---|
| **JWT 서명키가 예시값** | `change-me-to-a-random-32byte-or-longer-secret-value`. 이 값을 아는 사람은 사번·비밀번호 없이 **관리자 토큰을 위조**한다 | 🔴 매우 높음 |
| **비밀 정보가 커밋 대상 파일에** | `application.yml` 에 DB 비밀번호·JWT 키. 푸시하면 GitHub 공개. **한 번 푸시되면 이력에 영구히 남는다** | 🔴 높음 |
| 관리자 비밀번호가 소스에 | `LoginRequest.java` Swagger 예시에 실제 비밀번호 평문 | 🟠 중간 |
| `saas-admin-web` 이 git 밖 | 프론트 전체가 버전관리되지 않고 있다 | 🟠 중간 |
| 테넌트 격리 기반 부재 | 여전히 미해결 (아래 2026-07-14 항목 참조) | 🔴 높음 |
| 260001 비밀번호 미상 | 사용자가 콘솔에서 직접 변경. 검증은 `260007`(`Verify2026!`)로 진행했다 | 🟡 낮음 |

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
| 개발 도구 | `tools\db.ps1` — DB 접속/SQL 실행 도구. `mysql` CLI 가 없어서 만들었다 |
| 작업 체계 | `WORKLOG.md`(일일 일지) + `CLAUDE.md` 마감 루틴. **커밋은 사용자가 직접 한다** |

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
