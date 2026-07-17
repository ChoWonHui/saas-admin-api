package com.saas.admin.notice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 공지 댓글. 평면(대댓글 없음) 구조. 작성자 본인(또는 슈퍼)만 삭제할 수 있다 — 서비스에서 강제.
 */
@Entity
@Table(name = "notice_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_comment_id")
    private Long id;

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    /** 작성자 사번. 삭제 권한의 기준. */
    @Column(name = "author_emp_no", nullable = false, length = 6)
    private String authorEmpNo;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static NoticeComment create(Long noticeId, String authorEmpNo, String content) {
        NoticeComment c = new NoticeComment();
        c.noticeId = noticeId;
        c.authorEmpNo = authorEmpNo;
        c.content = content;
        return c;
    }

    public void update(String content) {
        this.content = content;
    }

    public boolean isAuthor(String empNo) {
        return authorEmpNo.equals(empNo);
    }
}
