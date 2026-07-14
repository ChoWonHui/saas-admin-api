package com.saas.admin.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuspendTenantRequest(
        @NotBlank(message = "중지 사유는 필수입니다.")
        @Size(max = 255)
        String reason
) {
}
