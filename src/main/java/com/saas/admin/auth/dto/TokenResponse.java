package com.saas.admin.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        Long tenantId,
        String roleCode
) {
}
