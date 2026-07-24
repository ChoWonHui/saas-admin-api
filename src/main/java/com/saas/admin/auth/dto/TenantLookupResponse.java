package com.saas.admin.auth.dto;

/**
 * 로그인 전, 업체코드로 가게 이름을 확인하는 공개 응답.
 * 코드가 유효하지 않으면 found=false 로만 알려 주고 이름은 노출하지 않는다.
 */
public record TenantLookupResponse(boolean found, String name) {

    public static TenantLookupResponse notFound() {
        return new TenantLookupResponse(false, null);
    }

    public static TenantLookupResponse of(String name) {
        return new TenantLookupResponse(true, name);
    }
}
