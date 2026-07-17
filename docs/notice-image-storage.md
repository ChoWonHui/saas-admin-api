# 공지 에디터 이미지 저장 방식 (S3 + CloudFront)

작성일: 2026-07-17 · 대상: `saas-admin-api` / `saas-admin-web`

사내 공지사항 게시판의 리치 에디터에 넣는 이미지를 **AWS S3 에 저장하고 CloudFront(CDN) URL 로 서빙**하는 방식을 정리한다. 이전에는 이미지를 base64 로 인코딩해 공지 본문 HTML 안에 그대로 박아 넣었는데, 그 방식의 문제와 새 방식의 동작·설정·운영 주의점을 아래에 상세히 적는다.

---

## 1. 왜 base64 가 아니라 S3 인가

기존 방식은 `FileReader.readAsDataURL()` 로 이미지를 base64 문자열로 만들어 `<img src="data:image/png;base64,...">` 형태로 본문에 심었다. 화면에는 잘 보이지만 다음 문제가 있다.

- **DB 폭증**: 이미지가 그대로 본문 컬럼(`notice.content` = `LONGTEXT`)에 들어간다. 1MB 이미지 한 장이 base64 로는 약 1.37MB 텍스트가 되고, 공지 하나에 이미지 몇 장이면 로우 하나가 수 MB 가 된다. 목록 조회에서 본문을 함께 읽거나 인덱싱할 때 급격히 느려진다.
- **캐시 불가**: 같은 이미지를 열 명이 봐도 매번 본문과 함께 통째로 내려간다. 브라우저·CDN 캐시가 전혀 먹지 않는다.
- **재사용 불가**: 이미지에 고유 URL 이 없어 다른 글이나 외부에서 참조할 수 없다.

S3 + CloudFront 방식은 이미지를 **한 번 올려 URL 을 얻고**, 본문에는 그 URL 만 남긴다. 본문은 가벼운 HTML 로 유지되고, 이미지는 CDN 캐시를 탄다. 이 구조는 참고 프로젝트(food-biz)의 이미지 서버 설계(`d:\newProject\api-20260316.md`)와 동일한 원칙을 따른다.

---

## 2. 업로드 경로: 백엔드 경유 (프리사인드 아님)

이미지를 S3 에 넣는 방법은 두 가지다.

| 방식 | 흐름 | 장단점 |
|---|---|---|
| **프리사인드 URL** | 브라우저 → (우리 API 에서 서명 URL 발급) → 브라우저가 S3 로 직접 PUT | 대역폭이 백엔드를 안 거침. 단, **S3 버킷에 CORS 설정 필요** (브라우저 오리진에서의 PUT 허용). |
| **백엔드 프록시** ✅ | 브라우저 → 우리 API(멀티파트) → 백엔드가 S3 로 PUT | CORS 불필요. 자격 증명이 서버 밖으로 안 나감. 검증을 서버에서 강제. 대신 이미지 바이트가 백엔드를 한 번 지나감. |

food-biz 는 프리사인드 방식(`GET /api/files/presigned-url`)을 쓴다. **saas-admin 은 백엔드 프록시 방식을 택했다.** 이유:

- 우리는 food-biz 의 **운영 버킷을 그대로 공유**한다. 그 버킷의 CORS 설정을 우리 개발 오리진(`localhost:5173/5174`)에 맞춰 건드리고 싶지 않다.
- 프런트는 이미 Vite 프록시(`/api` → `:8089`)를 통해 **자기 오리진의 API 만** 부른다. 백엔드 프록시면 CORS 자체가 발생하지 않는다.
- 파일 형식·크기 검증을 서버에서 확실히 강제할 수 있다.

에디터 이미지처럼 크기가 작고(≤5MB) 빈도가 낮은 업로드에는 프록시 방식의 대역폭 비용이 문제되지 않는다.

---

## 3. 전체 흐름

