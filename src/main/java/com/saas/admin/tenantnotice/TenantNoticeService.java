package com.saas.admin.tenantnotice;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenantnotice.domain.TenantNotice;
import com.saas.admin.tenantnotice.dto.TenantNoticeDtos.*;
import com.saas.admin.tenantnotice.repository.TenantNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 업체 공지사항 — 관리자가 등록/관리, 업체가 조회. 상단 고정 + 팝업(기간). */
@Service
@RequiredArgsConstructor
public class TenantNoticeService {

    private final TenantNoticeRepository repo;
    private final AdminAccountRepository adminRepository;

    // ===== 관리자(등록/관리) =====

    @Transactional(readOnly = true)
    public Page<TenantNoticeSummary> list(String keyword, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"));
        return repo.findByTitleContaining(keyword == null ? "" : keyword, PageRequest.of(page, size, sort))
                .map((n) -> TenantNoticeSummary.of(n, authorName(n.getAuthorEmpNo())));
    }

    @Transactional(readOnly = true)
    public TenantNoticeDetail getForAdmin(Long id, AdminAccount viewer) {
        TenantNotice n = require(id);
        return TenantNoticeDetail.of(n, authorName(n.getAuthorEmpNo()), canEdit(n, viewer));
    }

    @Transactional
    public TenantNoticeDetail create(CreateTenantNoticeRequest req, AdminAccount author) {
        TenantNotice saved = repo.save(TenantNotice.create(
                req.title(), req.content(), author.getEmpNo(), req.pinned(),
                req.popupEnabled(), req.popupStartAt(), req.popupEndAt()));
        return TenantNoticeDetail.of(saved, author.getName(), true);
    }

    @Transactional
    public TenantNoticeDetail update(Long id, UpdateTenantNoticeRequest req, AdminAccount editor) {
        TenantNotice n = editable(id, editor);
        n.update(req.title(), req.content(), req.pinned(), req.popupEnabled(), req.popupStartAt(), req.popupEndAt());
        return TenantNoticeDetail.of(n, authorName(n.getAuthorEmpNo()), true);
    }

    @Transactional
    public void delete(Long id, AdminAccount editor) {
        repo.delete(editable(id, editor));
    }

    // ===== 업체(조회) =====

    @Transactional(readOnly = true)
    public List<TenantNoticeSummary> listForTenant() {
        return repo.findByOrderByPinnedDescCreatedAtDesc().stream()
                .map((n) -> TenantNoticeSummary.of(n, authorName(n.getAuthorEmpNo())))
                .toList();
    }

    @Transactional
    public TenantNoticeDetail getForTenant(Long id) {
        TenantNotice n = require(id);
        n.increaseView();
        return TenantNoticeDetail.of(n, authorName(n.getAuthorEmpNo()), false);
    }

    /** 지금 팝업으로 띄울 공지들(켜져 있고 기간 안). 고정 먼저, 최신순. */
    @Transactional(readOnly = true)
    public List<TenantNoticePopup> activePopups() {
        LocalDateTime now = LocalDateTime.now();
        return repo.findByPopupEnabledOrderByPinnedDescCreatedAtDesc("Y").stream()
                .filter((n) -> n.isPopupActive(now))
                .map(TenantNoticePopup::of)
                .toList();
    }

    // ===== 내부 =====

    private TenantNotice require(Long id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOTICE_NOT_FOUND));
    }

    private TenantNotice editable(Long id, AdminAccount editor) {
        TenantNotice n = require(id);
        if (!canEdit(n, editor)) throw new ApiException(ErrorCode.NOTICE_FORBIDDEN);
        return n;
    }

    /** 작성자 본인이거나 직책이 '대표'면 수정·삭제 가능(사내 공지와 동일 규칙). */
    private boolean canEdit(TenantNotice n, AdminAccount a) {
        return n.isAuthor(a.getEmpNo()) || "대표".equals(a.getJobTitle());
    }

    private String authorName(String empNo) {
        return adminRepository.findById(empNo).map(AdminAccount::getName).orElse(empNo);
    }
}
