package com.saas.admin.menu.dto;

import com.saas.admin.menu.domain.AdminMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 콘솔 메뉴 관련 요청/응답 DTO 모음. */
public final class MenuDtos {

    private MenuDtos() {
    }

    @Schema(description = "메뉴 추가. parentId 를 주면 그 메뉴의 하위(드롭다운)로 들어간다. 2단까지만 가능하다.")
    public record CreateMenuRequest(
            @Schema(example = "업체")
            @NotBlank(message = "메뉴명은 필수입니다.")
            @Size(max = 50, message = "메뉴명은 50자를 넘을 수 없습니다.")
            String name,

            @Schema(description = "이동할 경로. 콘솔 내부(/tenants) 또는 외부(https://…). 상위(묶음) 메뉴는 비워도 된다.",
                    example = "/tenants")
            @Size(max = 200, message = "URL 은 200자를 넘을 수 없습니다.")
            String url,

            @Schema(description = "상위 메뉴 ID. 없으면 최상위 메뉴가 된다.")
            Long parentId,

            @Schema(description = "표시 순서. 없으면 맨 뒤에 붙는다.")
            Integer sortOrder
    ) {
    }

    @Schema(description = """
            메뉴 수정. **name / url / parentId / sortOrder 를 보낸 값으로 전부 교체한다.**
            (parentId 를 비우면 최상위로 이동한다 — "안 보낸 것"과 "최상위" 를 구분하지 않는다.
            수정 화면이 항상 모든 항목을 보여주고 그대로 보내는 전제다)
            """)
    public record UpdateMenuRequest(
            @NotBlank(message = "메뉴명은 필수입니다.")
            @Size(max = 50, message = "메뉴명은 50자를 넘을 수 없습니다.")
            String name,

            @Size(max = 200, message = "URL 은 200자를 넘을 수 없습니다.")
            String url,

            Long parentId,

            Integer sortOrder
    ) {
    }

    @Schema(description = """
            메뉴 이동 (드래그앤드랍). 이름·URL 은 건드리지 않고 **위치만** 바꾼다.
            서버가 이동 후 새 형제들과 이전 형제들의 순서를 1부터 다시 매긴다.
            """)
    public record MoveMenuRequest(
            @Schema(description = "새 상위 메뉴 ID. 비우면 최상위로 이동한다.")
            Long parentId,

            @Schema(description = "새 형제 목록(자신 제외)에서의 0 기반 위치. 0 = 맨 앞.")
            @NotNull(message = "위치는 필수입니다.")
            @Min(value = 0, message = "위치는 0 이상이어야 합니다.")
            Integer position
    ) {
    }

    public record MenuResponse(
            Long id,
            Long parentId,
            String name,
            String url,
            int sortOrder,
            List<MenuResponse> children
    ) {
        /** 하위 메뉴까지 조립된 응답. children 은 이미 정렬돼 있다. */
        public static MenuResponse of(AdminMenu menu, List<MenuResponse> children) {
            return new MenuResponse(menu.getId(), menu.parentId(), menu.getName(), menu.getUrl(),
                    menu.getSortOrder(), children);
        }
    }
}
