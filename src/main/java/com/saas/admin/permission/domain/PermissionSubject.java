package com.saas.admin.permission.domain;

/**
 * 메뉴 권한을 부여하는 주체의 종류. 부서가 상한선이고, 부서+직책이 그 안에서 좁힌다.
 */
public enum PermissionSubject {
    /** 부서(조직) 단위 — 그 부서가 접근할 수 있는 메뉴의 상한선. subjectKey = organization.id. */
    DEPT,
    /**
     * 부서 안의 직책 단위 — 부서 허용 메뉴 중에서만 고를 수 있다(부분집합).
     * subjectKey = "조직id:직책명" (예: "4:팀장"). 그래서 "어느 부서의 팀장"인지가 분명하다.
     */
    DEPT_TITLE
}
