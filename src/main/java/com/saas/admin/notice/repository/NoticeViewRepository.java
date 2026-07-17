package com.saas.admin.notice.repository;

import com.saas.admin.notice.domain.NoticeView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeViewRepository extends JpaRepository<NoticeView, Long> {

    /** 이 계정이 이 공지를 이미 본 적이 있는지. false 일 때만 조회수를 올린다. */
    boolean existsByNoticeIdAndEmpNo(Long noticeId, String empNo);

    /** 공지 삭제 시 조회 이력도 함께 정리. */
    void deleteByNoticeId(Long noticeId);
}