```
[에디터에 이미지 삽입: 버튼 / 드래그앤드랍 / 붙여넣기]
        │
        ▼
 1) FileReader 로 base64 미리보기를 그 자리에 즉시 삽입     ← 사용자는 바로 이미지를 본다
        │                                                    (임시 <img id="imgup...">)
        ▼
 2) POST /api/platform-admin/files/images  (multipart: file)
        │
        ▼
 [saas-admin-api] FileUploadController → FileUploadService
        │   · 형식 검증(jpeg/png/webp/gif), 크기 검증(≤5MB)
        │   · 키 생성: saas-admin/notices/images/{yyyy}/{MM}/{UUID}.{ext}
        │   · S3Client.putObject(bucket=foodbiz-uploads, key, contentType)
        ▼
 3) 응답 { "url": "https://d2ziky4ycezd5d.cloudfront.net/saas-admin/notices/images/2026/07/<uuid>.png" }
        │
        ▼
 4) 그 임시 <img> 의 src 를 base64 → CDN URL 로 교체, id 제거
        │
        ▼
 [공지 저장]  본문 HTML 에는 <img src="https://.../<uuid>.png"> 만 남는다 (base64 없음)
```

업로드가 **실패하거나 저장소가 꺼져 있으면**(3번에서 503/에러) 4번을 건너뛰고 base64 미리보기를 그대로 남긴다. 즉 S3 가 없어도 에디터는 항상 동작한다(폴백).

---

## 4. 저장 키(S3 Key) 규칙

```
{key-prefix}/notices/images/{yyyy}/{MM}/{UUID}.{ext}
예) saas-admin/notices/images/2026/07/1a2b3c4d-....-9e.png
```

- **`saas-admin/` 접두어**로 food-biz 파일(`menu/`, `notices/`, `profile/`, `qr/` …)과 **키 공간을 분리**한다. 같은 버킷을 쓰지만 서로의 파일과 절대 섞이지 않는다.
- **연/월 디렉터리**로 나눠 한 접두어 아래 객체가 무한정 쌓이지 않게 한다(운영 시 목록·수명주기 관리가 쉬움).
- 파일명은 **UUID** 라 충돌·추측이 불가능하다. 원본 파일명은 쓰지 않는다(경로 삽입·한글 인코딩 문제 회피). 확장자는 원본에서 취하되 이상하면 content-type 으로 유추한다.

---

## 5. 설정 (`application.yml`)

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB      # 이미지 1장 한도
      max-request-size: 8MB   # 요청 전체 한도

