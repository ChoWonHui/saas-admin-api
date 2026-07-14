# 이 저장소에서 일하는 규칙

멀티테넌트 SaaS 백엔드. Spring Boot 3.2 / Java 17 / MySQL 5.7.44.

| 문서 | 용도 |
|---|---|
| [`설계안_v2.md`](./멀티테넌트_홈페이지_상담_예약_SaaS_설계안_v2.md) | **확정 설계** (초기설계는 폐기) |
| [`project_stat.md`](./project_stat.md) | **누적 현황** — 지금 무엇이 되고 무엇이 안 되는가 |
| [`WORKLOG.md`](./WORKLOG.md) | **일일 작업 일지** — 날짜별 기록 (최신이 위) |
| [`../saas-admin-web/CLAUDE.md`](../saas-admin-web/CLAUDE.md) | **관리자 콘솔 화면 규칙** — 모든 화면은 이 틀로 만든다 |

> **화면(프론트)을 건드릴 때는 위 화면 규칙을 먼저 읽는다.** 그리드·우클릭 메뉴·확인창·용어가 전 화면에서 같아야 한다.
> 화면과 서버가 다른 용어를 쓰지 않도록, `ErrorCode` 메시지도 그 규칙(§0-5)을 따른다. (예: "삭제" 가 아니라 "퇴사처리")

---

## 0. 🚫 커밋은 절대 하지 않는다

**커밋은 사용자가 직접 한다.** Claude 는 `git commit` / `git push` 를 **실행하지 않는다.**
"커밋할까요?" 라고 묻지도 않는다. 작업이 끝나면 **무엇이 바뀌었는지만 알려주고 끝낸다.**

`git add` / `git rm` 도 하지 않는다. 파일 삭제가 필요하면 그냥 파일을 지운다.
(`git status` / `git diff` / `git log` 같은 **읽기 전용** 명령은 써도 된다)

---

## 0-A. 🗄️ DB 접속 — `tools\db.ps1` 을 쓴다

**이 PC 에는 `mysql` CLI 가 없다.** 매번 Java 파일을 새로 짜지 말 것. 도구가 이미 있다.

```powershell
.\tools\db.ps1 "SHOW TABLES"
.\tools\db.ps1 "SELECT * FROM tenant"
.\tools\db.ps1 "DESC tenant_user"
.\tools\db.ps1 "SHOW CREATE TABLE tenant_user"

# 여러 문장은 세미콜론으로 (따옴표 안의 세미콜론은 무시된다)
.\tools\db.ps1 "ALTER TABLE reservation ADD COLUMN memo VARCHAR(255); DESC reservation"

# 긴 SQL 은 파일로
.\tools\db.ps1 -File tools\sql\작업.sql

# 계정 생성/권한 부여 등 스키마 밖 작업 (SAAS_ROOT_* 가 .env 에 있어야 함)
.\tools\db.ps1 -Admin "GRANT ALL PRIVILEGES ON tenant_saas.* TO 'saas_app'@'%'"
```

`db.ps1` 이 알아서 하는 것: `.env` UTF-8 로딩 / JDBC 드라이버 확보(최초 1회) / UTF-8 출력 /
SELECT 는 표로, DDL·DML 은 영향받은 행 수로 출력. **한글도 깨지지 않는다.**

기본 계정은 `saas_app` — `tenant_saas` 에 대해 **DDL 포함 전 권한**이 있으므로
`CREATE TABLE` / `ALTER TABLE` 도 그냥 된다. `-Admin` 은 계정·권한 작업에만 쓴다.

### 스키마를 바꿔야 할 때 (`ddl-auto: update` 로 안 되는 것)

`update` 는 **추가만 한다.** 아래는 `db.ps1` 로 직접 실행한다.

| 필요한 작업 | 예시 |
|---|---|
| 컬럼 타입 변경 | `.\tools\db.ps1 "ALTER TABLE tenant MODIFY contact_phone VARCHAR(30)"` |
| 컬럼 삭제 | `.\tools\db.ps1 "ALTER TABLE tenant DROP COLUMN latitude"` |
| 유니크 제약 추가 | `.\tools\db.ps1 "ALTER TABLE x ADD UNIQUE KEY uk_x (a, b)"` |
| **부분 유니크** (테넌트당 1건) | 아래 참조 — Hibernate 로는 불가능 |

