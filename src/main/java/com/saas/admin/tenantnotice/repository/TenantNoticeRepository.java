package com.saas.admin.tenantnotice.repository;

import com.saas.admin.tenantnotice.domain.TenantNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantNoticeRepository extends JpaRepository<TenantNotice, Long> {

    // 제목 검색(빈 문자열이면 전체). 정렬은 Pageable(고정 desc, 작성일 desc).
    Page<TenantNotice> findByTitleContaining(String title, Pageable pageable);

    // 업체 조회용 — 고정 먼저, 최신순.
    List<TenantNotice> findByOrderByPinnedDescCreatedAtDesc();

    // 팝업 후보(켜진 것만). 기간 판정은 서비스에서 현재 시각으로 거른다.
    List<TenantNotice> findByPopupEnabledOrderByPinnedDescCreatedAtDesc(String popupEnabled);
}
