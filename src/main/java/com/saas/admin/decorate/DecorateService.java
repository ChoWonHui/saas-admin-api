package com.saas.admin.decorate;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.decorate.domain.DecorateCategory;
import com.saas.admin.decorate.domain.DecorateItem;
import com.saas.admin.decorate.dto.DecorateDtos.*;
import com.saas.admin.decorate.domain.DecorateImage;
import com.saas.admin.decorate.repository.DecorateCategoryRepository;
import com.saas.admin.decorate.repository.DecorateImageRepository;
import com.saas.admin.decorate.repository.DecorateItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 가게 꾸미기(store decorate) 카탈로그 관리.
 * <ul>
 *   <li>대분류(고정) → 소분류(분류, CRUD) → 항목(선택지, CRUD)</li>
 *   <li>{@code locked} 분류/항목은 코드가 의존한다 — 삭제·키 변경 불가, 라벨/노출만 바꾼다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DecorateService {

    private final DecorateCategoryRepository categoryRepository;
    private final DecorateItemRepository itemRepository;
    private final DecorateImageRepository imageRepository;

    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 관리 화면용 — 전부(중지 포함). */
    @Transactional(readOnly = true)
    public List<CategoryResponse> catalog() {
        return catalog(false);
    }

    /** 손님·에디터용 — 노출(useYn='Y') 분류/항목만. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> activeCatalog() {
        return catalog(true);
    }

    private List<CategoryResponse> catalog(boolean activeOnly) {
        Map<Long, List<DecorateItem>> byCat = itemRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(it -> !activeOnly || it.isActive())
                .collect(Collectors.groupingBy(DecorateItem::getCategoryId));
        return categoryRepository.findAllByOrderByGroupAscSortOrderAscIdAsc().stream()
                .filter(c -> !activeOnly || c.isActive())
                // 대분류는 enum 선언 순서(벽지·바닥·도구·벽배너·캐릭터)로 정렬.
                .sorted(java.util.Comparator.comparingInt((DecorateCategory c) -> c.getGroup().ordinal())
                        .thenComparingInt(DecorateCategory::getSortOrder)
                        .thenComparing(DecorateCategory::getId))
                .map(c -> CategoryResponse.of(c, byCat.getOrDefault(c.getId(), List.of()).stream()
                        .map(ItemResponse::from).toList()))
                .toList();
    }

    // ===== 소분류 =====

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest req) {
        if (categoryRepository.existsByGroupAndCategoryKey(req.group(), req.categoryKey())) {
            throw new ApiException(ErrorCode.DECORATE_CATEGORY_DUPLICATED);
        }
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : nextCategoryOrder(req.group().name());
        DecorateCategory saved = categoryRepository.save(
                DecorateCategory.create(req.group(), req.categoryKey(), req.label(), req.icon(), sortOrder, false));
        return CategoryResponse.of(saved, List.of());
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest req) {
        DecorateCategory c = getCategory(id);
        c.update(req.label(), req.icon(), req.sortOrder(), req.useYn());
        return CategoryResponse.of(c, itemRepository.findByCategory_IdOrderBySortOrderAscIdAsc(id).stream()
                .map(ItemResponse::from).toList());
    }

    @Transactional
    public void deleteCategory(Long id) {
        DecorateCategory c = getCategory(id);
        if (c.isLocked()) throw new ApiException(ErrorCode.DECORATE_LOCKED);
        itemRepository.deleteByCategory_Id(id);   // 분류를 지우면 그 항목도 함께 지운다
        categoryRepository.delete(c);
    }

    // ===== 항목 =====

    @Transactional
    public ItemResponse createItem(Long categoryId, CreateItemRequest req) {
        DecorateCategory c = getCategory(categoryId);
        String key = (req.itemKey() == null || req.itemKey().isBlank())
                ? generateKey(categoryId) : req.itemKey();
        if (itemRepository.existsByCategory_IdAndItemKey(categoryId, key)) {
            throw new ApiException(ErrorCode.DECORATE_ITEM_DUPLICATED);
        }
        int sortOrder = req.sortOrder() != null ? req.sortOrder() : nextItemOrder(categoryId);
        DecorateItem saved = itemRepository.save(DecorateItem.create(
                c, key, req.label(), req.color(), req.emoji(), req.renderKind(),
                req.sz(), req.wallH(), req.renderData(), sortOrder, false));
        return ItemResponse.from(saved);
    }

    @Transactional
    public ItemResponse updateItem(Long id, UpdateItemRequest req) {
        DecorateItem it = itemRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DECORATE_ITEM_NOT_FOUND));
        it.update(req.label(), req.color(), req.emoji(), req.renderKind(),
                req.sz(), req.wallH(), req.renderData(), req.sortOrder(), req.useYn());
        return ItemResponse.from(it);
    }

    @Transactional
    public void deleteItem(Long id) {
        DecorateItem it = itemRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DECORATE_ITEM_NOT_FOUND));
        if (it.isLocked()) throw new ApiException(ErrorCode.DECORATE_LOCKED);
        itemRepository.delete(it);
    }

    // ===== 이미지(DB 저장, S3 불필요) =====

    @Transactional
    public Long saveImage(String contentType, long size, byte[] data) {
        if (data == null || data.length == 0) throw new ApiException(ErrorCode.FILE_EMPTY);
        if (contentType == null || !IMAGE_TYPES.contains(contentType)) throw new ApiException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        if (size > MAX_IMAGE_SIZE) throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        return imageRepository.save(DecorateImage.create(contentType, data)).getId();
    }

    @Transactional(readOnly = true)
    public DecorateImage getImage(Long id) {
        return imageRepository.findById(id).orElseThrow(() -> new ApiException(ErrorCode.DECORATE_ITEM_NOT_FOUND));
    }

    // ===== 헬퍼 =====

    private DecorateCategory getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DECORATE_CATEGORY_NOT_FOUND));
    }

    private int nextCategoryOrder(String groupName) {
        return categoryRepository.findAllByOrderByGroupAscSortOrderAscIdAsc().stream()
                .filter(c -> c.getGroup().name().equals(groupName))
                .mapToInt(DecorateCategory::getSortOrder).max().orElse(0) + 1;
    }

    private int nextItemOrder(Long categoryId) {
        return itemRepository.findByCategory_IdOrderBySortOrderAscIdAsc(categoryId).stream()
                .mapToInt(DecorateItem::getSortOrder).max().orElse(0) + 1;
    }

    /** 색 항목처럼 키가 무의미할 때 자동 생성 (분류 안에서 유일). */
    private String generateKey(Long categoryId) {
        int n = itemRepository.findByCategory_IdOrderBySortOrderAscIdAsc(categoryId).size() + 1;
        String key;
        do {
            key = "opt" + (n++);
        } while (itemRepository.existsByCategory_IdAndItemKey(categoryId, key));
        return key;
    }
}
