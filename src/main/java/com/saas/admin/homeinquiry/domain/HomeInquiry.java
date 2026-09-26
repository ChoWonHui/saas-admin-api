package com.saas.admin.homeinquiry.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 홈페이지 문의 — kanchenjunga.co.kr / exprism.co.kr 의 /contact 폼으로 들어온 것.
 * <p>
 * <b>업체 문의({@code inquiry})와 다른 테이블인 이유:</b> 저쪽은 로그인한 사장님이 남기는 1:1 문의라
 * {@code tenant_id}, {@code author_user_id} 가 모두 NOT NULL 이다. 홈페이지 문의는 로그인도 계정도
 * 없는 외부 방문자가 남긴다 — 같은 테이블에 넣으려면 그 두 제약을 풀어야 하고, 그러면 업체 문의 쪽의
 * "반드시 어느 업체의 누구" 라는 보장이 사라진다. 그래서 테이블을 나눴다.
 * <p>
 * 새로 만드는 테이블이라 enum 에 {@code @JdbcTypeCode(CHAR)} 를 붙이지 않는다 —
 * Hibernate 가 VARCHAR 로 만든다. (CLAUDE.md §3: 기존 테이블 = CHAR, 신규 테이블 = VARCHAR)
 */
@Entity
@Table(name = "home_inquiry", indexes = {
        @Index(name = "ix_home_inquiry__created", columnList = "created_at"),
        @Index(name = "ix_home_inquiry__status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomeInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "home_inquiry_id")
    private Long id;

    /** 어느 사이트에서 왔는가. 목록을 회사/EXPRISM 로 나누는 기준. */
    @Enumerated(EnumType.STRING)
    @Column(name = "site_type", nullable = false, length = 20)
    private SiteType siteType;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 회사 문의는 선택, EXPRISM 도입 문의는 필수(화면에서 강제). 서버는 둘 다 비워도 받는다. */
    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    /** 방문자가 쓴 본문 그대로. 매장 정보는 아래 필드로 따로 받으므로 여기 섞지 않는다. */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // ---- EXPRISM 도입 문의 전용 (회사 문의면 전부 null) ----

    @Column(name = "store_name", length = 100)
    private String storeName;

    @Column(name = "store_size", length = 50)
    private String storeSize;

    @Column(name = "open_timing", length = 50)
    private String openTiming;

    /** 관심 기능. 여러 개를 고를 수 있어 쉼표로 이어 붙인다(표시 전용이라 정규화하지 않는다). */
    @Column(name = "interests", length = 300)
    private String interests;

    /**
     * 첨부 이미지 URL(S3/CDN). 디자인 시안 요청서의 참고 이미지가 여기 들어간다.
     * 순서를 보존한다 - 보내 주신 순서가 곧 우선순위인 경우가 많다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "home_inquiry_image", joinColumns = @JoinColumn(name = "home_inquiry_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", length = 500)
    private java.util.List<String> imageUrls = new java.util.ArrayList<>();

    // ---- 처리 ----

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HomeInquiryStatus status;

    /** 담당자 메모. 화면에서 상태를 바꿀 때 같이 남긴다. */
    @Column(name = "memo", length = 500)
    private String memo;

    /** 접수 시각의 접속 IP. 같은 곳에서 반복 제출되는 것을 뒤에서 확인할 때 쓴다. */
    @Column(name = "client_ip", length = 45)
    private String clientIp;

    // ---- 알림 메일 발송 결과 ----

    /** 알림 메일이 한 통이라도 나갔는가. 실패해도 문의 접수는 유지된다. */
    @Column(name = "notified", nullable = false)
    private boolean notified;

    /** 실제로 보낸 주소들(쉼표). 나중에 "왜 나는 못 받았지" 를 확인할 근거. */
    @Column(name = "notified_to", length = 500)
    private String notifiedTo;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static HomeInquiry create(SiteType siteType, String name, String phone, String email,
                                     String subject, String content,
                                     String storeName, String storeSize, String openTiming, String interests,
                                     String clientIp) {
        HomeInquiry q = new HomeInquiry();
        q.siteType = siteType;
        q.name = name;
        q.phone = phone;
        q.email = email;
        q.subject = subject;
        q.content = content;
        q.storeName = storeName;
        q.storeSize = storeSize;
        q.openTiming = openTiming;
        q.interests = interests;
        q.clientIp = clientIp;
        q.status = HomeInquiryStatus.NEW;
        q.notified = false;
        return q;
    }

    /** 첨부 이미지를 붙인다. 업로드가 끝난 뒤 URL 만 받는다. */
    public void attachImages(java.util.List<String> urls) {
        if (urls != null) this.imageUrls.addAll(urls);
    }

    public void changeStatus(HomeInquiryStatus next, String memo) {
        this.status = next;
        if (memo != null) this.memo = memo.isBlank() ? null : memo.trim();
    }

    /** 알림 메일 발송 결과를 남긴다. 보낸 주소가 하나도 없으면 실패로 본다. */
    public void markNotified(String toAddresses) {
        this.notified = toAddresses != null && !toAddresses.isBlank();
        this.notifiedTo = toAddresses;
        this.notifiedAt = LocalDateTime.now();
    }
}
