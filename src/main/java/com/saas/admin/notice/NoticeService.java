package com.saas.admin.notice;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.notice.domain.Notice;
import com.saas.admin.notice.domain.NoticeComment;
import com.saas.admin.notice.domain.NoticeLike;
import com.saas.admin.notice.domain.NoticeView;
import com.saas.admin.notice.dto.NoticeDtos.*;
import com.saas.admin.notice.repository.NoticeCommentRepository;
import com.saas.admin.notice.repository.NoticeLikeRepository;
import com.saas.admin.notice.repository.NoticeRepository;
import com.saas.admin.notice.repository.NoticeViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeViewRepository viewRepository;
    private final NoticeLikeRepository likeRepository;
    private final NoticeCommentRepository commentRepository;
    private final AdminAccountRepository adminRepository;

    /** 목록. 고정 글이 먼저, 그다음 최신순. 제목 검색(빈 값이면 전체). 좋아요 수는 한 번에 모아 센다. */
    @Transactional(readOnly = true)
    public Page<NoticeSummary> list(String keyword, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"));
        Page<Notice> notices = noticeRepository.findByTitleContaining(
                keyword == null ? "" : keyword, PageRequest.of(page, size, sort));

        // 이 페이지 공지들의 좋아요 수를 한 번의 쿼리로 모아 맵으로. (N+1 회피)
        List<Long> ids = notices.getContent().stream().map(Notice::getId).toList();
        Map<Long, Long> likeCounts = ids.isEmpty() ? Map.of()
                : likeRepository.countByNoticeIdIn(ids).stream()
                    .collect(Collectors.toMap((r) -> (Long) r[0], (r) -> (Long) r[1]));

        return notices.map((n) ->
                NoticeSummary.of(n, authorName(n.getAuthorEmpNo()), likeCounts.getOrDefault(n.getId(), 0L)));
    }

    /** 좋아요 누른 사람 목록(누른 순) — 부서명 · 이름(사번) · 시각. 숫자 클릭 모달용. */
    @Transactional(readOnly = true)
    public List<LikeUser> likeUsers(Long id) {
        if (!noticeRepository.existsById(id)) {
            throw new ApiException(ErrorCode.NOTICE_NOT_FOUND);
        }
        List<NoticeLike> likes = likeRepository.findByNoticeIdOrderByCreatedAtAsc(id);
        Map<String, AdminAccount> accounts = adminRepository
                .findAllById(likes.stream().map(NoticeLike::getEmpNo).toList()).stream()
                .collect(Collectors.toMap(AdminAccount::getEmpNo, Function.identity(), (a, b) -> a));
        return likes.stream().map((l) -> {
            AdminAccount a = accounts.get(l.getEmpNo());
            return new LikeUser(
                    l.getEmpNo(),
                    a != null ? a.getName() : l.getEmpNo(),
                    a != null ? a.getDepartment() : null,
                    l.getCreatedAt());
        }).toList();
    }

    /**
     * 상세 조회. 조회수는 <b>계정당 1회만</b> 오른다 — notice_view 에 처음 기록될 때만 증가.
     * editable 은 요청자가 작성자 본인이거나 슈퍼면 true.
     */
    @Transactional
    public NoticeDetail get(Long id, AdminAccount viewer) {
        Notice n = noticeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTICE_NOT_FOUND));

        String empNo = viewer.getEmpNo();
        // 이 계정이 처음 보는 공지일 때만 조회수 +1. (유니크 제약이 경합 시 이중 증가를 막는 안전망)
        if (!viewRepository.existsByNoticeIdAndEmpNo(id, empNo)) {
            try {
                viewRepository.saveAndFlush(NoticeView.of(id, empNo));
                n.increaseView();
            } catch (DataIntegrityViolationException dup) {
                // 동시에 두 번 들어와 유니크 제약에 걸린 경우 — 이미 센 것으로 보고 무시.
            }
        }

        return detailOf(n, canEditNotice(n, viewer), empNo);
    }

    @Transactional
    public NoticeDetail create(CreateNoticeRequest req, AdminAccount author) {
        Notice saved = noticeRepository.save(
                Notice.create(req.title(), req.content(), author.getEmpNo(), req.pinned()));
        return NoticeDetail.of(saved, author.getName(), true, 0, false, 0);
    }

    @Transactional
    public NoticeDetail update(Long id, UpdateNoticeRequest req, AdminAccount editor) {
        Notice n = getEditable(id, editor);
        n.update(req.title(), req.content(), req.pinned());
        return detailOf(n, true, editor.getEmpNo());
    }

    @Transactional
    public void delete(Long id, AdminAccount editor) {
        Notice n = getEditable(id, editor);
        // 자식 데이터(조회 이력/좋아요/댓글)를 먼저 정리한 뒤 공지를 지운다. (FK 를 걸지 않으므로 코드로 정리)
        viewRepository.deleteByNoticeId(id);
        likeRepository.deleteByNoticeId(id);
        commentRepository.deleteByNoticeId(id);
        noticeRepository.delete(n);
    }

    // ==================== 좋아요 ====================

    /** 좋아요 토글. 이미 눌렀으면 취소, 아니면 추가. 결과(현재 상태 + 총 개수)를 돌려준다. */
    @Transactional
    public LikeResponse toggleLike(Long id, AdminAccount account) {
        if (!noticeRepository.existsById(id)) {
            throw new ApiException(ErrorCode.NOTICE_NOT_FOUND);
        }
        String empNo = account.getEmpNo();
        boolean liked;
        var existing = likeRepository.findByNoticeIdAndEmpNo(id, empNo);
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            liked = false;
        } else {
            try {
                likeRepository.saveAndFlush(NoticeLike.of(id, empNo));
            } catch (DataIntegrityViolationException dup) {
                // 경합으로 이미 들어간 경우 — 좋아요 상태로 본다.
            }
            liked = true;
        }
        return new LikeResponse(liked, likeRepository.countByNoticeId(id));
    }

    // ==================== 댓글 ====================

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(Long id, AdminAccount viewer) {
        if (!noticeRepository.existsById(id)) {
            throw new ApiException(ErrorCode.NOTICE_NOT_FOUND);
        }
        List<NoticeComment> comments = commentRepository.findByNoticeIdOrderByCreatedAtAsc(id);
        Map<String, String> names = namesOf(comments.stream().map(NoticeComment::getAuthorEmpNo).toList());
        return comments.stream()
                .map((c) -> CommentResponse.of(c,
                        names.getOrDefault(c.getAuthorEmpNo(), c.getAuthorEmpNo()),
                        canDeleteComment(c, viewer)))
                .toList();
    }

    @Transactional
    public CommentResponse addComment(Long id, CommentRequest req, AdminAccount author) {
        if (!noticeRepository.existsById(id)) {
            throw new ApiException(ErrorCode.NOTICE_NOT_FOUND);
        }
        NoticeComment saved = commentRepository.save(
                NoticeComment.create(id, author.getEmpNo(), req.content()));
        return CommentResponse.of(saved, author.getName(), true);
    }

    @Transactional
    public void deleteComment(Long commentId, AdminAccount account) {
        NoticeComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
        if (!canDeleteComment(c, account)) {
            throw new ApiException(ErrorCode.COMMENT_FORBIDDEN);
        }
        commentRepository.delete(c);
    }

    /** 댓글은 <b>작성자 본인만</b> 삭제할 수 있다. (슈퍼여도 남의 댓글은 못 지운다) */
    private boolean canDeleteComment(NoticeComment c, AdminAccount account) {
        return c.isAuthor(account.getEmpNo());
    }

    // ==================== 내부 ====================

    private Notice getEditable(Long id, AdminAccount editor) {
        Notice n = noticeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOTICE_NOT_FOUND));
        if (!canEditNotice(n, editor)) {
            throw new ApiException(ErrorCode.NOTICE_FORBIDDEN);
        }
        return n;
    }

    /**
     * 공지 수정·삭제 권한: <b>작성자 본인</b> 또는 <b>직책이 '대표'</b>인 계정만.
     * (슈퍼관리자여도 직책이 대표가 아니면 남의 공지는 수정·삭제할 수 없다 — 사용자 결정)
     */
    private boolean canEditNotice(Notice n, AdminAccount account) {
        return n.isAuthor(account.getEmpNo()) || isRepresentative(account);
    }

    /** 직책(job_title)이 '대표'인가. */
    private boolean isRepresentative(AdminAccount account) {
        String title = account.getJobTitle();
        return title != null && "대표".equals(title.trim());
    }

    /** 상세 DTO 를 좋아요/댓글 수와 함께 만든다. */
    private NoticeDetail detailOf(Notice n, boolean editable, String viewerEmpNo) {
        long likeCount = likeRepository.countByNoticeId(n.getId());
        boolean liked = likeRepository.existsByNoticeIdAndEmpNo(n.getId(), viewerEmpNo);
        long commentCount = commentRepository.countByNoticeId(n.getId());
        return NoticeDetail.of(n, authorName(n.getAuthorEmpNo()), editable, likeCount, liked, commentCount);
    }

    private String authorName(String empNo) {
        return adminRepository.findById(empNo)
                .map(AdminAccount::getName).orElse(empNo);
    }

    /** 여러 사번 → 이름 매핑을 한 번에 조회(N+1 회피). */
    private Map<String, String> namesOf(List<String> empNos) {
        if (empNos.isEmpty()) return Map.of();
        return adminRepository.findAllById(empNos).stream()
                .collect(Collectors.toMap(AdminAccount::getEmpNo, AdminAccount::getName, (a, b) -> a));
    }
}
