package com.saas.admin.auth.dto;

import java.util.List;

/**
 * 로그인 결과.
 * <p>
 * 플랫폼 관리자는 테넌트 컨텍스트가 없으므로 이 토큰만으로 바로 /api/platform-admin/** 을 쓴다.
 * 업체 사용자는 소속이 여러 개일 수 있어, memberships 중 하나를 골라
 * /api/auth/select-tenant 로 테넌트 컨텍스트가 담긴 토큰을 다시 받아야 한다.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        Long userId,
        String email,
        String name,
        boolean platformAdmin,
        List<MembershipResponse> memberships
) {
}