# application.yml (git 커밋) — 키는 두지 않는다. 환경변수/로컬 프로파일로 주입.
storage:
  s3:
    enabled: ${S3_ENABLED:false}        # 기본 꺼짐 → 그냥 clone 하면 base64 폴백
    bucket: ${S3_BUCKET:foodbiz-uploads}
    region: ${S3_REGION:ap-northeast-2}
    access-key: ${S3_ACCESS_KEY:}       # ← 키는 여기 없음
    secret-key: ${S3_SECRET_KEY:}       # ←
    cdn-url: ${S3_CDN_URL:https://d2ziky4ycezd5d.cloudfront.net}
    key-prefix: ${S3_KEY_PREFIX:saas-admin}
```

> **자격 증명은 커밋하지 않는다**: AWS access-key/secret-key 는 GitHub push protection 이 자동 차단하며, 노출되면 버킷 전체(=food-biz 운영 이미지)가 위험하다. 그래서 실제 키는 **git 에 올라가지 않는 `application-local.yml`** 에 둔다:
>
> ```yaml
> # src/main/resources/application-local.yml  (.gitignore 에 등록됨)
> storage:
>   s3:
>     enabled: true
>     access-key: AKIA...        # 실제 키
>     secret-key: ...
> ```
>
> `run.ps1` 이 `-Dspring-boot.run.profiles=local` 로 이 파일을 로드한다. 파일이 없는 PC(새로 clone)에서는 조용히 무시되어 base64 폴백으로 동작한다. 환경변수(`S3_ENABLED`/`S3_ACCESS_KEY`/`S3_SECRET_KEY`)로 주입해도 된다. 키를 회전할 때는 이 로컬 파일과 food-biz 쪽 값을 함께 바꾼다(같은 IAM 자격 증명).

의존성(`pom.xml`):

```xml
<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>s3</artifactId>
  <version>2.25.16</version>
</dependency>
```

---

## 6. 코드 맵

### 백엔드 (`com.saas.admin.file`)

| 파일 | 역할 |
|---|---|
| `S3Config.java` | `@ConditionalOnProperty("storage.s3.enabled"=true)`. `S3Client` 빈을 `StaticCredentialsProvider` 로 생성. enabled=false 면 빈 자체가 안 만들어짐. |
| `FileUploadService.java` | `upload(MultipartFile)` — 형식/크기 검증 → 키 생성 → `putObject` → `cdn-url + "/" + key` 반환. `s3Client` 는 `@Autowired(required=false)` 라 빈이 없으면 null → `isEnabled()`=false → `FILE_STORAGE_DISABLED`(503). |
| `FileUploadController.java` | `POST /api/platform-admin/files/images` (multipart). 응답 `{ "url": ... }`. `/api/platform-admin/**` 라 PLATFORM_ADMIN 역할 필수(SecurityConfig). |

관련:
- `ErrorCode` 에 `FILE_EMPTY`(400) / `FILE_TYPE_NOT_ALLOWED`(400) / `FILE_TOO_LARGE`(413) / `FILE_STORAGE_DISABLED`(503) / `FILE_UPLOAD_FAILED`(500) 추가.
- `GlobalExceptionHandler` 에 `MaxUploadSizeExceededException` → `FILE_TOO_LARGE`(413) 핸들러 추가(멀티파트 한도 초과 시 500 대신 명확한 413).

### 프런트 (`saas-admin-web`)

| 위치 | 역할 |
|---|---|
| `src/api/client.js` → `fileApi.uploadImage(file)` | 멀티파트 POST. `Content-Type` 은 브라우저가 boundary 와 자동 설정(직접 지정 금지). 401 이면 리프레시 후 1회 재시도. 반환 `{ url }`. |
| `src/pages/NoticesPage.jsx` → `RichEditor.insertImageFile()` | base64 미리보기 즉시 삽입 → `fileApi.uploadImage` → 성공 시 그 `<img>` src 를 CDN URL 로 교체 / 실패 시 base64 유지. |

---

## 7. 검증 규칙

| 항목 | 값 | 위반 시 |
|---|---|---|
| 허용 형식 | `image/jpeg`, `image/png`, `image/webp`, `image/gif` | `FILE_TYPE_NOT_ALLOWED` (400) |
| 최대 크기 | 5MB (서비스 검증 + multipart 한도 이중) | `FILE_TOO_LARGE` (413) |
| 빈 파일 | 거부 | `FILE_EMPTY` (400) |
| 저장소 비활성 | `enabled=false` 또는 버킷 미설정 | `FILE_STORAGE_DISABLED` (503) → 프런트 base64 폴백 |

---

## 8. 운영 주의점

- **운영 버킷 공유**: `foodbiz-uploads` 는 food-biz **운영** 버킷이다. saas-admin 의 업로드는 실제 운영 스토리지에 즉시 반영된다. 개발/테스트 업로드도 진짜 객체를 만든다.
- **테스트 산출물 정리**: 개발 중 올린 테스트 이미지는 `saas-admin/notices/images/…` 아래에 남는다. 확인이 끝나면 지워 두는 것이 좋다(접두어로 분리돼 있어 우리 것만 골라 삭제 가능).
- **삭제 정책**: 현재 공지 삭제 시 S3 객체를 함께 지우지는 않는다(본문 HTML 안 URL 추적이 필요). food-biz 문서의 원칙(“삭제는 DB 레코드만, 파일은 보존”)과 동일하게 우선 파일은 남긴다. 고아 객체 정리가 필요하면 S3 수명주기 규칙이나 별도 배치로 처리한다.
- **CDN 갱신**: CloudFront 캐시는 URL(=UUID) 기준이라 새 이미지는 항상 새 URL 이다. 무효화(invalidation) 는 필요 없다.
- **끄는 법**: `storage.s3.enabled: false` 로 두면 업로드 없이 base64 폴백만으로 에디터가 동작한다(소규모/오프라인 데모용).

---

## 9. 수동 테스트 체크리스트

1. `storage.s3.enabled: true` 로 서버 기동 → 로그에 S3 관련 에러 없이 뜨는지.
2. 공지 작성 화면에서 이미지 버튼/드래그앤드랍/붙여넣기로 이미지 삽입.
3. 잠깐 뒤 `<img>` 의 `src` 가 `data:` → `https://d2ziky4ycezd5d.cloudfront.net/saas-admin/notices/images/...` 로 바뀌는지(개발자도구 Elements).
4. 공지 저장 후 상세 재조회 → 본문에 base64 가 아니라 CDN URL 이 남아 있는지.
5. 5MB 초과 이미지 → 413(`FILE_TOO_LARGE`) 후 base64 폴백 확인.
6. `enabled: false` 로 재기동 → 업로드 503, 이미지가 base64 로 삽입되고 에디터는 정상 동작.
7. 테스트로 올린 객체 정리.
