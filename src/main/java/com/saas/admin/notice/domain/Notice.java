package com.saas.admin.notice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 사내 공지사항. 본문은 리치 에디터가 만든 HTML 을 그대로 저장한다.
 * <p>
 * 본문(content)은 이미지 base64 가 들어갈 수 있어 매우 길어질 수 있으므로 {@code @Lob}(LONGTEXT)로 둔다.
 * 작성·수정은 로그인 관리자(작성자 본인 또는 슈퍼)만 — 서비스에서 강제한다.
 */
@Entity
@Table(name = "notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 리치 에디터 HTML. 이미지 base64 포함 가능 → LONGTEXT. */
    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** 작성자 사번. 수정·삭제 권한의 기준. */
    @Column(name = "author_emp_no", nullable = false, length = 6)
    private String authorEmpNo;

    /** 상단 고정. 'Y' / 'N'. 목록에서 고정 글이 먼저 나온다. */
    @Column(name = "pinned", nullable = false, length = 1)
    private String pinned;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Notice create(String title, String content, String authorEmpNo, boolean pinned) {
        Notice n = new Notice();
        n.title = title;
        n.content = content;
        n.authorEmpNo = authorEmpNo;
        n.pinned = pinned ? "Y" : "N";
        n.viewCount = 0;
        return n;
    }

    public void update(String title, String content, boolean pinned) {
        this.title = title;
        this.content = content;
        this.pinned = pinned ? "Y" : "N";
    }

    public void increaseView() {
        this.viewCount++;
    }

    public boolean isPinned() {
        return "Y".equals(pinned);
    }

    public boolean isAuthor(String empNo) {
        return authorEmpNo.equals(empNo);
    }
}
