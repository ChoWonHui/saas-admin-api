package com.saas.admin.tenant.menu.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 메뉴 분류(예: 메인, 사이드, 음료). 지점별로 관리한다. */
@Entity
@Table(name = "menu_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static MenuCategory create(Long branchId, String name, int sortOrder) {
        MenuCategory c = new MenuCategory();
        c.branchId = branchId;
        c.name = name;
        c.sortOrder = sortOrder;
        return c;
    }

    public void rename(String name) {
        if (name != null && !name.isBlank()) this.name = name;
    }

    public void changeSort(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
