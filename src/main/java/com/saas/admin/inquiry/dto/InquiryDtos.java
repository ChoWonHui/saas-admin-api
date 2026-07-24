package com.saas.admin.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 문의 게시판 DTO. 본문은 일반 텍스트, 이미지는 URL 목록으로 첨부. */
public final class InquiryDtos {

    private InquiryDtos() {
    }

    // ---- 응답 ----

    public record InquirySummary(
            Long inquiryId, String title, String status,
            String authorName, Long tenantId, String tenantName,
            long replyCount, boolean hasImages,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
    }

    public record ReplyView(
            Long replyId, String authorType, String authorName,
            String content, List<String> imageUrls, LocalDateTime createdAt
    ) {
    }

    public record InquiryDetail(
            Long inquiryId, Long tenantId, String tenantName,
            String title, String content, String status,
            String authorName, List<String> imageUrls,
            LocalDateTime createdAt, LocalDateTime updatedAt,
            List<ReplyView> replies
    ) {
    }

    // ---- 요청 ----

    @Schema(description = "문의 등록 (제목 + 일반 텍스트 본문 + 이미지 URL 목록)")
    public record InquiryCreateRequest(
            @NotBlank(message = "제목은 필수입니다.") @Size(max = 200) String title,
            @NotBlank(message = "내용은 필수입니다.") @Size(max = 5000) String content,
            @Size(max = 10, message = "이미지는 최대 10장입니다.") List<@Size(max = 500) String> imageUrls
    ) {
    }

    @Schema(description = "답변/재문의 등록")
    public record ReplyCreateRequest(
            @NotBlank(message = "내용은 필수입니다.") @Size(max = 5000) String content,
            @Size(max = 10, message = "이미지는 최대 10장입니다.") List<@Size(max = 500) String> imageUrls
    ) {
    }
}
