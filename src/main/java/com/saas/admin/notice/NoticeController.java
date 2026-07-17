package com.saas.admin.notice;

import com.saas.admin.adminaccount.AdminAccountService;
import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.auth.jwt.AuthPrincipal;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.notice.dto.NoticeDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "9. 공지사항", description = "사내 공지 게시판. 본문은 에디터 HTML. 작성·수정은 작성자 본인(또는 슈퍼).")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/platform-admin/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;
    private final AdminAccountService adminAccountService;

    private AdminAccount me(AuthPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return adminAccountService.get(principal.empNo());
    }

    @Operation(summary = "공지 목록", description = "고정 글이 먼저, 그다음 최신순. keyword 로 제목을 검색한다.")
    @GetMapping
    public ResponseEntity<Page<NoticeSummary>> list(@RequestParam(defaultValue = "") String keyword,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(noticeService.list(keyword, page, size));
    }

    @Operation(summary = "공지 상세", description = "본문을 포함해 반환하고 조회수를 1 올린다.")
    @GetMapping("/{id}")
    public ResponseEntity<NoticeDetail> get(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(noticeService.get(id, me(principal)));
    }

    @Operation(summary = "공지 작성")
    @PostMapping
    public ResponseEntity<NoticeDetail> create(@Valid @RequestBody CreateNoticeRequest request,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noticeService.create(request, me(principal)));
    }

    @Operation(summary = "공지 수정", description = "작성자 본인(또는 슈퍼)만.")
    @PatchMapping("/{id}")
    public ResponseEntity<NoticeDetail> update(@PathVariable Long id, @Valid @RequestBody UpdateNoticeRequest request,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(noticeService.update(id, request, me(principal)));
    }

    @Operation(summary = "공지 삭제", description = "작성자 본인(또는 슈퍼)만. 조회 이력·좋아요·댓글도 함께 삭제된다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        noticeService.delete(id, me(principal));
        return ResponseEntity.noContent().build();
    }

    // ==================== 좋아요 ====================

    @Operation(summary = "좋아요 토글", description = "누르면 좋아요, 다시 누르면 취소. 계정당 1개.")
    @PostMapping("/{id}/like")
    public ResponseEntity<LikeResponse> toggleLike(@PathVariable Long id,
                                                   @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(noticeService.toggleLike(id, me(principal)));
    }

    @Operation(summary = "좋아요한 사람 목록", description = "부서명·이름(사번)·누른 시각. 좋아요 수 클릭 모달용.")
    @GetMapping("/{id}/likes")
    public ResponseEntity<java.util.List<LikeUser>> likeUsers(@PathVariable Long id,
                                                              @AuthenticationPrincipal AuthPrincipal principal) {
        me(principal); // 인증 확인
        return ResponseEntity.ok(noticeService.likeUsers(id));
    }

    // ==================== 댓글 ====================

    @Operation(summary = "댓글 목록", description = "오래된 순. deletable 은 본인(또는 슈퍼)이면 true.")
    @GetMapping("/{id}/comments")
    public ResponseEntity<java.util.List<CommentResponse>> listComments(
            @PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(noticeService.listComments(id, me(principal)));
    }

    @Operation(summary = "댓글 작성")
    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id,
                                                      @Valid @RequestBody CommentRequest request,
                                                      @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noticeService.addComment(id, request, me(principal)));
    }

    @Operation(summary = "댓글 삭제", description = "작성자 본인(또는 슈퍼)만.")
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        noticeService.deleteComment(commentId, me(principal));
        return ResponseEntity.noContent().build();
    }
}
