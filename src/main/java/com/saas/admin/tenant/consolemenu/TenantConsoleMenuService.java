package com.saas.admin.tenant.consolemenu;

import com.saas.admin.common.error.ApiException;
import com.saas.admin.common.error.ErrorCode;
import com.saas.admin.tenant.consolemenu.TenantMenuDtos.*;
import com.saas.admin.tenant.consolemenu.domain.TenantMenu;
import com.saas.admin.tenant.consolemenu.domain.TenantMenuRoles;
import com.saas.admin.tenant.consolemenu.repository.TenantMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사장님 콘솔 메뉴/권한 — <b>서비스 전역 정책</b>. 모든 업체가 동일한 메뉴 구성을 공유하며,
 * <b>요금제마다</b> 역할(대표/홀/주방)별 노출을 정한다. 비어 있으면 기본 메뉴를 심는다.
 */
@Service
@RequiredArgsConstructor
public class TenantConsoleMenuService {

    private final TenantMenuRepository menuRepository;
    private final com.saas.admin.tenant.repository.TenantPlanRepository planRepository;

    /**
     * 화면에 줄 때는 <b>모든 요금제 칸을 채워서</b> 준다.
     * 저장은 켜진 칸만 하지만, 화면은 꺼진 칸도 체크박스로 그려야 해서 빈 칸까지 만들어 준다.
     */
    private TenantMenuView withAllPlans(TenantMenu m) {
        return TenantMenuView.of(m, planIdsInOrder());
    }

    private List<Long> planIdsInOrder() {
        return planRepository.findAll().stream()
                .map(com.saas.admin.tenant.domain.TenantPlan::getId)
                .sorted()
                .toList();
    }

    /**
     * 기본 메뉴 정의: 이름, URL, 아이콘, 홀 노출, 주방 노출.
     * hall/kitchen 은 <b>처음 심을 때의 기본값</b>이다. 심은 뒤로는 요금제별로 따로 관리한다.
     */
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
        List<Long> planIds = planIdsInOrder();
        return load().stream().map(m -> TenantMenuView.of(m, planIds)).toList();
    }

    /**
     * 로그인한 사람이 볼 수 있는 네비 메뉴.
     *
     * 업체가 쓰는 요금제의 칸을 찾아 그 칸의 역할 플래그를 본다.
     * 칸이 없으면 그 요금제에 이 메뉴가 없는 것이라 역할과 무관하게 안 보인다
     * (예: 무료에는 주문·통계 칸이 없다).
     */
    @Transactional
    public List<TenantNavItem> navFor(String roleCode, Long planId) {
        return load().stream()
                .filter(m -> m.accessOf(planId).map(a -> a.visibleTo(roleCode)).orElse(false))
                .map(TenantNavItem::of)
                .toList();
    }

    @Transactional
    public TenantMenuView add(TenantMenuRequest req) {
        List<TenantMenu> existing = load();
        int nextOrder = existing.stream().mapToInt(TenantMenu::getSortOrder).max().orElse(0) + 1;
        TenantMenu saved = menuRepository.save(
                TenantMenu.create(req.name(), req.url(), req.icon(), nextOrder));
        // 노출 설정을 안 보내면 전 요금제 대표 노출로 시작한다. 새 메뉴가 아무에게도 안 보이는 사고를 막는다.
        saved.replacePlanAccess(req.plans() == null ? defaultAccess() : toAccess(req.plans()));
        return withAllPlans(saved);
    }

    @Transactional
    public TenantMenuView update(Long menuId, TenantMenuRequest req) {
        TenantMenu m = require(menuId);
        m.update(req.name(), req.url(), req.icon());
        // plans 가 아예 없으면 "노출 설정은 건드리지 말라" 는 뜻으로 본다.
        // 빈 배열([])은 "아무 데서도 안 보임" 이라는 명시적 지시라 그대로 반영한다.
        if (req.plans() != null) m.replacePlanAccess(toAccess(req.plans()));
        return withAllPlans(m);
    }

    private java.util.Map<Long, TenantMenuRoles> toAccess(List<PlanAccess> rows) {
        java.util.Map<Long, TenantMenuRoles> map = new java.util.LinkedHashMap<>();
        for (PlanAccess r : rows) {
            if (r == null || r.planId() == null) continue;
            map.put(r.planId(), TenantMenuRoles.of(r.allowOwner(), r.allowHall(), r.allowKitchen()));
        }
        return map;
    }

    /** 새 메뉴의 기본 노출 — 모든 요금제에서 대표만. */
    private java.util.Map<Long, TenantMenuRoles> defaultAccess() {
        return seedAccess(false, false);
    }

    /** 모든 요금제에 같은 값을 넣은 노출 맵. */
    private java.util.Map<Long, TenantMenuRoles> seedAccess(boolean hall, boolean kitchen) {
        java.util.Map<Long, TenantMenuRoles> map = new java.util.LinkedHashMap<>();
        planRepository.findAll().forEach(p -> map.put(p.getId(), TenantMenuRoles.of(true, hall, kitchen)));
        return map;
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
            TenantMenu stats = menuRepository.save(TenantMenu.create("통계", "/admin/stats", "bar_chart", order));
            stats.replacePlanAccess(defaultAccess());
            menus = menuRepository.findAllByOrderBySortOrderAscIdAsc();
        }
        return menus;
    }

    /** 기본 8개 메뉴를 심는다(부트스트랩·지연시딩 공용). */
    @Transactional
    public void seedDefaults() {
        int order = 1;
        for (Seed s : DEFAULTS) {
            TenantMenu saved = menuRepository.save(TenantMenu.create(s.name(), s.url(), s.icon(), order++));
            // 처음 심을 때는 모든 요금제에 같은 값으로 넣는다. 이후 요금제별 조정은 화면에서 한다.
            saved.replacePlanAccess(seedAccess(s.hall(), s.kitchen()));
        }
    }

    private TenantMenu require(Long menuId) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_MENU_NOT_FOUND));
    }
}
