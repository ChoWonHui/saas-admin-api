package com.saas.admin.tenant.dto;

import com.saas.admin.tenant.domain.Tenant;

import java.time.LocalDateTime;

public record TenantResponse(
        Long tenantId,
        String tenantCode,
        String tenantName,
        String status,
        Long planId,
        String ownerName,
        String businessNo,
        String mailOrderSalesNo,
        String contactPhone,
        String contactEmail,
        String postalCode,
        String address,
        String addressDetail,
        String bankCode,
        /** 은행 표시명. 공통코드 BANK_CD 에서 찾아 채운다. 코드가 지워졌으면 코드값이 그대로 온다. */
        String bankName,
        String accountNo,
        String accountHolder,
        boolean deleted,
        long branchCount,
        LocalDateTime createdAt
) {
    public static TenantResponse from(Tenant tenant) {
        return from(tenant, 0, null);
    }

    public static TenantResponse from(Tenant tenant, long branchCount) {
        return from(tenant, branchCount, null);
    }

    public static TenantResponse from(Tenant tenant, long branchCount, String bankName) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getStatus().name(),
                tenant.getPlanId(),
                tenant.getOwnerName(),
                tenant.getBusinessNo(),
                tenant.getMailOrderSalesNo(),
                tenant.getContactPhone(),
                tenant.getContactEmail(),
                tenant.getPostalCode(),
                tenant.getAddress(),
                tenant.getAddressDetail(),
                tenant.getBankCode(),
                bankName != null ? bankName : tenant.getBankCode(),
                tenant.getAccountNo(),
                tenant.getAccountHolder(),
                tenant.isDeleted(),
                branchCount,
                tenant.getCreatedAt());
    }
}
