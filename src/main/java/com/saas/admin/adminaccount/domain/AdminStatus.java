package com.saas.admin.adminaccount.domain;

public enum AdminStatus {
    /** 정상. 로그인할 수 있다. */
    ACTIVE,
    /** 연속 로그인 실패로 잠김. 시간이 지나거나 비밀번호를 재설정하면 풀린다. */
    LOCKED,
    /** 사용 중지. 계정은 남아 있으나 로그인할 수 없다. (삭제와는 다르다) */
    DISABLED
}
