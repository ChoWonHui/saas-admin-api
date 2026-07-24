package com.saas.admin.tenant.menu.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 메뉴 옵션 그룹(예: "맵기", "사이즈"). 필수 여부·다중선택 여부를 가진다. */
@Entity
@Table(name = "menu_option_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuOptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 반드시 하나 이상 골라야 하는가. 'Y'/'N'. */
    @Column(name = "required", nullable = false, length = 1)
    private String required;

    /** 여러 개 고를 수 있는가(체크박스). 'N'이면 단일선택(라디오). */
    @Column(name = "multiple", nullable = false, length = 1)
    private String multiple;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static MenuOptionGroup of(Long itemId, String name, boolean required, boolean multiple, int sortOrder) {
        MenuOptionGroup g = new MenuOptionGroup();
        g.itemId = itemId;
        g.name = name;
        g.required = required ? "Y" : "N";
        g.multiple = multiple ? "Y" : "N";
        g.sortOrder = sortOrder;
        return g;
    }

    public boolean isRequired() {
        return "Y".equals(required);
    }

    public boolean isMultiple() {
        return "Y".equals(multiple);
    }
}
