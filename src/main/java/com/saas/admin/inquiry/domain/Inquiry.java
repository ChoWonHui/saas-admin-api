package com.saas.admin.inquiry.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 업체(사장님)가 올리는 1:1 문의. 본문은 리치 에디터가 아니라 <b>일반 텍스트</b>이고,
 * 이미지는 첨부 URL 목록으로 따로 담는다(에디터 HTML 아님).
 * <p>
 * 특정 업체(tenantId)에 속하며, 그 업체 사람과 플랫폼 관리자만 볼 수 있다 — 서비스에서 강제한다.
 */
@Entity
@Table(name = "inquiry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 작성한 업체 사용자(user_account.user_id). */
    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    /** 작성자 이름 스냅샷 — 표시용(계정이 지워져도 남게). */
    @Column(name = "author_name", nullable = false, length = 50)
    private String authorName;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 일반 텍스트 본문. 이미지는 imageUrls 로 따로 첨부한다. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "status", nullable = false, length = 10)
    private InquiryStatus status;

    /** 첨부 이미지 URL(S3/CDN). 순서 보존. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "inquiry_image", joinColumns = @JoinColumn(name = "inquiry_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", length = 500)
    private List<String> imageUrls = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Inquiry create(Long tenantId, Long authorUserId, String authorName,
                                 String title, String content, List<String> imageUrls) {
        Inquiry q = new Inquiry();
        q.tenantId = tenantId;
        q.authorUserId = authorUserId;
        q.authorName = authorName;
        q.title = title;
        q.content = content;
        q.status = InquiryStatus.OPEN;
        if (imageUrls != null) q.imageUrls.addAll(imageUrls);
        return q;
    }

    /** 관리자가 답변하면 답변완료로. */
    public void markAnswered() {
        if (this.status != InquiryStatus.CLOSED) this.status = InquiryStatus.ANSWERED;
    }

    /** 업체가 다시 문의를 달면 다시 답변대기로. */
    public void reopen() {
        if (this.status != InquiryStatus.CLOSED) this.status = InquiryStatus.OPEN;
    }

    public void close() {
        this.status = InquiryStatus.CLOSED;
    }

    public boolean belongsTo(Long tenantId) {
        return this.tenantId.equals(tenantId);
    }
}
