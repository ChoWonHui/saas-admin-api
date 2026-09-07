package com.saas.admin.tenant.consolemenu;

import com.saas.admin.tenant.consolemenu.domain.TenantMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 업체 콘솔 메뉴/권한 DTO 모음. */
public final class TenantMenuDtos {

    private TenantMenuDtos() {
    }

    /**
     * 요금제 한 칸의 노출 설정.
     *
     * <p>메뉴 × 요금제 × 역할 3차원이라 요금제마다 역할을 따로 정한다.
     * 셋 다 false 면 그 요금제에는 이 메뉴가 없다는 뜻이다.
     */
    @Schema(description = "요금제 하나에서의 역할별 노출")
    public record PlanAccess(
            @NotNull Long planId,
            @Schema(description = "대표에게 노출") boolean allowOwner,
            @Schema(description = "홀에게 노출") boolean allowHall,
            @Schema(description = "주방에게 노출") boolean allowKitchen
    ) {
    }

    @Schema(description = "업체 콘솔 메뉴 추가/수정. 노출은 요금제별로 역할 3칸을 정한다.")
    public record TenantMenuRequest(
            @NotBlank(message = "메뉴명은 필수입니다.")
            @Size(max = 40, message = "메뉴명은 40자를 넘을 수 없습니다.")
            String name,

            @NotBlank(message = "URL 은 필수입니다.")
            @Size(max = 200, message = "URL 은 200자를 넘을 수 없습니다.")
            String url,

            @Size(max = 40, message = "아이콘 이름이 너무 깁니다.")
            String icon,

            @Schema(description = "요금제별 노출 설정. 생략하면 기존 설정을 그대로 둔다.")
            List<PlanAccess> plans
    ) {
    }

    @Schema(description = "메뉴 순서 변경 — 표시 순서대로 tenant_menu_id 배열.")
    public record TenantMenuReorderRequest(List<Long> orderedIds) {
    }

    /** 관리자용 — 요금제별 노출까지 담은 메뉴. */
    public record TenantMenuView(Long id, String name, String url, String icon, int sortOrder,
                                 List<PlanAccess> plans) {

        /**
         * 화면이 체크박스를 그리려면 꺼진 칸도 있어야 한다.
         * 저장은 켜진 칸만 하므로, 여기서 전체 요금제 목록에 맞춰 빈 칸을 채워 준다.
         */
        public static TenantMenuView of(TenantMenu m, List<Long> allPlanIds) {
            List<PlanAccess> rows = allPlanIds.stream()
                    .map(planId -> m.accessOf(planId)
                            .map(r -> new PlanAccess(planId, r.isAllowOwner(), r.isAllowHall(), r.isAllowKitchen()))
                            .orElseGet(() -> new PlanAccess(planId, false, false, false)))
                    .toList();
            return new TenantMenuView(m.getId(), m.getName(), m.getUrl(), m.getIcon(), m.getSortOrder(), rows);
        }
    }

    /** 테넌트 콘솔 네비용 — 로그인한 역할이 볼 수 있는 메뉴만(권한 플래그는 빼고 노출 정보만). */
    public record TenantNavItem(Long id, String name, String url, String icon) {
        public static TenantNavItem of(TenantMenu m) {
            return new TenantNavItem(m.getId(), m.getName(), m.getUrl(), m.getIcon());
        }
    }
}
