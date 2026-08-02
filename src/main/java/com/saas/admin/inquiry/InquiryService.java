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
import com.saas.admin.notify.AdminNotifySocketHandler;
import com.saas.admin.tenant.domain.Tenant;
import com.saas.admin.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final AdminNotifySocketHandler notifyHandler;
    private final com.saas.admin.notify.TenantNotifySocketHandler tenantNotifyHandler;

    // ===== 업체(사장님) 쪽 =====

    @Transactional
    public InquiryDetail createByTenant(Long tenantId, Long userId, InquiryCreateRequest req) {
        String name = tenantUserName(userId);
        Inquiry q = inquiryRepository.save(Inquiry.create(
                tenantId, userId, name, req.title().trim(), req.content(), safeImages(req.imageUrls())));
        pushAfterCommit(tenantId, q.getId(), q.getTitle(), tenantDisplayName(tenantId));
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
        pushAfterCommit(tenantId, q.getId(), q.getTitle(), tenantDisplayName(tenantId));
        return detail(q, replyRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId));
    }

    private String tenantDisplayName(Long tenantId) {
        return tenantRepository.findById(tenantId).map(Tenant::getName).orElse("");
    }

    /** DB 커밋이 끝난 뒤에만 실시간 알림을 밀어준다(롤백 시 헛알림 방지). */
    private void pushAfterCommit(Long tenantId, Long inquiryId, String title, String tenantName) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyHandler.pushNewInquiry(tenantId, inquiryId, title, tenantName);
                }
            });
        } else {
            notifyHandler.pushNewInquiry(tenantId, inquiryId, title, tenantName);
        }
    }

    /** 관리자 답변 → 커밋 후 그 업체에 실시간 푸시. */
    private void pushTenantAfterCommit(Long tenantId, Long inquiryId, String content) {
        String preview = preview(content);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tenantNotifyHandler.pushAdminReply(tenantId, inquiryId, preview);
                }
            });
        } else {
            tenantNotifyHandler.pushAdminReply(tenantId, inquiryId, preview);
        }
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
        pushTenantAfterCommit(q.getTenantId(), q.getId(), req.content());
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

    // ===== 업체별 대화(채팅) — 문의별이 아니라 업체 하나로 묶어 본다 =====

    /** 관리자 문의 목록: 업체별로 하나의 대화방으로 묶어 마지막 메시지·미답변 여부를 준다. */
    @Transactional(readOnly = true)
    public List<TenantConvSummary> listTenantConversations() {
        List<Inquiry> all = inquiryRepository.findByOrderByCreatedAtDesc();
        if (all.isEmpty()) return List.of();
        Map<Long, List<Inquiry>> byTenant = all.stream().collect(Collectors.groupingBy(Inquiry::getTenantId));
        Map<Long, List<InquiryReply>> repliesByInquiry = replyRepository
                .findByInquiryIdInOrderByCreatedAtAsc(all.stream().map(Inquiry::getId).toList())
                .stream().collect(Collectors.groupingBy(InquiryReply::getInquiryId));
        Map<Long, String> names = tenantNames(all);

        List<TenantConvSummary> result = new ArrayList<>();
        for (var e : byTenant.entrySet()) {
            Long tid = e.getKey();
            LocalDateTime lastAt = null;
            String lastMsg = "", lastFrom = "TENANT";
            long count = 0;
            boolean needsReply = e.getValue().stream().anyMatch(q -> q.getStatus() == InquiryStatus.OPEN);
            for (Inquiry q : e.getValue()) {
                count++;
                if (lastAt == null || q.getCreatedAt().isAfter(lastAt)) { lastAt = q.getCreatedAt(); lastMsg = q.getContent(); lastFrom = "TENANT"; }
                for (InquiryReply r : repliesByInquiry.getOrDefault(q.getId(), List.of())) {
                    count++;
                    if (lastAt == null || r.getCreatedAt().isAfter(lastAt)) { lastAt = r.getCreatedAt(); lastMsg = r.getContent(); lastFrom = r.getAuthorType().name(); }
                }
            }
            result.add(new TenantConvSummary(tid, names.getOrDefault(tid, "(삭제된 가게)"),
                    preview(lastMsg), lastFrom, lastAt, needsReply, count));
        }
        result.sort((a, b) -> b.lastAt().compareTo(a.lastAt()));
        return result;
    }

    /** 한 업체와의 전체 대화(모든 문의·답글을 시간순으로 병합). */
    @Transactional(readOnly = true)
    public ConvView getTenantConversation(Long tenantId) {
        String tenantName = tenantRepository.findById(tenantId).map(Tenant::getName).orElse("(삭제된 가게)");
        List<Inquiry> asc = new ArrayList<>(inquiryRepository.findByTenantIdOrderByCreatedAtDesc(tenantId));
        asc.sort(Comparator.comparing(Inquiry::getCreatedAt));
        List<Long> ids = asc.stream().map(Inquiry::getId).toList();
        Map<Long, List<InquiryReply>> repliesByInquiry = ids.isEmpty() ? Map.of()
                : replyRepository.findByInquiryIdInOrderByCreatedAtAsc(ids).stream().collect(Collectors.groupingBy(InquiryReply::getInquiryId));
        List<ConvMessage> msgs = new ArrayList<>();
        for (Inquiry q : asc) {
            msgs.add(new ConvMessage("TENANT", q.getAuthorName(), q.getContent(), List.copyOf(q.getImageUrls()), q.getCreatedAt()));
            for (InquiryReply r : repliesByInquiry.getOrDefault(q.getId(), List.of())) {
                msgs.add(new ConvMessage(r.getAuthorType().name(), r.getAuthorName(), r.getContent(), List.copyOf(r.getImageUrls()), r.getCreatedAt()));
            }
        }
        msgs.sort(Comparator.comparing(ConvMessage::at));
        return new ConvView(tenantId, tenantName, msgs);
    }

    /** 관리자가 그 업체 대화에 메시지를 보낸다 → 가장 최근 문의에 답글로 달고 답변완료. */
    @Transactional
    public ConvView sendAdminMessage(Long tenantId, String empNo, ReplyCreateRequest req) {
        List<Inquiry> qs = inquiryRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (qs.isEmpty()) throw new ApiException(ErrorCode.INQUIRY_NOT_FOUND);
        Inquiry latest = qs.get(0);
        replyRepository.save(InquiryReply.create(
                latest.getId(), InquiryAuthorType.ADMIN, adminName(empNo), req.content(), safeImages(req.imageUrls())));
        latest.markAnswered();
        pushTenantAfterCommit(tenantId, latest.getId(), req.content());
        return getTenantConversation(tenantId);
    }

    private String preview(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 50 ? t.substring(0, 50) + "…" : t;
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
