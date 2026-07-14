package com.saas.admin.auth.dto;

import jakarta.validation.constraints.NotNull;

public record SelectTenantRequest(
        @NotNull(message = "업체를 선택해야 합니다.")
        Long tenantId
) {
}
