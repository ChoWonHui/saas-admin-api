package com.saas.admin.tenant.menu;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.menu.domain.*;
import com.saas.admin.tenant.menu.dto.MenuDtos.*;
import com.saas.admin.tenant.menu.repository.*;
import com.saas.admin.tenant.repository.TenantBranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 지점 메뉴판 관리 — 분류/메뉴/옵션 CRUD + 다른 지점 메뉴 복사. */
@Service
@RequiredArgsConstructor
public class TenantMenuService {

    private final MenuCategoryRepository categoryRepo;
    private final MenuItemRepository itemRepo;
    private final MenuOptionGroupRepository groupRepo;
    private final MenuOptionRepository optionRepo;
    private final TenantBranchRepository branchRepo;

    // ==================== 조회 ====================

    @Transactional(readOnly = true)
    public MenuResponse getMenu(Long tenantId, Long branchId) {
        assertBranch(tenantId, branchId);
        return new MenuResponse(buildTree(branchId));
    }

    private List<CategoryNode> buildTree(Long branchId) {
        List<MenuCategory> categories = categoryRepo.findByBranchIdOrderBySortOrderAscIdAsc(branchId);
        List<MenuItem> items = itemRepo.findByBranchIdOrderBySortOrderAscIdAsc(branchId);
        List<Long> itemIds = items.stream().map(MenuItem::getId).toList();
        List<MenuOptionGroup> groups = itemIds.isEmpty() ? List.of()
                : groupRepo.findByItemIdInOrderBySortOrderAscIdAsc(itemIds);
        List<Long> groupIds = groups.stream().map(MenuOptionGroup::getId).toList();
        List<MenuOption> options = groupIds.isEmpty() ? List.of()
                : optionRepo.findByGroupIdInOrderBySortOrderAscIdAsc(groupIds);

        Map<Long, List<MenuOption>> optByGroup = options.stream().collect(Collectors.groupingBy(MenuOption::getGroupId));
        Map<Long, List<MenuOptionGroup>> grpByItem = groups.stream().collect(Collectors.groupingBy(MenuOptionGroup::getItemId));
        Map<Long, List<MenuItem>> itemsByCat = items.stream().collect(Collectors.groupingBy(MenuItem::getCategoryId));

        return categories.stream().map((c) -> new CategoryNode(
                c.getId(), c.getName(), c.getSortOrder(),
                itemsByCat.getOrDefault(c.getId(), List.of()).stream().map((it) -> new ItemNode(
                        it.getId(), it.getCategoryId(), it.getName(), it.getPrice(), it.getDescription(),
                        it.getImageUrl(), it.getYoutubeUrl(), it.isSoldOut(), it.getSortOrder(),
                        grpByItem.getOrDefault(it.getId(), List.of()).stream().map((g) -> new OptionGroupNode(
                                g.getId(), g.getName(), g.isRequired(), g.isMultiple(), g.getSortOrder(),
                                optByGroup.getOrDefault(g.getId(), List.of()).stream().map((o) ->
                                        new OptionNode(o.getId(), o.getName(), o.getExtraPrice(), o.getSortOrder())).toList()
                        )).toList()
                )).toList()
        )).toList();
    }

    // ==================== 분류 ====================

    @Transactional
    public MenuResponse addCategory(Long tenantId, Long branchId, CategoryRequest req) {
        assertBranch(tenantId, branchId);
        categoryRepo.save(MenuCategory.create(branchId, req.name(), categoryRepo.maxSort(branchId) + 1));
        return new MenuResponse(buildTree(branchId));
    }

    @Transactional
    public MenuResponse renameCategory(Long tenantId, Long branchId, Long categoryId, CategoryRequest req) {
        assertBranch(tenantId, branchId);
        MenuCategory c = category(branchId, categoryId);
        c.rename(req.name());
        return new MenuResponse(buildTree(branchId));
    }

    @Transactional
    public MenuResponse deleteCategory(Long tenantId, Long branchId, Long categoryId) {
        assertBranch(tenantId, branchId);
        MenuCategory c = category(branchId, categoryId);
        // 이 분류의 메뉴(옵션 포함) 먼저 정리
        List<MenuItem> items = itemRepo.findByCategoryIdOrderBySortOrderAscIdAsc(categoryId);
        deleteItemsDeep(items);
        categoryRepo.delete(c);
        return new MenuResponse(buildTree(branchId));
    }

    // ==================== 메뉴 ====================

    @Transactional
    public MenuResponse addItem(Long tenantId, Long branchId, Long categoryId, ItemRequest req) {
        assertBranch(tenantId, branchId);
        category(branchId, categoryId); // 존재 확인
        MenuItem item = itemRepo.save(MenuItem.create(branchId, categoryId, req.name(), req.price(),
                req.description(), req.imageUrl(), req.youtubeUrl(), req.soldOut(), itemRepo.maxSort(categoryId) + 1));
        saveOptionGroups(item.getId(), req.optionGroups());
        return new MenuResponse(buildTree(branchId));
    }

    @Transactional
    public MenuResponse updateItem(Long tenantId, Long branchId, Long itemId, ItemRequest req) {
        assertBranch(tenantId, branchId);
        MenuItem item = item(branchId, itemId);
        Long newCat = req.categoryId();
        if (newCat != null) category(branchId, newCat); // 분류 이동 시 존재 확인
        item.update(newCat, req.name(), req.price(), req.description(), req.imageUrl(), req.youtubeUrl(), req.soldOut());
        // 옵션그룹 통째 교체
        clearOptionGroups(List.of(itemId));
        saveOptionGroups(itemId, req.optionGroups());
        return new MenuResponse(buildTree(branchId));
    }

