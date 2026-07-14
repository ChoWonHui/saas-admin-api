package com.saas.admin.tenant.dto;

import com.saas.admin.tenant.domain.Tenant;

import java.time.LocalDateTime;

public record TenantResponse(
        Long tenantId,
        String tenantCode,
        String tenantName,
        String tenantSlug,
        String status,
        Long planId,
        String ownerName,
        String contactPhone,
        String contactEmail,
        LocalDateTime createdAt
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getStatus().name(),
                tenant.getPlanId(),
                tenant.getOwnerName(),
                tenant.getContactPhone(),
                tenant.getContactEmail(),
                tenant.getCreatedAt());
    }
}
