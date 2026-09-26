package com.saas.admin.homenotice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 홈페이지 공지사항 DTO. 목록은 요약, 상세는 본문 HTML. */
public final class HomeNoticeDtos {

    private HomeNoticeDtos() {
    }

    // ---- 응답 ----

    /**
     * 목록 한 줄. {@code summary} 는 본문에서 태그를 걷어내 잘라 만든다 —
     * 별도 컬럼을 두면 본문만 고쳤을 때 요약이 옛것으로 남는다.
     */
    public record Summary(
            Long noticeId, String category, String categoryLabel,
            String title, String summary,
            boolean pinned, boolean published,
            int viewCount, String authorName,
            LocalDateTime publishedAt, LocalDateTime createdAt
    ) {
    }

    public record Detail(
            Long noticeId, String category, String categoryLabel,
            String title, String content,
            boolean pinned, boolean published,
            int viewCount, String authorName,
            LocalDateTime publishedAt, LocalDateTime createdAt, LocalDateTime updatedAt,
            /** 목록으로 돌아가지 않고 이어 볼 수 있게. 없으면 null. */
            NeighborRef prev, NeighborRef next
    ) {
    }

    /** 이전/다음 글 링크용 최소 정보. */
    public record NeighborRef(Long noticeId, String title) {
    }

    public record PageResponse(
            List<Summary> content, int page, int size,
            long totalElements, int totalPages,
            long publishedCount, long draftCount
    ) {
    }

    /** 분류 선택지. 공통코드 KCJG_NOTICE_CATEGORY 에서 온다. */
    public record CategoryOption(String code, String label) {
    }

    // ---- 요청 (관리자) ----

    @Schema(description = "홈페이지 공지 등록/수정")
    public record SaveRequest(
            @NotBlank(message = "분류는 필수입니다.") @Size(max = 30) String category,
            @NotBlank(message = "제목은 필수입니다.") @Size(max = 200) String title,
            @NotBlank(message = "내용은 필수입니다.") String content,
            boolean pinned,
            @Schema(description = "true 면 사이트에 공개, false 면 초안으로 보관") boolean published
    ) {
    }
}
