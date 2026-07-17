package com.saas.admin.notice.repository;

import com.saas.admin.notice.domain.NoticeComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeCommentRepository extends JpaRepository<NoticeComment, Long> {

    /** 한 공지의 댓글을 오래된 순으로. */
    List<NoticeComment> findByNoticeIdOrderByCreatedAtAsc(Long noticeId);

    long countByNoticeId(Long noticeId);

    /** 공지 삭제 시 댓글도 함께 정리. */
    void deleteByNoticeId(Long noticeId);
}
