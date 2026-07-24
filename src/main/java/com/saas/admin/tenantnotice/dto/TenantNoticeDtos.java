package com.saas.admin.tenantnotice.dto;

import com.saas.admin.tenantnotice.domain.TenantNotice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 업체 공지사항 DTO. 본문은 리치 에디터 HTML. */
public final class TenantNoticeDtos {

    private TenantNoticeDtos() {
    }

    @Schema(description = "업체 공지 등록. 상단 고정 + 팝업(기간) 설정.")
    public record CreateTenantNoticeRequest(
            @NotBlank(message = "제목은 필수입니다.") @Size(max = 200) String title,
            @NotBlank(message = "내용은 필수입니다.") String content,
            boolean pinned,
            boolean popupEnabled,
            LocalDateTime popupStartAt,
            LocalDateTime popupEndAt
    ) {
    }

    @Schema(description = "업체 공지 수정.")
    public record UpdateTenantNoticeRequest(
            @NotBlank(message = "제목은 필수입니다.") @Size(max = 200) String title,
            @NotBlank(message = "내용은 필수입니다.") String content,
            boolean pinned,
            boolean popupEnabled,
            LocalDateTime popupStartAt,
            LocalDateTime popupEndAt
    ) {
    }

    public record TenantNoticeSummary(
            Long id, String title, String authorName,
            boolean pinned, boolean popupEnabled, LocalDateTime popupStartAt, LocalDateTime popupEndAt,
            int viewCount, LocalDateTime createdAt
    ) {
        public static TenantNoticeSummary of(TenantNotice n, String authorName) {
            return new TenantNoticeSummary(n.getId(), n.getTitle(), authorName,
                    n.isPinned(), n.isPopupEnabled(), n.getPopupStartAt(), n.getPopupEndAt(),
                    n.getViewCount(), n.getCreatedAt());
        }
    }

    public record TenantNoticeDetail(
            Long id, String title, String content, String authorEmpNo, String authorName,
            boolean pinned, boolean popupEnabled, LocalDateTime popupStartAt, LocalDateTime popupEndAt,
            int viewCount, boolean editable, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static TenantNoticeDetail of(TenantNotice n, String authorName, boolean editable) {
            return new TenantNoticeDetail(n.getId(), n.getTitle(), n.getContent(),
                    n.getAuthorEmpNo(), authorName, n.isPinned(), n.isPopupEnabled(),
                    n.getPopupStartAt(), n.getPopupEndAt(), n.getViewCount(), editable,
                    n.getCreatedAt(), n.getUpdatedAt());
        }
    }

    /** 업체 콘솔 팝업 노출용(가벼운 형태). */
    public record TenantNoticePopup(Long id, String title, String content, LocalDateTime createdAt) {
        public static TenantNoticePopup of(TenantNotice n) {
            return new TenantNoticePopup(n.getId(), n.getTitle(), n.getContent(), n.getCreatedAt());
        }
    }
}
