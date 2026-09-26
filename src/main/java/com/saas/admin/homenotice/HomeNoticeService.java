package com.saas.admin.homenotice;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.repository.AdminAccountRepository;
import com.saas.admin.code.domain.CommonCode;
import com.saas.admin.code.repository.CommonCodeRepository;
import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.homenotice.domain.HomeNotice;
import com.saas.admin.homenotice.dto.HomeNoticeDtos.*;
import com.saas.admin.homenotice.repository.HomeNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홈페이지 공지사항.
 * <p>
 * 공개 조회와 관리자 조회를 <b>같은 메서드로 처리하지 않는다.</b> 초안(published='N')이 공개
 * 경로로 새어 나가면 되돌릴 수 없기 때문이다. 공개용은 전용 쿼리로만 읽는다.
 */
@Service
@RequiredArgsConstructor
public class HomeNoticeService {

    /** 분류 선택지가 등록된 공통코드 그룹. */
    public static final String CATEGORY_GROUP = "KCJG_NOTICE_CATEGORY";

    /** 목록에 보일 요약 길이. 카드 두세 줄 분량. */
    private static final int SUMMARY_LENGTH = 180;

    private final HomeNoticeRepository repo;
    private final CommonCodeRepository codeRepository;
    private final AdminAccountRepository adminRepository;

    // ===== 공개 (회사 사이트) =====

    @Transactional(readOnly = true)
    public PageResponse listPublic(String category, String keyword, int page, int size) {
        Page<HomeNotice> found = repo.findPublic(
                blankToNull(category), blankToNull(keyword),
                PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
        return toPageResponse(found);
    }

    /** 상세. 볼 때마다 조회수를 올린다(본문은 건드리지 않는 UPDATE 한 줄). */
    @Transactional
    public Detail getPublic(Long id) {
        HomeNotice n = repo.findByIdAndPublished(id, "Y")
                .orElseThrow(() -> new ApiException(ErrorCode.NOTICE_NOT_FOUND));
        repo.increaseView(id);
        return toDetail(n, n.getViewCount() + 1, true);
    }

    @Transactional(readOnly = true)
    public List<CategoryOption> categories() {
        return codeRepository.findByGroupGroupCodeOrderBySortOrderAscIdAsc(CATEGORY_GROUP).stream()
                .filter(CommonCode::isActive)
                .map(c -> new CategoryOption(c.getCode(), c.getName()))
                .toList();
    }

    // ===== 관리자 콘솔 =====

    @Transactional(readOnly = true)
    public PageResponse listForAdmin(String published, String category, String keyword, int page, int size) {
        String publishedFlag = switch (published == null ? "ALL" : published.toUpperCase()) {
            case "PUBLISHED", "Y" -> "Y";
            case "DRAFT", "N" -> "N";
            default -> null;
        };
        Page<HomeNotice> found = repo.findForAdmin(
                publishedFlag, blankToNull(category), blankToNull(keyword),
                PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
        return toPageResponse(found);
    }

    @Transactional(readOnly = true)
    public Detail getForAdmin(Long id) {
        HomeNotice n = find(id);
        return toDetail(n, n.getViewCount(), false);
    }

    @Transactional
    public Detail create(String empNo, SaveRequest req) {
        String authorName = adminRepository.findById(empNo).map(AdminAccount::getName).orElse(empNo);
        HomeNotice saved = repo.save(HomeNotice.create(
                req.category().trim(), req.title().trim(), req.content(),
                empNo, authorName, req.pinned(), req.published()));
        return toDetail(saved, saved.getViewCount(), false);
    }

    @Transactional
    public Detail update(Long id, SaveRequest req) {
        HomeNotice n = find(id);
        n.update(req.category().trim(), req.title().trim(), req.content(), req.pinned(), req.published());
        return toDetail(n, n.getViewCount(), false);
    }

    @Transactional
    public void delete(Long id) {
        repo.delete(find(id));
    }

    // ===== 내부 =====

    private HomeNotice find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOTICE_NOT_FOUND));
    }

    private PageResponse toPageResponse(Page<HomeNotice> found) {
        Map<String, String> labels = categoryLabels();
        List<Summary> content = found.getContent().stream()
                .map(n -> new Summary(
                        n.getId(), n.getCategory(), label(labels, n.getCategory()),
                        n.getTitle(), summarize(n.getContent()),
                        n.isPinned(), n.isPublished(),
                        n.getViewCount(), n.getAuthorName(),
                        n.getPublishedAt(), n.getCreatedAt()))
                .toList();
        return new PageResponse(content, found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages(),
                repo.countByPublished("Y"), repo.countByPublished("N"));
    }

    private Detail toDetail(HomeNotice n, int viewCount, boolean withNeighbors) {
        NeighborRef prev = null;
        NeighborRef next = null;
        if (withNeighbors && n.getPublishedAt() != null) {
            prev = repo.findFirstByPublishedAndPublishedAtLessThanOrderByPublishedAtDesc("Y", n.getPublishedAt())
                    .map(x -> new NeighborRef(x.getId(), x.getTitle())).orElse(null);
            next = repo.findFirstByPublishedAndPublishedAtGreaterThanOrderByPublishedAtAsc("Y", n.getPublishedAt())
                    .map(x -> new NeighborRef(x.getId(), x.getTitle())).orElse(null);
        }
        Map<String, String> labels = categoryLabels();
        return new Detail(
                n.getId(), n.getCategory(), label(labels, n.getCategory()),
                n.getTitle(), n.getContent(),
                n.isPinned(), n.isPublished(),
                viewCount, n.getAuthorName(),
                n.getPublishedAt(), n.getCreatedAt(), n.getUpdatedAt(),
                prev, next);
    }

    private Map<String, String> categoryLabels() {
        return codeRepository.findByGroupGroupCodeOrderBySortOrderAscIdAsc(CATEGORY_GROUP).stream()
                .collect(Collectors.toMap(CommonCode::getCode, CommonCode::getName, (a, b) -> a));
    }

    /** 코드가 지워졌으면 코드값을 그대로 보여준다 — 라벨이 비어 화면이 뚫리는 것보다 낫다. */
    private static String label(Map<String, String> labels, String code) {
        return labels.getOrDefault(code, code);
    }

    /**
     * 목록용 요약. 에디터 HTML 에서 태그를 걷어내고 잘라 쓴다.
     * 별도 컬럼을 두지 않는 이유: 본문만 고치고 요약을 안 고치면 목록이 옛말을 계속 한다.
     */
    static String summarize(String html) {
        if (html == null) return "";
        String text = html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("(?i)</p>|</div>|</li>|</h[1-6]>", " ")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() <= SUMMARY_LENGTH) return text;
        return text.substring(0, SUMMARY_LENGTH).trim() + "…";
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank() || "ALL".equalsIgnoreCase(v)) ? null : v.trim();
    }
}
