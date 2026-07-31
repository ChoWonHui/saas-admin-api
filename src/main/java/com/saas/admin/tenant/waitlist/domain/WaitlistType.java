package com.saas.admin.tenant.waitlist.domain;

/**
 * 접수 유형.
 * RESERVATION 예약 — 미리 시간을 잡고 오는 손님(예약일시 필요).
 * WAITING 대기표 — 워킹(현장 방문)인데 테이블이 꽉 차서 대기하는 손님(순번 부여, 연락처 필수).
 */
public enum WaitlistType {
    RESERVATION, WAITING
}
