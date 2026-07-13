# 멀티테넌트 홈페이지·상담·예약 SaaS 초기 설계안

## 1. 서비스 개요

하나의 대표 도메인에서 업체별 경로를 다르게 제공하고, 경로와 매핑된 업체를 기준으로 홈페이지, 상담, 게시판, 예약관리 기능을 제공하는 멀티테넌트 SaaS 시스템이다.

```text
https://www.홍보.com/{tenantSlug}
```

예시:

```text
https://www.홍보.com/맛있는식당
https://www.홍보.com/강남미용실
```

`/#/경로` 방식보다 일반 경로 방식을 권장한다. `#` 뒤의 값은 서버로 전달되지 않아 SEO, 서버 렌더링, 공유 미리보기, 로그 분석에 불리하다.

---

## 2. 전체 서비스 영역

### 2.1 고객용 홍보 홈페이지

```text
https://www.홍보.com/{tenantSlug}
```

주요 기능:

- 업체 소개
- 서비스·메뉴 소개
- 게시판
- 상담 신청
- 예약 신청
- 오시는 길
- 연락처
- 카카오톡 연결

### 2.2 업체 관리자

```text
https://www.홍보.com/admin
```

주요 기능:

- 홈페이지 내용 관리
- 게시판 관리
- 상담 내역 관리
- 예약 관리
- 고객 관리
- 직원 계정 관리
- 운영시간 관리
- 알림 관리

### 2.3 플랫폼 통합 관리자

```text
https://www.홍보.com/platform-admin
```

주요 기능:

- 업체 등록
- 업체 계정 생성
- 요금제 관리
- 구독 관리
- 서비스 개설·중지
- 공지사항 관리
- 사용량 확인
- 권한 관리

---

## 3. 핵심 개념: Tenant

경로를 업체에 매핑한 뒤 내부적으로는 `tenant_id`를 기준으로 모든 데이터를 조회한다.

```text
/맛있는식당
→ tenant_slug 조회
→ tenant_id 확인
→ 해당 업체 데이터만 조회
```

### TENANT 예시

```text
TENANT
────────────────────────
tenant_id
tenant_code
tenant_name
tenant_slug
status
plan_id
created_at
updated_at
```

예시:

```text
tenant_id   : 10001
tenant_code : SHOP0001
tenant_name : 맛있는식당
tenant_slug : delicious
status      : ACTIVE
```

---

## 4. 멀티테넌트 데이터 원칙

아래 모든 업무 테이블에 `tenant_id`를 포함한다.

- 게시판
- 예약
- 상담
- 고객
- 직원
- 메뉴
- 페이지
- 이미지
- 운영시간
- 알림

### 게시판 예시

```text
BOARD_POST
────────────────────────
post_id
tenant_id
board_type
title
content
writer_id
status
created_at
updated_at
```

### 예약 예시

```text
RESERVATION
────────────────────────
reservation_id
tenant_id
customer_id
reservation_date
reservation_time
reservation_status
customer_name
customer_phone
memo
created_at
updated_at
```

조회 시 반드시 `tenant_id` 조건을 포함한다.

```sql
SELECT *
FROM reservation
WHERE tenant_id = :tenantId
  AND reservation_status = 'REQUESTED';
```

`tenant_id` 조건 누락은 타 업체 데이터 노출로 이어질 수 있으므로 Repository 또는 Service 계층에서 강제해야 한다.

---

## 5. 권장 DB 구조

초기 권장 방식:

```text
PostgreSQL 단일 DB
단일 스키마
모든 업무 테이블에 tenant_id 포함
```

주요 테이블:

```text
TENANT
TENANT_DOMAIN
TENANT_PLAN
TENANT_SUBSCRIPTION

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
RESERVATION_SERVICE
BUSINESS_HOUR

NOTIFICATION
AUDIT_LOG
```

---

## 6. 홈페이지 구성 방식

업체별 HTML을 개별 저장하지 말고, 페이지와 섹션을 조합하는 구조로 설계한다.

```text
홈
├─ 메인 배너
├─ 업체 소개
├─ 서비스 소개
├─ 갤러리
├─ 상담 신청
└─ 오시는 길
```

### SITE_PAGE

```text
page_id
tenant_id
page_name
page_slug
page_type
display_order
is_published
created_at
updated_at
```

### SITE_SECTION

```text
section_id
tenant_id
page_id
section_type
section_data_json
display_order
is_visible
created_at
updated_at
```

### section_data_json 예시

```json
{
  "title": "정성을 다하는 맛있는식당",
  "description": "신선한 재료로 매일 준비합니다.",
  "buttonText": "예약하기",
  "buttonLink": "/reservation",
  "imageUrl": "/assets/main.jpg"
}
```

이 구조를 사용하면 관리자가 문구와 이미지를 수정해도 재배포할 필요가 없다.

---

## 7. 로그인 및 권한

업체코드는 소속 업체 식별용으로만 사용하고, 실제 인증은 계정 기반으로 처리한다.

권장 방식:

```text
업체코드 또는 업체 경로
아이디
비밀번호
```

또는:

```text
이메일 로그인
→ 소속 업체 목록 조회
→ 업체 선택
```

권한:

```text
PLATFORM_ADMIN
TENANT_OWNER
TENANT_MANAGER
TENANT_STAFF
CUSTOMER
```

### TENANT_USER

```text
tenant_user_id
tenant_id
user_id
role_id
status
created_at
updated_at
```