```powershell
# "테넌트당 ACTIVE 1건" 을 DB 레벨에서 강제하려면 STORED 생성 컬럼을 직접 붙인다.
# MySQL 5.7 에는 부분 유니크 인덱스가 없어서 이게 유일한 수단이다. (NULL 은 유니크에서 충돌하지 않음)
.\tools\db.ps1 @"
ALTER TABLE reservation_slot
  ADD COLUMN active_marker TINYINT UNSIGNED
      GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_slot__one_active (tenant_id, active_marker)
"@
```

> 생성 컬럼을 붙였으면 **엔티티에는 매핑하지 않는다.** 매핑하면 Hibernate 가 INSERT 를 시도해 깨진다.

---

## 0-B. 🔔 "오늘 업무 끝" 이라고 하면 — 자동 마감 루틴

사용자가 **"오늘 업무는 끝"**, **"오늘 여기까지"**, **"퇴근"**, **"마감"** 같은 말을 하면
**묻지 말고 아래를 순서대로 수행한다.**

```text
1. 실행 중인 앱/백그라운드 작업을 전부 내린다
2. git status 로 오늘 바뀐 것을 확인한다 (읽기만 — 커밋하지 않는다)
3. WORKLOG.md 최상단에 오늘 날짜 항목을 추가한다 (아래 템플릿)
4. project_stat.md 를 현재 상태로 갱신한다
   - §2 진행률표 / §5 검증 결과 / §8 기술 부채 / §10 파일 이력
   - 사실만 적는다. 검증하지 않은 것을 "완료" 로 적지 않는다
5. 바뀐 파일 목록을 사용자에게 보여준다 (커밋은 사용자가 직접 한다 — §0)
6. 마지막에 "내일 할 일" 을 3줄 이내로 제시한다
```

### WORKLOG 항목 템플릿

```markdown
## YYYY-MM-DD (요일)

**한 줄**: 오늘의 핵심 성과 한 문장.

### 오늘 완료한 것
표 또는 목록. 실제로 동작을 확인한 것만.

### 실제 서버에서 검증한 것
✅ 로 나열. **코드를 짠 것과 동작을 확인한 것을 구분한다.**

### 오늘 내린 결정
무엇을, 왜. **잃은 것과 얻은 것을 함께 적는다.**

### 오늘 막혔던 것 (다시 밟지 말 것)
증상 → 원인 → 해결. 이 항목이 가장 값어치가 크다.

### 내일 할 일
### 남은 위험
```

**원칙**: "무엇을 했다" 보다 **"왜 그렇게 했고, 무엇을 몰랐다가 알게 됐는지"** 를 남긴다.
막힌 것을 적지 않으면 다음에 똑같이 막힌다.

---

## 1. 스키마 — 엔티티가 전부다. 마이그레이션 도구를 쓰지 않는다

**사용자 결정 (2026-07-14): SQL / changelog 를 쓰지 않는다.**
Liquibase 는 걷어냈다. `db/changelog/` 폴더도 삭제했다. **되살리지 않는다.**

```yaml
spring.jpa.hibernate.ddl-auto: update    # Hibernate 가 엔티티를 보고 테이블을 만든다
```

새 기능이 필요하면 **엔티티만 작성하고 앱을 띄운다.** 그러면 테이블이 생긴다.

```powershell
.\run.ps1     # 기동 시 Hibernate 가 없는 테이블/컬럼을 만든다
```

### `ddl-auto: update` 의 한계 — 알고 써야 한다

**1. 추가만 한다.** 테이블·컬럼 추가는 하지만 **타입 변경, 컬럼 삭제, 이름 변경은 조용히 무시**한다.
엔티티에서 컬럼 타입을 바꿔도 DB 는 그대로다. 에러도 안 난다. 이럴 땐 사람이 직접
`ALTER TABLE` 을 실행해야 한다 (Claude 가 JDBC 로 실행할 수 있다).

