package com.saas.admin.decorate.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 가게 꾸미기 항목(선택지). 소분류에 속한다.
 * <p>
 * 표현에 따라 채워지는 값이 다르다:
 * <ul>
 *   <li>색 기반(벽지·바닥·벽배너·캐릭터 색): {@code color} 채움</li>
 *   <li>도구: {@code renderKind}(sprite/wall/table/counter) + {@code emoji}/{@code sz}/{@code wallH}</li>
 *   <li>캐릭터 머리스타일·모자: {@code renderKind}(hairstyle/hat) + {@code itemKey}가 코드 키(short/chef …)</li>
 * </ul>
 * {@code locked='Y'} = 코드로 그려지는 기본 항목 — 삭제·키 변경 불가, 라벨/노출만 바꾼다.
 */
@Entity
@Table(name = "decorate_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_decorate_item__category_key", columnNames = {"category_id", "item_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecorateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private DecorateCategory category;

    /** 항목 키(분류 안에서 유일). 생성 후 불변. */
    @Column(name = "item_key", nullable = false, length = 40, updatable = false)
    private String itemKey;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    /** 색(HEX). 색 기반 항목에서 채움. */
    @Column(name = "color", length = 20)
    private String color;

    /** 이모지(도구 스프라이트 등). */
    @Column(name = "emoji", length = 20)
    private String emoji;

    /** 표현 방식: sprite / wall / table / counter / hairstyle / hat 등. 비면 색 기반. */
    @Column(name = "render_kind", length = 20)
    private String renderKind;

    /** 스프라이트 크기(도구). */
    @Column(name = "sz")
    private Integer sz;

    /** 가벽 높이(도구 render_kind=wall). */
    @Column(name = "wall_h")
    private Integer wallH;

    /** 디자인 데이터(JSON). 머리스타일 파라미터 등: {"type":"mass","len":-41}. */
    @Column(name = "render_data", length = 255)
    private String renderData;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "use_yn", nullable = false, length = 1)
    private String useYn;

    /** 'Y' = 코드로 그려지는 기본 항목 — 삭제 불가. */
    @Column(name = "locked", nullable = false, length = 1)
    private String locked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static DecorateItem create(DecorateCategory category, String itemKey, String label,
                                      String color, String emoji, String renderKind,
                                      Integer sz, Integer wallH, String renderData, int sortOrder, boolean locked) {
        DecorateItem it = new DecorateItem();
        it.category = category;
        it.itemKey = itemKey;
        it.label = label;
        it.color = blankToNull(color);
        it.emoji = blankToNull(emoji);
        it.renderKind = blankToNull(renderKind);
        it.sz = sz;
        it.wallH = wallH;
        it.renderData = blankToNull(renderData);
        it.sortOrder = sortOrder;
        it.useYn = "Y";
        it.locked = locked ? "Y" : "N";
        return it;
    }

    public void update(String label, String color, String emoji, String renderKind,
                       Integer sz, Integer wallH, String renderData, Integer sortOrder, String useYn) {
        this.label = label;
        this.color = blankToNull(color);
        this.emoji = blankToNull(emoji);
        this.renderKind = blankToNull(renderKind);
        this.sz = sz;
        this.wallH = wallH;
        this.renderData = blankToNull(renderData);
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (useYn != null) this.useYn = useYn;
    }

    /** 이미 심어진 머리스타일 항목을 디자인 데이터로 채우고 잠금 해제(부트스트랩 업그레이드용). */
    public void applyDesignAndUnlock(String renderData) {
        this.renderData = blankToNull(renderData);
        this.locked = "N";
    }

    /** 잠금 해제(부트스트랩 업그레이드용). */
    public void unlock() {
        this.locked = "N";
    }

    public Long getCategoryId() {
        return category.getId();
    }

    public boolean isLocked() {
        return "Y".equals(locked);
    }

    public boolean isActive() {
        return "Y".equals(useYn);
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
