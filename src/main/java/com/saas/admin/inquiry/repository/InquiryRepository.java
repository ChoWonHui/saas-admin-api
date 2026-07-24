package com.saas.admin.inquiry.repository;

import com.saas.admin.inquiry.domain.Inquiry;
import com.saas.admin.inquiry.domain.InquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    // 업체 콘솔: 우리 가게 문의만, 최신순.
    List<Inquiry> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    // 관리자 콘솔: 전체 / 상태별, 최신순.
    List<Inquiry> findByOrderByCreatedAtDesc();

    List<Inquiry> findByStatusOrderByCreatedAtDesc(InquiryStatus status);
}