### JWT 예시

```json
{
  "userId": 501,
  "tenantId": 10001,
  "role": "TENANT_OWNER"
}
```

프론트에서 전달한 `tenantId`를 그대로 신뢰하지 말고, 서버에서 실제 소속 여부를 검증한다.

---

## 8. Next.js 라우팅 예시

```text
app/
├─ [tenantSlug]/
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
└─ platform-admin/
```

처리 흐름:

```text
tenantSlug
→ tenant 조회
→ tenantId 확인
→ 해당 업체 데이터 조회
→ 페이지 렌더링
```

---

## 9. 커스텀 도메인 확장

초기:

```text
https://www.홍보.com/delicious
```

추후:

```text
https://www.delicious-store.com
```

### TENANT_DOMAIN

```text
domain_id
tenant_id
domain_name
domain_type
verification_status
is_primary
ssl_status
created_at
updated_at
```

---

## 10. 예약 시스템 MVP

초기 범위:

- 예약 가능 요일·시간 설정
- 고객의 날짜·시간·인원 선택
- 예약 요청 등록
- 업체의 승인·거절
- 관리자 예약 목록 조회
- 고객 결과 알림

예약 상태:

```text
REQUESTED
CONFIRMED
REJECTED
CANCELLED
COMPLETED
NO_SHOW
```

추후 확장:

- 직원별 예약
- 서비스별 소요시간
- 중복 예약 방지
- 예약금·선결제
- 카카오 알림톡
- SMS
- 구글 캘린더 연동
- 자동 리마인드

---

## 11. 초기 MVP 범위

### 고객 화면

```text
업체 메인
업체 소개
서비스 소개
게시판
상담 신청
예약 신청
오시는 길
```

### 업체 관리자

```text
로그인
대시보드
기본정보 수정
홈페이지 문구·이미지 수정
게시판 관리
상담 관리
예약 승인·거절
```

### 플랫폼 관리자

```text
업체 등록
대표 계정 생성
경로 설정
요금제 설정
서비스 사용 중지
```

초기 제외 권장:

```text
온라인 결제
카카오 알림톡
복잡한 권한
업체별 독립 DB
커스텀 도메인 자동 연결
다국어
고급 페이지 빌더
```

---

## 12. 추천 기술 스택

### Frontend

```text
Next.js
TypeScript
React Query
Tailwind CSS
```

### Backend

```text
Spring Boot
Spring Security
JPA
QueryDSL
JWT
```

### Database

```text
PostgreSQL
```

### Storage

```text
AWS S3 또는 Cloudflare R2
```

### Deployment

```text
Frontend: Vercel
Backend: AWS / Railway / Render
Database: 관리형 PostgreSQL
```

초기에는 아래 구성으로 시작한다.

```text
Next.js 프론트 1개
Spring Boot 백엔드 1개
PostgreSQL DB 1개
```

권장 아키텍처:

```text
모듈형 모놀리스
```

---

## 13. 백엔드 모듈 예시

```text
com.service
├─ tenant
├─ auth
├─ user
├─ site
├─ board
├─ customer
├─ consultation
├─ reservation
├─ notification
├─ subscription
├─ file
└─ common
```

모듈 내부:

```text
controller
service
domain
repository
dto
mapper
```

---

## 14. 최초 개발 순서

```text
플랫폼 관리자가 업체 등록
→ tenantSlug 생성
→ 대표 계정 생성
→ 대표 로그인
→ 홈페이지 정보 입력
→ 페이지 공개
→ 고객이 업체 URL 접속
→ 상담 또는 예약 신청
→ 업체 관리자에서 확인
```

구현 우선순위:

```text
1. 업체 등록
2. tenantSlug 매핑
3. 업체별 홈페이지 출력
4. 업체 관리자 로그인
5. 홈페이지 정보 수정
6. 예약 신청
7. 예약 목록 조회
8. 예약 승인·거절
9. 상담 신청·조회
10. 게시판 관리
```

---

## 15. 개발 전 설계 문서

```text
1. 사용자 유형 정의
2. 전체 메뉴 구조
3. 업체 생성부터 홈페이지 공개까지의 흐름
4. 테이블 목록
5. ERD
6. 권한 매트릭스
7. API 목록
8. 화면 목록
9. MVP 범위
10. 제외 범위
```

---

## 16. 핵심 설계 원칙

1. 모든 업체별 데이터에 `tenant_id`를 포함한다.
2. 프론트에서 받은 `tenant_id`를 신뢰하지 않는다.
3. URL은 `tenantSlug`로 받고 서버에서 `tenant_id`로 변환한다.
4. 초기에는 단일 DB, 단일 스키마를 사용한다.
5. 마이크로서비스보다 모듈형 모놀리스로 시작한다.
6. 홈페이지는 페이지와 섹션 조합 방식으로 구성한다.
7. 업체별 HTML 소스를 개별 관리하지 않는다.
8. 예약 MVP는 요청·승인·거절 중심으로 제한한다.
9. 플랫폼 관리자, 업체 대표, 직원 권한을 분리한다.
10. 커스텀 도메인과 구독 결제를 고려해 관련 테이블을 분리한다.

---

## 17. 1차 목표

```text
업체 등록
→ 경로 매핑
→ 업체별 홈페이지 출력
→ 업체 관리자 로그인
→ 상담·예약 접수
→ 업체 관리자에서 확인
```

이 흐름이 완성되면 SaaS의 기본 골격이 완성된다.
