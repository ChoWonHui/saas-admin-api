package com.saas.admin.decorate.dto;

import com.saas.admin.decorate.domain.DecorateCategory;
import com.saas.admin.decorate.domain.DecorateGroup;
import com.saas.admin.decorate.domain.DecorateItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 가게 꾸미기 카탈로그 요청/응답 DTO 모음. */
public final class DecorateDtos {

    private DecorateDtos() {
    }

    // ===== 요청 =====

    @Schema(description = "소분류 추가. group·categoryKey 는 생성 후 바꿀 수 없다.")
    public record CreateCategoryRequest(
            @Schema(description = "대분류", example = "OBJECT")
            @NotNull(message = "대분류는 필수입니다.")
            DecorateGroup group,

            @Schema(description = "소분류 키(대분류 안에서 유일). 소문자/숫자/_ 또는 대문자.", example = "furniture")
            @NotBlank(message = "소분류 키는 필수입니다.")
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,39}$", message = "소분류 키는 영문으로 시작하는 1~40자 영문/숫자/_ 여야 합니다.")
            String categoryKey,

            @NotBlank(message = "이름은 필수입니다.")
            @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다.")
            String label,

            @Schema(description = "분류 아이콘 — 이모지 또는 이미지 URL")
            @Size(max = 255)
            String icon,

            Integer sortOrder
    ) {
    }

    @Schema(description = "소분류 수정. 키/대분류는 바꿀 수 없다.")
    public record UpdateCategoryRequest(
            @NotBlank(message = "이름은 필수입니다.")
            @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다.")
            String label,

            @Size(max = 255)
            String icon,

            Integer sortOrder,

            @Pattern(regexp = "^[YN]$", message = "사용 여부는 Y 또는 N 이어야 합니다.")
            String useYn
    ) {
    }

    @Schema(description = "항목 추가. itemKey 를 비우면 서버가 자동 생성한다.")
    public record CreateItemRequest(
            @Schema(description = "항목 키(분류 안에서 유일). 비우면 자동 생성.", example = "fridge")
            @Pattern(regexp = "^$|^[A-Za-z][A-Za-z0-9_]{0,39}$", message = "항목 키는 영문으로 시작하는 1~40자 영문/숫자/_ 여야 합니다.")
            String itemKey,

            @NotBlank(message = "이름은 필수입니다.")
            @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다.")
            String label,

            @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "색은 #RRGGBB 형식이어야 합니다.")
            String color,

            @Size(max = 20, message = "이모지가 너무 깁니다.")
            String emoji,

            @Size(max = 20)
            String renderKind,

            Integer sz,
            Integer wallH,

            @Schema(description = "디자인 데이터(JSON). 머리스타일: {\"type\":\"mass\",\"len\":-41}")
            @Size(max = 255)
            String renderData,

            Integer sortOrder
    ) {
    }

    @Schema(description = "항목 수정. 키는 바꿀 수 없다.")
    public record UpdateItemRequest(
            @NotBlank(message = "이름은 필수입니다.")
            @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다.")
            String label,

            @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "색은 #RRGGBB 형식이어야 합니다.")
            String color,

            @Size(max = 20, message = "이모지가 너무 깁니다.")
            String emoji,

            @Size(max = 20)
            String renderKind,

            Integer sz,
            Integer wallH,

            @Size(max = 255)
            String renderData,

            Integer sortOrder,

            @Pattern(regexp = "^[YN]$", message = "사용 여부는 Y 또는 N 이어야 합니다.")
            String useYn
    ) {
    }

    // ===== 응답 =====

    public record ItemResponse(Long id, String itemKey, String label, String color, String emoji,
                               String renderKind, Integer sz, Integer wallH, String renderData, int sortOrder,
                               String useYn, boolean locked) {
        public static ItemResponse from(DecorateItem it) {
            return new ItemResponse(it.getId(), it.getItemKey(), it.getLabel(), it.getColor(), it.getEmoji(),
                    it.getRenderKind(), it.getSz(), it.getWallH(), it.getRenderData(), it.getSortOrder(), it.getUseYn(), it.isLocked());
        }
    }

    public record CategoryResponse(Long id, DecorateGroup group, String groupLabel, String categoryKey,
                                   String label, String icon, int sortOrder, String useYn, boolean locked,
                                   List<ItemResponse> items) {
        public static CategoryResponse of(DecorateCategory c, List<ItemResponse> items) {
            return new CategoryResponse(c.getId(), c.getGroup(), c.getGroup().getLabel(), c.getCategoryKey(),
                    c.getLabel(), c.getIcon(), c.getSortOrder(), c.getUseYn(), c.isLocked(), items);
        }
    }
}
