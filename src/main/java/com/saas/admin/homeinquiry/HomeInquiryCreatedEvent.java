package com.saas.admin.homeinquiry;

/**
 * 홈페이지 문의가 접수됐다는 신호.
 * <p>
 * 알림 메일을 서비스가 직접 부르지 않고 이 이벤트로 넘기는 이유가 있다.
 * 직접 부르면 {@code @Async} 스레드가 <b>바깥 트랜잭션이 커밋되기 전에</b> 실행돼
 * 방금 저장한 문의를 찾지 못한다("대상이 없다" 로그만 남고 메일은 나가지 않는다).
 * 실제로 그렇게 새어 나갔다 - 짧은 트랜잭션에서는 우연히 커밋이 먼저 끝나 통과하고,
 * 이미지 업로드가 붙은 요청서처럼 무거운 트랜잭션에서는 매번 실패했다.
 * <p>
 * 커밋이 끝난 뒤에만 듣도록 {@code @TransactionalEventListener(AFTER_COMMIT)} 로 받는다.
 */
public record HomeInquiryCreatedEvent(Long inquiryId) {
}
