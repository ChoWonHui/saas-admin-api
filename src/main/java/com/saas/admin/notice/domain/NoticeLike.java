package com.saas.admin.notice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 공지 좋아요. (공지, 사번) 한 쌍당 최대 1건 — 유니크 제약(uk_notice_like)이 중복을 막는다.
 * 좋아요 토글은 이 행을 넣거나(좋아요) 지우는(취소) 것으로 처리한다. 좋아요 수는 count 로 센다.
 */
@Entity
@Table(name = "notice_like",
        uniqueConstraints = @UniqueConstraint(name = "uk_notice_like", columnNames = {"notice_id", "emp_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_like_id")
    private Long id;

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(name = "emp_no", nullable = false, length = 6)
    private String empNo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static NoticeLike of(Long noticeId, String empNo) {
        NoticeLike l = new NoticeLike();
        l.noticeId = noticeId;
        l.empNo = empNo;
        return l;
    }
}
