package com.saas.admin.tenant.dto;

public record CreateTenantResponse(
        Long tenantId,
        String tenantCode,
        String tenantSlug,
        String status,
        Long ownerUserId,
        String ownerEmail
) {
}
