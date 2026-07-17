package com.saas.admin.notice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 공지 조회 이력. "계정당 1회만 조회수 증가" 를 위해 (공지, 사번) 쌍을 한 번만 기록한다.
 * <p>
 * 유니크 제약(uk_notice_view)이 같은 사람의 중복 기록을 막는다. 이 테이블에 새 행이 들어갈 때만
 * Notice.viewCount 를 1 올린다 → 새로고침·재방문으로는 조회수가 오르지 않는다.
 */
@Entity
@Table(name = "notice_view",
        uniqueConstraints = @UniqueConstraint(name = "uk_notice_view", columnNames = {"notice_id", "emp_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_view_id")
    private Long id;

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(name = "emp_no", nullable = false, length = 6)
    private String empNo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static NoticeView of(Long noticeId, String empNo) {
        NoticeView v = new NoticeView();
        v.noticeId = noticeId;
        v.empNo = empNo;
        return v;
    }
}
