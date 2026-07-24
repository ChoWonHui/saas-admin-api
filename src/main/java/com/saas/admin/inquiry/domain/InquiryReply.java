package com.saas.admin.inquiry.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 문의에 달리는 글타래. 업체(재문의)와 관리자(답변)가 번갈아 남길 수 있다.
 * 본문은 일반 텍스트, 이미지는 첨부 URL 목록.
 */
@Entity
@Table(name = "inquiry_reply")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reply_id")
    private Long id;

    @Column(name = "inquiry_id", nullable = false)
    private Long inquiryId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "author_type", nullable = false, length = 10)
    private InquiryAuthorType authorType;

    /** 작성자 이름 스냅샷 — 표시용. */
    @Column(name = "author_name", nullable = false, length = 50)
    private String authorName;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "inquiry_reply_image", joinColumns = @JoinColumn(name = "reply_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", length = 500)
    private List<String> imageUrls = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static InquiryReply create(Long inquiryId, InquiryAuthorType authorType, String authorName,
                                      String content, List<String> imageUrls) {
        InquiryReply r = new InquiryReply();
        r.inquiryId = inquiryId;
        r.authorType = authorType;
        r.authorName = authorName;
        r.content = content;
        if (imageUrls != null) r.imageUrls.addAll(imageUrls);
        return r;
    }
}
