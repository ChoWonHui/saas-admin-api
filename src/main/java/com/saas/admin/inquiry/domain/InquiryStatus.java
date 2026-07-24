package com.saas.admin.inquiry.domain;

/** 문의 상태. OPEN(답변대기) → ANSWERED(답변완료). 관리자가 CLOSED(종료)로 닫을 수 있다. */
public enum InquiryStatus {
    OPEN,
    ANSWERED,
    CLOSED
}
