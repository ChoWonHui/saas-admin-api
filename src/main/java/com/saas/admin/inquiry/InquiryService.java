package com.saas.admin.inquiry;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import com.saas.admin.auth.repository.UserAccountRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.inquiry.domain.Inquiry;
import com.saas.admin.inquiry.domain.InquiryAuthorType;
import com.saas.admin.inquiry.domain.InquiryReply;
import com.saas.admin.inquiry.domain.InquiryStatus;
import com.saas.admin.inquiry.dto.InquiryDtos.*;
import com.saas.admin.inquiry.repository.InquiryReplyRepository;
import com.saas.admin.inquiry.repository.InquiryRepository;
import com.saas.admin.tenant.domain.Tenant;
import com.saas.admin.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 업체 ↔ 관리자 1:1 문의 게시판. 업체 사용자가 문의하고 플랫폼 관리자가 답변한다. */
@Service
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryReplyRepository replyRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final AdminAccountRepository adminAccountRepository;

    // ===== 업체(사장님) 쪽 =====

    @Transactional
    public InquiryDetail createByTenant(Long tenantId, Long userId, InquiryCreateRequest req) {
        String name = tenantUserName(userId);
        Inquiry q = inquiryRepository.save(Inquiry.create(
                tenantId, userId, name, req.title().trim(), req.content(), safeImages(req.imageUrls())));
        return detail(q, List.of());
    }

    @Transactional(readOnly = true)
    public List<InquirySummary> listForTenant(Long tenantId) {
        List<Inquiry> list = inquiryRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return toSummaries(list, false);
    }

    @Transactional(readOnly = true)
    public InquiryDetail getForTenant(Long tenantId, Long inquiryId) {
        Inquiry q = requireInquiry(inquiryId);
        if (!q.belongsTo(tenantId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "다른 가게의 문의는 볼 수 없습니다.");
        }
        return detail(q, replyRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    /** 업체가 자기 문의에 재문의(글타래)를 남긴다 → 다시 답변대기. */
    @Transactional
    public InquiryDetail replyByTenant(Long tenantId, Long inquiryId, Long userId, ReplyCreateRequest req) {
        Inquiry q = requireInquiry(inquiryId);
        if (!q.belongsTo(tenantId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "다른 가게의 문의에는 남길 수 없습니다.");
        }
        replyRepository.save(InquiryReply.create(
                inquiryId, InquiryAuthorType.TENANT, tenantUserName(userId), req.content(), safeImages(req.imageUrls())));
        q.reopen();
        return detail(q, replyRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    // ===== 관리자 쪽 =====

    @Transactional(readOnly = true)
    public List<InquirySummary> listForAdmin(String statusFilter) {
        List<Inquiry> list;
        InquiryStatus status = parseStatus(statusFilter);
        if (status == null) {
            list = inquiryRepository.findByOrderByCreatedAtDesc();
        } else {
            list = inquiryRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return toSummaries(list, true);
    }

    @Transactional(readOnly = true)
    public InquiryDetail getForAdmin(Long inquiryId) {
        Inquiry q = requireInquiry(inquiryId);
        return detail(q, replyRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    /** 관리자가 답변을 남긴다 → 답변완료. */
    @Transactional
    public InquiryDetail replyByAdmin(Long inquiryId, String empNo, ReplyCreateRequest req) {
        Inquiry q = requireInquiry(inquiryId);
        replyRepository.save(InquiryReply.create(
                inquiryId, InquiryAuthorType.ADMIN, adminName(empNo), req.content(), safeImages(req.imageUrls())));
        q.markAnswered();
        return detail(q, replyRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    /** 관리자가 문의를 종료 처리한다. */
    @Transactional
    public InquiryDetail close(Long inquiryId) {
        Inquiry q = requireInquiry(inquiryId);
        q.close();
        return detail(q, replyRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    @Transactional
    public void delete(Long inquiryId) {
        Inquiry q = requireInquiry(inquiryId);
        replyRepository.deleteByInquiryId(inquiryId);
        inquiryRepository.delete(q);
    }

    // ===== 내부 =====

    private Inquiry requireInquiry(Long id) {
        return inquiryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.INQUIRY_NOT_FOUND));
    }

    private String tenantUserName(Long userId) {
        return userAccountRepository.findById(userId)
                .map(u -> u.getName())
                .orElse("(알 수 없음)");
    }

    private String adminName(String empNo) {
        return adminAccountRepository.findByEmpNoAndDeleted(empNo, "N")
                .map(AdminAccount::getName)
                .orElse("관리자");
    }

    private InquiryStatus parseStatus(String s) {
        if (s == null || s.isBlank() || "ALL".equalsIgnoreCase(s)) return null;
        try {
            return InquiryStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<String> safeImages(List<String> urls) {
        if (urls == null) return List.of();
        return urls.stream().filter(u -> u != null && !u.isBlank()).limit(10).toList();
    }

    private List<InquirySummary> toSummaries(List<Inquiry> list, boolean withTenantName) {
        Map<Long, Long> replyCounts = list.stream().collect(Collectors.toMap(
                Inquiry::getId, q -> replyRepository.countByInquiryId(q.getId())));
        Map<Long, String> tenantNames = withTenantName ? tenantNames(list) : Map.of();
        return list.stream().map(q -> new InquirySummary(
                q.getId(), q.getTitle(), q.getStatus().name(),
                q.getAuthorName(), q.getTenantId(),
                withTenantName ? tenantNames.getOrDefault(q.getTenantId(), "(삭제된 가게)") : null,
                replyCounts.getOrDefault(q.getId(), 0L),
                !q.getImageUrls().isEmpty(),
                q.getCreatedAt(), q.getUpdatedAt()
        )).toList();
    }

    private Map<Long, String> tenantNames(List<Inquiry> list) {
        List<Long> ids = list.stream().map(Inquiry::getTenantId).distinct().toList();
        return tenantRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getName));
    }

    private InquiryDetail detail(Inquiry q, List<InquiryReply> replies) {
        String tenantName = tenantRepository.findById(q.getTenantId())
                .map(Tenant::getName).orElse("(삭제된 가게)");
        List<ReplyView> replyViews = replies.stream().map(r -> new ReplyView(
                r.getId(), r.getAuthorType().name(), r.getAuthorName(),
                r.getContent(), List.copyOf(r.getImageUrls()), r.getCreatedAt()
        )).toList();
        return new InquiryDetail(
                q.getId(), q.getTenantId(), tenantName,
                q.getTitle(), q.getContent(), q.getStatus().name(),
                q.getAuthorName(), List.copyOf(q.getImageUrls()),
                q.getCreatedAt(), q.getUpdatedAt(), replyViews);
    }
}
