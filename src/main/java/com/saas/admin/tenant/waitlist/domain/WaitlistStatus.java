package com.saas.admin.tenant.waitlist.domain;

/**
 * 접수 상태.
 * 예약: RESERVED 예약접수 → SEATED 착석(입장) / CANCELLED 취소.
 * 대기표: WAITING 대기 → CALLED 호출 → SEATED 착석 / CANCELLED 취소.
 */
public enum WaitlistStatus {
    RESERVED, WAITING, CALLED, SEATED, CANCELLED;

    /** 아직 처리되지 않은(현황판에 남는) 상태. */
    public boolean isActive() {
        return this == RESERVED || this == WAITING || this == CALLED;
    }
}