    @Transactional
    public MenuResponse deleteItem(Long tenantId, Long branchId, Long itemId) {
        assertBranch(tenantId, branchId);
        MenuItem item = item(branchId, itemId);
        deleteItemsDeep(List.of(item));
        return new MenuResponse(buildTree(branchId));
    }

    // ==================== 복사 ====================

    /** 다른 지점(같은 업체)의 메뉴판을 이 지점으로 복사(기존 메뉴는 대체). */
    @Transactional
    public MenuResponse copyFrom(Long tenantId, Long branchId, CopyMenuRequest req) {
        assertBranch(tenantId, branchId);
        Long from = req.fromBranchId();
        if (from == null || from.equals(branchId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "복사할 원본 지점을 선택하세요.");
        }
        assertBranch(tenantId, from); // 같은 업체 지점인지 확인

        clearMenu(branchId); // 대상 지점 메뉴 비우기

        List<MenuCategory> srcCats = categoryRepo.findByBranchIdOrderBySortOrderAscIdAsc(from);
        for (MenuCategory sc : srcCats) {
            MenuCategory nc = categoryRepo.save(MenuCategory.create(branchId, sc.getName(), sc.getSortOrder()));
            for (MenuItem si : itemRepo.findByCategoryIdOrderBySortOrderAscIdAsc(sc.getId())) {
                MenuItem ni = itemRepo.save(MenuItem.create(branchId, nc.getId(), si.getName(), si.getPrice(),
                        si.getDescription(), si.getImageUrl(), si.getYoutubeUrl(), si.isSoldOut(), si.getSortOrder()));
                for (MenuOptionGroup sg : groupRepo.findByItemIdOrderBySortOrderAscIdAsc(si.getId())) {
                    MenuOptionGroup ng = groupRepo.save(MenuOptionGroup.of(ni.getId(), sg.getName(),
                            sg.isRequired(), sg.isMultiple(), sg.getSortOrder()));
                    for (MenuOption so : optionRepo.findByGroupIdInOrderBySortOrderAscIdAsc(List.of(sg.getId()))) {
                        optionRepo.save(MenuOption.of(ng.getId(), so.getName(), so.getExtraPrice(), so.getSortOrder()));
                    }
                }
            }
        }
        return new MenuResponse(buildTree(branchId));
    }

    // ==================== 내부 ====================

    private void saveOptionGroups(Long itemId, List<ItemRequest.OptionGroupReq> groups) {
        if (groups == null) return;
        int gsort = 1;
        for (ItemRequest.OptionGroupReq g : groups) {
            if (g.name() == null || g.name().isBlank()) continue;
            MenuOptionGroup savedGroup = groupRepo.save(
                    MenuOptionGroup.of(itemId, g.name(), g.required(), g.multiple(), gsort++));
            int osort = 1;
            if (g.options() != null) {
                for (ItemRequest.OptionReq o : g.options()) {
                    if (o.name() == null || o.name().isBlank()) continue;
                    optionRepo.save(MenuOption.of(savedGroup.getId(), o.name(), o.extraPrice(), osort++));
                }
            }
        }
    }

    private void clearOptionGroups(List<Long> itemIds) {
        if (itemIds.isEmpty()) return;
        List<Long> groupIds = groupRepo.findByItemIdInOrderBySortOrderAscIdAsc(itemIds).stream()
                .map(MenuOptionGroup::getId).toList();
        if (!groupIds.isEmpty()) optionRepo.deleteByGroupIdIn(groupIds);
        groupRepo.deleteByItemIdIn(itemIds);
    }

    private void deleteItemsDeep(List<MenuItem> items) {
        if (items.isEmpty()) return;
        List<Long> itemIds = items.stream().map(MenuItem::getId).toList();
        clearOptionGroups(itemIds);
        itemRepo.deleteAll(items);
    }

    private void clearMenu(Long branchId) {
        List<MenuItem> items = itemRepo.findByBranchIdOrderBySortOrderAscIdAsc(branchId);
        clearOptionGroups(items.stream().map(MenuItem::getId).toList());
        itemRepo.deleteByBranchId(branchId);
        categoryRepo.deleteByBranchId(branchId);
    }

    private void assertBranch(Long tenantId, Long branchId) {
        var branch = branchRepo.findById(branchId)
                .orElseThrow(() -> new ApiException(ErrorCode.BRANCH_NOT_FOUND));
        if (!branch.getTenantId().equals(tenantId)) {
            throw new ApiException(ErrorCode.BRANCH_NOT_FOUND);
        }
    }

    private MenuCategory category(Long branchId, Long categoryId) {
        MenuCategory c = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_CATEGORY_NOT_FOUND));
        if (!c.getBranchId().equals(branchId)) throw new ApiException(ErrorCode.MENU_CATEGORY_NOT_FOUND);
        return c;
    }

    private MenuItem item(Long branchId, Long itemId) {
        MenuItem i = itemRepo.findById(itemId)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_ITEM_NOT_FOUND));
        if (!i.getBranchId().equals(branchId)) throw new ApiException(ErrorCode.MENU_ITEM_NOT_FOUND);
        return i;
    }
}
