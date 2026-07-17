package com.saas.admin.notice.dto;

import com.saas.admin.notice.domain.Notice;
import com.saas.admin.notice.domain.NoticeComment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 공지사항 요청·응답 DTO 모음. */
public final class NoticeDtos {

    private NoticeDtos() {
    }

    @Schema(description = "공지 작성. content 는 에디터 HTML.")
    public record CreateNoticeRequest(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
            String title,

            @NotBlank(message = "내용은 필수입니다.")
            String content,

            boolean pinned
    ) {
    }

    @Schema(description = "공지 수정.")
    public record UpdateNoticeRequest(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
            String title,

            @NotBlank(message = "내용은 필수입니다.")
            String content,

            boolean pinned
    ) {
    }

    /** 목록용 — 본문은 빼고 요약 정보만(+ 좋아요 수). */
    public record NoticeSummary(
            Long id, String title, String authorEmpNo, String authorName,
            boolean pinned, int viewCount, long likeCount, LocalDateTime createdAt
    ) {
        public static NoticeSummary of(Notice n, String authorName, long likeCount) {
            return new NoticeSummary(n.getId(), n.getTitle(), n.getAuthorEmpNo(), authorName,
                    n.isPinned(), n.getViewCount(), likeCount, n.getCreatedAt());
        }
    }

    /** 좋아요 누른 사람 한 명 — 부서명 · 이름(사번) · 누른 시각. */
    public record LikeUser(
            String empNo, String name, String department, LocalDateTime likedAt
    ) {
    }

    /** 상세 — 본문 포함 + 편집 가능 여부 + 좋아요/댓글 요약. */
    public record NoticeDetail(
            Long id, String title, String content, String authorEmpNo, String authorName,
            boolean pinned, int viewCount, boolean editable,
            long likeCount, boolean liked, long commentCount,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static NoticeDetail of(Notice n, String authorName, boolean editable,
                                      long likeCount, boolean liked, long commentCount) {
            return new NoticeDetail(n.getId(), n.getTitle(), n.getContent(), n.getAuthorEmpNo(), authorName,
                    n.isPinned(), n.getViewCount(), editable, likeCount, liked, commentCount,
                    n.getCreatedAt(), n.getUpdatedAt());
        }
    }

    @Schema(description = "좋아요 토글 결과.")
    public record LikeResponse(boolean liked, long likeCount) {
    }

    @Schema(description = "댓글 작성/수정.")
    public record CommentRequest(
            @NotBlank(message = "댓글 내용은 필수입니다.")
            @Size(max = 1000, message = "댓글은 1000자를 넘을 수 없습니다.")
            String content
    ) {
    }

    /** 댓글 응답 — 작성자 이름 + 본인/슈퍼 삭제 가능 여부(deletable). */
    public record CommentResponse(
            Long id, String authorEmpNo, String authorName, String content,
            boolean deletable, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static CommentResponse of(NoticeComment c, String authorName, boolean deletable) {
            return new CommentResponse(c.getId(), c.getAuthorEmpNo(), authorName, c.getContent(),
                    deletable, c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}