**2. 기존 테이블은 건드리지 않는다.** 이건 오히려 다행이다 —
과거에 손으로 만든 제약(FK 8개, 유니크 10개, STORED 생성 컬럼 3개)이 **전부 살아 있다.**
Liquibase 를 걷어낸 뒤 앱을 띄웠을 때 Hibernate 는 `ALTER`/`DROP` 을 단 한 줄도 실행하지 않았다.

**3. 새 테이블에는 제약이 안 붙는다.** 앞으로 Hibernate 가 만드는 테이블에는
FK, 유니크 제약, STORED 생성 컬럼이 **생기지 않는다.** 필요하면 이렇게 챙긴다.

| 필요한 것 | 방법 |
|---|---|
| 유니크 제약 | `@Table(uniqueConstraints = @UniqueConstraint(...))` — Hibernate 가 만들어 준다 |
| 외래키 | `@ManyToOne` 을 쓰면 생긴다. `Long tenantId` 스타일을 유지하면 안 생긴다 |
| **부분 유니크** ("테넌트당 1건") | **Hibernate 로는 불가능.** MySQL 5.7 엔 부분 유니크 인덱스가 없다.<br>STORED 생성 컬럼이 유일한 수단인데 Hibernate 는 못 만든다.<br>→ **애플리케이션 코드에서 막아야 하고, 동시 요청에는 뚫린다.**<br>정말 필요하면 Claude 가 `ALTER TABLE` 을 직접 실행해 붙인다. |

### 스키마 확인 / 수정이 필요할 때

Claude 는 JDBC 로 DB 에 직접 접속해 확인·수정할 수 있다.
`mysql` CLI 는 이 PC 에 없다. 아래 방식을 쓴다.

```powershell
# .env 에서 접속 정보를 읽고, JDK 17 + mysql-connector 로 단일 파일 Java 실행
.\mvnw17.cmd dependency:copy-dependencies "-DincludeArtifactIds=mysql-connector-j" "-DoutputDirectory=target/lib"
& "C:\SHIS\jdk-17\bin\java.exe" "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" `
    -cp "target\lib\mysql-connector-j-8.1.0.jar" Script.java
```

---

## 2. 빌드·실행 — `mvnw` 를 직접 쓰지 않는다

시스템 `JAVA_HOME` 은 **메인 프로젝트(food-biz / Java 8 / Zulu 8)** 가 쓰고 있어 바꿀 수 없다.
Spring Boot 3.2 는 Java 17 이 최소 요구사항이라 Java 8 로는 빌드조차 안 된다.

```powershell
.\run.ps1                    # 앱 기동 (.env 주입 + JDK 17) → http://localhost:8081
.\mvnw17.cmd <goal>          # Maven (JDK 17)
```

`mvnw17.cmd` 가 자기 프로세스 안에서만 `JAVA_HOME` 을 `C:\SHIS\jdk-17` 로 덮어쓴다.
시스템 환경변수는 건드리지 않는다. **`.\mvnw` 를 직접 부르면 Java 8 로 돌아 실패한다.**

---

## 3. JPA 매핑 규칙 (MySQL 5.7 대응)

### 기존 테이블의 ENUM / CHAR 컬럼에는 `@JdbcTypeCode(SqlTypes.CHAR)` 를 붙인다

기존 14개 테이블은 손으로 만들어져 `ENUM` 과 `CHAR(n)` 을 쓴다. JDBC 는 이들을 `Types#CHAR` 로
보고하는데 Java `String`/`enum` 은 기본이 `VARCHAR` 다. 타입이 어긋나면 바인딩이 깨진다.

```java
@Enumerated(EnumType.STRING)
@JdbcTypeCode(SqlTypes.CHAR)   // 기존 ENUM 컬럼용
@Column(name = "status", nullable = false)
private TenantStatus status;
```

> **앞으로 Hibernate 가 새로 만드는 테이블에는 붙이지 않는다.**
> Hibernate 는 enum 을 `VARCHAR(255)` 로 만들므로 `@Enumerated(EnumType.STRING)` 만으로 충분하다.
> 기존 테이블 = `CHAR`, 신규 테이블 = `VARCHAR` 로 갈린다는 점을 기억할 것.

