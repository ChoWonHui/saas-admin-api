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
        String businessNo,
        String contactPhone,
        String contactEmail,
        String postalCode,
        String address,
        String addressDetail,
        boolean deleted,
        long branchCount,
        LocalDateTime createdAt
) {
    public static TenantResponse from(Tenant tenant) {
        return from(tenant, 0);
    }

    public static TenantResponse from(Tenant tenant, long branchCount) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getStatus().name(),
                tenant.getPlanId(),
                tenant.getOwnerName(),
                tenant.getBusinessNo(),
                tenant.getContactPhone(),
                tenant.getContactEmail(),
                tenant.getPostalCode(),
                tenant.getAddress(),
                tenant.getAddressDetail(),
                tenant.isDeleted(),
                branchCount,
                tenant.getCreatedAt());
    }
}
