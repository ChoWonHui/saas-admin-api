package com.saas.admin.homeinquiry.domain;

/**
 * 홈페이지 문의 처리 상태.
 * <p>
 * 업체 문의({@code inquiry})와 달리 답변을 이 게시판 안에서 주고받지 않는다 —
 * 실제 답변은 담당자가 메일이나 전화로 한다. 그래서 상태는 "우리가 처리했는가" 세 단계뿐이다.
 * <p>
 * <b>⚠️ 값을 추가하려면 DB 도 같이 고쳐야 한다.</b> Hibernate 6 은 MySQL 에서 enum 을
 * {@code VARCHAR} 가 아니라 <b>네이티브 {@code ENUM} 컬럼</b>으로 만든다
 * ({@code home_inquiry.status enum('NEW','IN_PROGRESS','DONE')} — 실제로 그렇게 생성됐다).
 * {@code ddl-auto: update} 는 기존 컬럼을 바꾸지 않으므로, 여기에 상수를 하나 더 넣어도
 * DB 는 그대로다. 저장할 때 값이 잘려 들어가거나 거부된다.
 * <pre>
 * ALTER TABLE home_inquiry MODIFY status ENUM('NEW','IN_PROGRESS','DONE','HOLD') NOT NULL;
 * </pre>
 * (같은 이유로 {@code waitlist_entry.status} 등 다른 신규 테이블도 네이티브 ENUM 이다)
 */
public enum HomeInquiryStatus {
    /** 접수됨. 아직 아무도 손대지 않았다. */
    NEW("접수"),
    /** 담당자가 확인하고 연락 중. */
    IN_PROGRESS("처리중"),
    /** 답변·상담이 끝났다. */
    DONE("완료");

    private final String label;

    HomeInquiryStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
