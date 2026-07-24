package com.saas.admin.tenant.menu.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 메뉴 항목(음식). 분류에 속하고, 옵션그룹을 가질 수 있다. */
@Entity
@Table(name = "menu_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** 유튜브 영상 URL(선택). 손님 주문 화면에서 메뉴 소개 영상으로 보여줄 수 있다. */
    @Column(name = "youtube_url", length = 300)
    private String youtubeUrl;

    /** 품절 여부 'Y'/'N'. */
    @Column(name = "sold_out", nullable = false, length = 1)
    private String soldOut;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static MenuItem create(Long branchId, Long categoryId, String name, int price, String description,
                                  String imageUrl, String youtubeUrl, boolean soldOut, int sortOrder) {
        MenuItem m = new MenuItem();
        m.branchId = branchId;
        m.categoryId = categoryId;
        m.name = name;
        m.price = Math.max(0, price);
        m.description = blankToNull(description);
        m.imageUrl = blankToNull(imageUrl);
        m.youtubeUrl = blankToNull(youtubeUrl);
        m.soldOut = soldOut ? "Y" : "N";
        m.sortOrder = sortOrder;
        return m;
    }

    public void update(Long categoryId, String name, int price, String description, String imageUrl,
                       String youtubeUrl, boolean soldOut) {
        if (categoryId != null) this.categoryId = categoryId;
        if (name != null && !name.isBlank()) this.name = name;
        this.price = Math.max(0, price);
        this.description = blankToNull(description);
        this.imageUrl = blankToNull(imageUrl);
        this.youtubeUrl = blankToNull(youtubeUrl);
        this.soldOut = soldOut ? "Y" : "N";
    }

    public boolean isSoldOut() {
        return "Y".equals(soldOut);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
