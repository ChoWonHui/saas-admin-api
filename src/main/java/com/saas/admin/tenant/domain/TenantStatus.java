package com.saas.admin.tenant.domain;

/**
 * 설계안 §4.1 — 상태별 고객 화면 동작.
 * PENDING   : 404 (공개 전)
 * ACTIVE    : 정상 노출
 * SUSPENDED : 503 + 안내 페이지 (404 아님 — 검색 색인 유지)
 * CLOSED    : 404, 관리자 로그인 차단
 */
public enum TenantStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
