package com.saas.admin.auth.dto;

/** 로그인 사용자가 소속된 업체 하나. 여러 개면 업체를 선택해야 한다. (설계안 §11) */
public record MembershipResponse(
        Long tenantId,
        String tenantName,
        String tenantSlug,
        String tenantStatus,
        String roleCode
) {
}
