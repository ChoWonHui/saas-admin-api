package com.saas.admin.tenant.consolemenu;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.consolemenu.TenantMenuDtos.*;
import com.saas.admin.tenant.consolemenu.domain.TenantMenu;
import com.saas.admin.tenant.consolemenu.repository.TenantMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사장님 콘솔 메뉴/권한 — <b>서비스 전역 정책</b>. 모든 업체가 동일한 메뉴 구성을 공유하며,
 * 역할(대표/홀/주방)별 노출을 정한다. 비어 있으면 기본 8개 메뉴를 심는다. 대표는 항상 전 메뉴를 본다.
 */
@Service
@RequiredArgsConstructor
public class TenantConsoleMenuService {

    private final TenantMenuRepository menuRepository;

    /** 기본 메뉴 정의: 이름, URL, 아이콘, 홀 노출, 주방 노출. (기존 사장님 콘솔 하드코딩 네비와 동일) */
    public record Seed(String name, String url, String icon, boolean hall, boolean kitchen) {
    }

    public static final List<Seed> DEFAULTS = List.of(
            new Seed("홈", "/admin", "cottage", true, true),
            new Seed("주문", "/admin/orders", "receipt_long", true, true),
            new Seed("예약", "/admin/waitlist", "event", true, false),
            new Seed("테이블", "/admin/tables", "table_restaurant", true, false),
            new Seed("메뉴판", "/admin/menu", "restaurant_menu", true, true),
            new Seed("통계", "/admin/stats", "bar_chart", false, false),
            new Seed("직원", "/admin/staff", "group", false, false),
            new Seed("공지사항", "/admin/notices", "campaign", true, true),
            new Seed("문의", "/admin/inquiries", "forum", true, true)
    );

    /** 전역 메뉴 목록(관리자용, 권한 플래그 포함). 비어 있으면 기본 메뉴를 먼저 심는다. */
    @Transactional
    public List<TenantMenuView> list() {
        return load().stream().map(TenantMenuView::of).toList();
    }

    /** 로그인한 역할이 볼 수 있는 네비 메뉴만. owner=전체, hall=allowHall, kitchen=allowKitchen. */
    @Transactional
    public List<TenantNavItem> navFor(String roleCode) {
        boolean owner = "TENANT_OWNER".equals(roleCode);
        boolean hall = "TENANT_MANAGER".equals(roleCode);   // 홀
        boolean kitchen = "TENANT_STAFF".equals(roleCode);  // 주방
        return load().stream()
                .filter(m -> owner
                        || (hall && m.isAllowHall())
                        || (kitchen && m.isAllowKitchen())
                        // 알 수 없는 역할이면 최소한 대표 전용이 아닌 것만
                        || (!owner && !hall && !kitchen && (m.isAllowHall() || m.isAllowKitchen())))
                .map(TenantNavItem::of)
                .toList();
    }

    @Transactional
    public TenantMenuView add(TenantMenuRequest req) {
        List<TenantMenu> existing = load();
        int nextOrder = existing.stream().mapToInt(TenantMenu::getSortOrder).max().orElse(0) + 1;
        TenantMenu saved = menuRepository.save(TenantMenu.create(
                req.name(), req.url(), req.icon(), nextOrder, req.allowHall(), req.allowKitchen()));
        return TenantMenuView.of(saved);
    }

    @Transactional
    public TenantMenuView update(Long menuId, TenantMenuRequest req) {
        TenantMenu m = require(menuId);
        m.update(req.name(), req.url(), req.icon(), req.allowHall(), req.allowKitchen());
        return TenantMenuView.of(m);
    }

    @Transactional
    public void delete(Long menuId) {
        menuRepository.delete(require(menuId));
    }

    /** 순서 변경 — 보낸 id 순서대로 1..n 로 다시 매긴다. */
    @Transactional
    public void reorder(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) return;
        List<TenantMenu> menus = load();
        int order = 1;
        for (Long id : orderedIds) {
            for (TenantMenu m : menus) {
                if (m.getId().equals(id)) {
                    m.changeSortOrder(order++);
                    break;
                }
            }
        }
    }

    /** 전역 메뉴를 읽되, 없으면 기본 메뉴를 심고 반환한다. 새로 추가된 기본 메뉴(통계 등)는 없으면 멱등 보강. */
    @Transactional
    public List<TenantMenu> load() {
        List<TenantMenu> menus = menuRepository.findAllByOrderBySortOrderAscIdAsc();
        if (menus.isEmpty()) {
            seedDefaults();
            menus = menuRepository.findAllByOrderBySortOrderAscIdAsc();
        } else if (menus.stream().noneMatch(m -> "/admin/stats".equals(m.getUrl()))) {
            // 기존 설치에도 통계 메뉴를 추가한다(멱등).
            int order = menus.stream().mapToInt(TenantMenu::getSortOrder).max().orElse(0) + 1;
            menuRepository.save(TenantMenu.create("통계", "/admin/stats", "bar_chart", order, false, false));
            menus = menuRepository.findAllByOrderBySortOrderAscIdAsc();
        }
        return menus;
    }

    /** 기본 8개 메뉴를 심는다(부트스트랩·지연시딩 공용). */
    @Transactional
    public void seedDefaults() {
        int order = 1;
        for (Seed s : DEFAULTS) {
            menuRepository.save(TenantMenu.create(s.name(), s.url(), s.icon(), order++, s.hall(), s.kitchen()));
        }
    }

    private TenantMenu require(Long menuId) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_MENU_NOT_FOUND));
    }
}
