package com.saas.admin.menu;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.menu.domain.AdminMenu;
import com.saas.admin.menu.dto.MenuDtos.CreateMenuRequest;
import com.saas.admin.menu.dto.MenuDtos.MenuResponse;
import com.saas.admin.menu.dto.MenuDtos.MoveMenuRequest;
import com.saas.admin.menu.dto.MenuDtos.UpdateMenuRequest;
import com.saas.admin.menu.repository.AdminMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 콘솔 상단 메뉴 관리.
 * <p>
 * 규칙은 전부 여기서 강제한다 (DB 는 FK 만 보장한다):
 * <ul>
 *   <li>2단까지만 — 하위 메뉴의 하위는 만들 수 없다</li>
 *   <li>같은 위치(형제)에 같은 이름 금지 — DB 유니크로는 못 막는다
 *       (최상위는 parent_id 가 NULL 이라 유니크 인덱스에서 충돌하지 않는다)</li>
 *   <li>하위 메뉴가 있으면 삭제 불가 — 실수로 묶음째 날리는 것을 막는다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final AdminMenuRepository menuRepository;

    /** 상단 표시·관리 화면 공용. 최상위 메뉴 목록에 children 이 조립돼 나간다. */
    @Transactional(readOnly = true)
    public List<MenuResponse> tree() {
        List<AdminMenu> all = menuRepository.findAllByOrderBySortOrderAscIdAsc();
        return all.stream()
                .filter(AdminMenu::isTopLevel)
                .map(top -> MenuResponse.of(top, all.stream()
                        .filter(m -> Objects.equals(m.parentId(), top.getId()))
                        .map(child -> MenuResponse.of(child, List.of()))
                        .toList()))
                .toList();
    }

    @Transactional
    public MenuResponse create(CreateMenuRequest request) {
        AdminMenu parent = resolveParent(request.parentId());
        List<AdminMenu> all = menuRepository.findAllByOrderBySortOrderAscIdAsc();
        rejectDuplicateName(all, request.parentId(), request.name(), null);

        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : nextSortOrder(all, request.parentId());
        AdminMenu saved = menuRepository.save(AdminMenu.create(parent, request.name(), request.url(), sortOrder));
        return MenuResponse.of(saved, List.of());
    }

    @Transactional
    public MenuResponse update(Long id, UpdateMenuRequest request) {
        AdminMenu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_NOT_FOUND));

        if (Objects.equals(request.parentId(), id)) {
            throw new ApiException(ErrorCode.MENU_INVALID_PARENT);
        }
        AdminMenu parent = resolveParent(request.parentId());
        // 하위를 거느린 메뉴를 남의 밑으로 넣으면 3단이 된다.
        if (parent != null && menuRepository.existsByParentId(id)) {
            throw new ApiException(ErrorCode.MENU_DEPTH_EXCEEDED);
        }

        List<AdminMenu> all = menuRepository.findAllByOrderBySortOrderAscIdAsc();
        rejectDuplicateName(all, request.parentId(), request.name(), id);

        int sortOrder = request.sortOrder() != null ? request.sortOrder() : menu.getSortOrder();
        menu.update(parent, request.name(), request.url(), sortOrder);
        return MenuResponse.of(menu, List.of());
    }

    /**
     * 드래그앤드랍 이동. 이름·URL 은 그대로 두고 상위·순서만 바꾼다.
     * 이동 후 새 형제들과 (상위가 바뀌었으면) 이전 형제들의 순서를 1부터 다시 매긴다 —
     * 순서에 구멍이 남지 않아 화면의 "순서" 값이 항상 깔끔하다.
     */
    @Transactional
    public void move(Long id, MoveMenuRequest request) {
        AdminMenu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_NOT_FOUND));
        if (Objects.equals(request.parentId(), id)) {
            throw new ApiException(ErrorCode.MENU_INVALID_PARENT);
        }
        AdminMenu parent = resolveParent(request.parentId());
        // 하위를 거느린 메뉴를 남의 밑으로 넣으면 3단이 된다.
        if (parent != null && menuRepository.existsByParentId(id)) {
            throw new ApiException(ErrorCode.MENU_DEPTH_EXCEEDED);
        }

        List<AdminMenu> all = menuRepository.findAllByOrderBySortOrderAscIdAsc();
        rejectDuplicateName(all, request.parentId(), menu.getName(), id);

        Long oldParentId = menu.parentId();

        // 새 형제 목록(자신 제외)의 position 위치에 끼워 넣고 1..n 로 다시 매긴다
        List<AdminMenu> newSiblings = new ArrayList<>(all.stream()
                .filter(m -> Objects.equals(m.parentId(), request.parentId()))
                .filter(m -> !m.getId().equals(id))
                .toList());
        int position = Math.min(request.position(), newSiblings.size());
        newSiblings.add(position, menu);

        menu.moveTo(parent);
        for (int i = 0; i < newSiblings.size(); i++) {
            newSiblings.get(i).changeSortOrder(i + 1);
        }

        // 상위가 바뀌었으면 떠나온 쪽도 다시 매겨 구멍을 없앤다
        if (!Objects.equals(oldParentId, request.parentId())) {
            List<AdminMenu> oldSiblings = all.stream()
                    .filter(m -> Objects.equals(m.parentId(), oldParentId))
                    .filter(m -> !m.getId().equals(id))
                    .toList();
            for (int i = 0; i < oldSiblings.size(); i++) {
                oldSiblings.get(i).changeSortOrder(i + 1);
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        AdminMenu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_NOT_FOUND));
        if (menuRepository.existsByParentId(id)) {
            throw new ApiException(ErrorCode.MENU_HAS_CHILDREN);
        }
        menuRepository.delete(menu);
    }

    /** parentId 검증: 존재해야 하고, 그 자신이 최상위여야 한다(2단 제한). */
    private AdminMenu resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        AdminMenu parent = menuRepository.findById(parentId)
                .orElseThrow(() -> new ApiException(ErrorCode.MENU_PARENT_NOT_FOUND));
        if (!parent.isTopLevel()) {
            throw new ApiException(ErrorCode.MENU_DEPTH_EXCEEDED);
        }
        return parent;
    }

    private void rejectDuplicateName(List<AdminMenu> all, Long parentId, String name, Long selfId) {
        boolean duplicated = all.stream()
                .filter(m -> !Objects.equals(m.getId(), selfId))
                .anyMatch(m -> Objects.equals(m.parentId(), parentId) && m.getName().equals(name));
        if (duplicated) {
            throw new ApiException(ErrorCode.MENU_NAME_DUPLICATED);
        }
    }

    private int nextSortOrder(List<AdminMenu> all, Long parentId) {
        return all.stream()
                .filter(m -> Objects.equals(m.parentId(), parentId))
                .mapToInt(AdminMenu::getSortOrder)
                .max()
                .orElse(0) + 1;
    }
}
