package com.saas.admin.homeinquiry.repository;

import com.saas.admin.homeinquiry.domain.HomeInquiry;
import com.saas.admin.homeinquiry.domain.HomeInquiryStatus;
import com.saas.admin.homeinquiry.domain.SiteType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface HomeInquiryRepository extends JpaRepository<HomeInquiry, Long> {

    /**
     * 목록. siteType / status 를 null 로 주면 그 조건은 빼고 본다 —
     * 화면의 "전체" 필터를 위해서다. (조건 조합이 넷뿐이라 Specification 까지 가지 않는다)
     */
    @Query("""
            select q from HomeInquiry q
            where (:siteType is null or q.siteType = :siteType)
              and (:status is null or q.status = :status)
            order by q.createdAt desc
            """)
    Page<HomeInquiry> search(SiteType siteType, HomeInquiryStatus status, Pageable pageable);

    long countByStatus(HomeInquiryStatus status);

    /** 같은 IP 에서 최근 몇 건이 들어왔는지 — 공개 폼 도배 차단용. */
    long countByClientIpAndCreatedAtAfter(String clientIp, LocalDateTime after);
}
