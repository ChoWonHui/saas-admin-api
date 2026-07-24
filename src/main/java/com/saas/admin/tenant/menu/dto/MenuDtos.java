package com.saas.admin.tenant.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 메뉴판 요청·응답 DTO. 응답은 분류→메뉴→옵션그룹→옵션 트리. */
public final class MenuDtos {

    private MenuDtos() {
    }

    // ===== 응답 =====
    public record MenuResponse(List<CategoryNode> categories) {
    }

    public record CategoryNode(Long id, String name, int sortOrder, List<ItemNode> items) {
    }

    public record ItemNode(Long id, Long categoryId, String name, int price, String description,
                           String imageUrl, String youtubeUrl, boolean soldOut, int sortOrder,
                           List<OptionGroupNode> optionGroups) {
    }

    public record OptionGroupNode(Long id, String name, boolean required, boolean multiple, int sortOrder,
                                  List<OptionNode> options) {
    }

    public record OptionNode(Long id, String name, int extraPrice, int sortOrder) {
    }

    // ===== 요청 =====
    public record CategoryRequest(
            @NotBlank(message = "분류명은 필수입니다.") @Size(max = 50) String name) {
    }

    /** 메뉴 생성·수정. 옵션그룹은 통째로 교체한다. */
    public record ItemRequest(
            @NotBlank(message = "메뉴명은 필수입니다.") @Size(max = 100) String name,
            int price,
            @Size(max = 500) String description,
            @Size(max = 500) String imageUrl,
            @Size(max = 300) String youtubeUrl,
            boolean soldOut,
            Long categoryId, // 수정 시 분류 이동용(선택)
            List<OptionGroupReq> optionGroups
    ) {
        public record OptionGroupReq(
                @NotBlank @Size(max = 50) String name,
                boolean required,
                boolean multiple,
                List<OptionReq> options
        ) {
        }

        public record OptionReq(@NotBlank @Size(max = 50) String name, int extraPrice) {
        }
    }

    public record CopyMenuRequest(Long fromBranchId) {
    }
}
