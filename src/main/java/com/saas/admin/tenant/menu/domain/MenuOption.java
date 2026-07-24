package com.saas.admin.tenant.menu.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 옵션 항목(예: "순한맛" +0원, "매운맛" +500원). */
@Entity
@Table(name = "menu_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 이 옵션 선택 시 가산되는 금액. */
    @Column(name = "extra_price", nullable = false)
    private int extraPrice;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static MenuOption of(Long groupId, String name, int extraPrice, int sortOrder) {
        MenuOption o = new MenuOption();
        o.groupId = groupId;
        o.name = name;
        o.extraPrice = extraPrice;
        o.sortOrder = sortOrder;
        return o;
    }
}
