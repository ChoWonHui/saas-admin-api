package com.saas.admin.auth.jwt;

/**
 * 인증된 요청의 주체.
 * <p>
 * <b>주체마다 식별자가 다르다.</b> 관리자(ADMIN)는 사번({@code empNo}), 업체 사용자(USER)는
 * {@code userId}(user_account.user_id) 로 식별한다. 둘은 서로 다른 테이블이고 키 체계도 다르다.
 * subjectType 을 보지 않고 한쪽만 믿으면 엉뚱한 사람을 가리키게 된다.
 * <p>
 * tenantId 가 null 이면 테넌트 컨텍스트가 없는 토큰이다 — 관리자이거나, 업체 선택 전 상태다.
 */
public record AuthPrincipal(
        /** 업체 사용자만 값이 있다. 관리자는 null — 관리자의 키는 사번이다. */
        Long userId,
        String email,
        Long tenantId,
        String roleCode,
        boolean platformAdmin,
        SubjectType subjectType,
        /** 관리자만 값이 있다. admin_account 의 PK. 업체 사용자는 null. */
        String empNo,
        /**
         * 기본 비밀번호 상태라 비밀번호를 먼저 바꿔야 하는가.
         * true 면 관리자 권한을 주지 않는다 — 비밀번호 변경 외에는 아무것도 못 한다.
         */
        boolean mustChangePassword
) {
    /** 업체 사용자 토큰. */
    public static AuthPrincipal user(Long userId, String email, Long tenantId, String roleCode) {
        return new AuthPrincipal(userId, email, tenantId, roleCode, false, SubjectType.USER, null, false);
    }

    /** 관리자 토큰. 사번으로 식별하며, 테넌트 컨텍스트를 갖지 않는다. */
    public static AuthPrincipal admin(String empNo, String email, boolean mustChangePassword) {
        return new AuthPrincipal(null, email, null, null, true, SubjectType.ADMIN, empNo, mustChangePassword);
    }

    public boolean isAdmin() {
        return subjectType == SubjectType.ADMIN;
    }

    public boolean hasTenantContext() {
        return tenantId != null;
    }
}
