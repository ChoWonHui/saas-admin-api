package com.saas.admin.auth.jwt;

/**
 * 인증된 요청의 주체.
 * tenantId 가 null 이면 테넌트 컨텍스트가 없는 토큰이다 — 플랫폼 관리자이거나, 업체 선택 전 상태다.
 */
public record AuthPrincipal(
        Long userId,
        String email,
        Long tenantId,
        String roleCode,
        boolean platformAdmin
) {
    public boolean hasTenantContext() {
        return tenantId != null;
    }
}
