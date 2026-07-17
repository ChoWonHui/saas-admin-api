package com.saas.admin.notice.repository;

import com.saas.admin.notice.domain.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 제목 검색(빈 문자열이면 전체). 정렬은 Pageable(고정 desc, 작성일 desc)로 준다. */
    Page<Notice> findByTitleContaining(String title, Pageable pageable);
}
