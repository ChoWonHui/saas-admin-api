package com.saas.admin.auth.jwt;

/**
 * 토큰 주체가 어느 테이블의 계정인가.
 * <p>
 * admin_account 와 user_account 는 각자 1번부터 시작하는 ID 를 쓴다.
 * 이 값이 없으면 {@code userId=3} 이 관리자 3번인지 업체 사용자 3번인지 알 수 없고,
 * 권한이 뒤섞인다. 토큰마다 반드시 실려야 한다.
 */
public enum SubjectType {
    /** admin_account — 내부 직원(플랫폼 관리자). 사번으로 로그인한다. */
    ADMIN,
    /** user_account — 업체 사용자. 이메일로 로그인한다. */
    USER
}