### STORED 생성 컬럼은 매핑하지 않는다

`owner_marker`, `active_marker`, `primary_marker` 는 DB 가 계산한다.
매핑하면 Hibernate 가 INSERT 를 시도해 깨진다.

### `role_id = 2` 하드코딩 의존

`tenant_user.owner_marker` 생성 컬럼이 DB 안에서 `role_id = 2` (TENANT_OWNER) 를 하드코딩한다.
`role` 테이블의 고정 `role_id` (1=PLATFORM_ADMIN, 2=TENANT_OWNER …) 와 짝을 이룬다.
**역할 데이터를 바꾸면 "테넌트당 대표 1명" 제약이 조용히 깨진다.**
Java 쪽은 `Role.TENANT_OWNER_ID` 상수로 이 의존을 명시해 두었다.

---

## 4. 테넌트 격리 (업무 테이블을 만들기 전 필독)

**MySQL 5.7 에는 RLS 가 없다. DB 레벨 최후 방어선이 없다.**
설계안의 3단 방어 중 **현재 작동하는 것은 2차(DB 계정 권한 분리)뿐이고, 이는 DB 간 격리이지
테넌트 간 격리가 아니다.** 1차(Hibernate `@Filter`)와 3차(격리 테스트)는 아직 없다.

예약·상담·게시판 같은 **업무 테이블을 만들기 전에** 반드시 아래를 먼저 구축한다 (설계안 §17-0).

```text
TenantContext(ThreadLocal) + BaseTenantEntity + Hibernate @Filter + 인터셉터
ArchUnit: 업무 엔티티는 BaseTenantEntity 상속 강제 / Repository 밖 네이티브 쿼리 금지
통합 테스트: 테넌트 A 컨텍스트에서 테넌트 B 데이터 조회 시 0건
```

건너뛰고 업무 테이블을 쌓으면 나중에 전 테이블 소급 적용 대공사가 된다.

---

## 5. 인코딩 함정 (PowerShell 5.1) — 실제로 데이터가 깨졌던 건

| 증상 | 원인 | 대응 |
|---|---|---|
| DB 에 `?????` 저장 | `Invoke-RestMethod` 가 문자열 `-Body` 를 **ASCII** 로 전송 | UTF-8 바이트로 직접 넘긴다 |
| 응답이 `ë§ìë...` | PS 가 응답을 **ISO-8859-1** 로 해석 (표시만 깨짐) | 무시 가능 |
| `.env` 한글이 깨진 채 주입 | `Get-Content` 기본값이 **ANSI(CP949)** | `-Encoding UTF8` 필수 |
| `.cmd` 스크립트가 깨짐 | `cmd.exe` 가 UTF-8 을 CP949 로 읽음 | **`.cmd` 는 ASCII 전용으로 작성** |

```powershell
# 한글이 포함된 요청
$bytes = [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json))
Invoke-RestMethod -Uri $url -Method Post -Body $bytes `
    -ContentType "application/json; charset=utf-8" -Headers $H
```

애플리케이션은 정상이다. 깨지는 것은 전부 PowerShell 클라이언트 쪽이다.

---

## 6. 접속 정보

`.env` (gitignore 대상)로만 주입한다. 저장소에 절대 커밋하지 않는다.
DB 는 기존 사내 MySQL 5.7.44 서버의 `tenant_saas` 스키마.

`saas_app` 은 `tenant_saas` 에 대해 DDL 을 포함한 전 권한을 갖는다 (`ddl-auto: update` 가 테이블을
만들어야 하므로). **단 범위는 `tenant_saas` 로 한정돼 있다** — 기존 `cwh`(food-biz)는 보이지도 않는다
(확인됨). MySQL 5.7 에 RLS 가 없는 상황에서 최소한 크로스 DB 유출만은 DB 권한으로 차단한다.

`saas_migrate` 계정은 Liquibase 를 걷어내면서 쓸 일이 없어졌다. `.env` 에는 남아 있으나 앱은 쓰지 않는다.
