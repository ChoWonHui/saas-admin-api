package com.saas.admin.inquiry.repository;

import com.saas.admin.inquiry.domain.InquiryReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryReplyRepository extends JpaRepository<InquiryReply, Long> {

    List<InquiryReply> findByInquiryIdOrderByCreatedAtAsc(Long inquiryId);

    void deleteByInquiryId(Long inquiryId);

    long countByInquiryId(Long inquiryId);
}
