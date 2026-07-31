package com.saas.admin.tenant.consolemenu;

import com.saas.admin.tenant.consolemenu.domain.TenantMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 업체 콘솔 메뉴/권한 DTO 모음. */
public final class TenantMenuDtos {

    private TenantMenuDtos() {
    }

    @Schema(description = "업체 콘솔 메뉴 추가/수정. 대표는 항상 전 메뉴를 보므로 홀/주방 노출만 정한다.")
    public record TenantMenuRequest(
            @NotBlank(message = "메뉴명은 필수입니다.")
            @Size(max = 40, message = "메뉴명은 40자를 넘을 수 없습니다.")
            String name,

            @NotBlank(message = "URL 은 필수입니다.")
            @Size(max = 200, message = "URL 은 200자를 넘을 수 없습니다.")
            String url,

            @Size(max = 40, message = "아이콘 이름이 너무 깁니다.")
            String icon,

            @Schema(description = "홀 역할에게 노출") boolean allowHall,
            @Schema(description = "주방 역할에게 노출") boolean allowKitchen
    ) {
    }

    @Schema(description = "메뉴 순서 변경 — 표시 순서대로 tenant_menu_id 배열.")
    public record TenantMenuReorderRequest(List<Long> orderedIds) {
    }

    /** 관리자용 — 권한 플래그까지 담은 메뉴. */
    public record TenantMenuView(Long id, String name, String url, String icon, int sortOrder,
                                 boolean allowHall, boolean allowKitchen) {
        public static TenantMenuView of(TenantMenu m) {
            return new TenantMenuView(m.getId(), m.getName(), m.getUrl(), m.getIcon(), m.getSortOrder(),
                    m.isAllowHall(), m.isAllowKitchen());
        }
    }

    /** 테넌트 콘솔 네비용 — 로그인한 역할이 볼 수 있는 메뉴만(권한 플래그는 빼고 노출 정보만). */
    public record TenantNavItem(Long id, String name, String url, String icon) {
        public static TenantNavItem of(TenantMenu m) {
            return new TenantNavItem(m.getId(), m.getName(), m.getUrl(), m.getIcon());
        }
    }
}
