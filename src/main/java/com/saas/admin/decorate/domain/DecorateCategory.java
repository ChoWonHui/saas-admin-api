package com.saas.admin.decorate.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 가게 꾸미기 소분류 (대분류 안의 분류). 예: OBJECT 대분류의 furniture = 가구.
 * <p>
 * {@code locked='Y'} = 코드가 의존하는 시스템 분류(캐릭터 부위 등) — 삭제·키 변경 불가, 라벨/노출만 바꾼다.
 * 플랫폼 공통 데이터다. 테이블은 Hibernate 가 만든다.
 */
@Entity
@Table(name = "decorate_category",
        uniqueConstraints = @UniqueConstraint(name = "uk_decorate_category__group_key", columnNames = {"group_code", "category_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecorateCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_code", nullable = false, length = 20)
    private DecorateGroup group;

    /** 소분류 키(대분류 안에서 유일). 생성 후 불변. */
    @Column(name = "category_key", nullable = false, length = 40, updatable = false)
    private String categoryKey;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    /** 분류 아이콘 — 이모지(예: 🪑) 또는 이미지 URL(/api/public/decorate/images/{id}). 도구 팔레트 탭에 쓰인다. */
    @Column(name = "icon", length = 255)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 'Y' = 노출 / 'N' = 중지. */
    @Column(name = "use_yn", nullable = false, length = 1)
    private String useYn;

    /** 'Y' = 시스템 분류(코드 의존) — 삭제 불가. */
    @Column(name = "locked", nullable = false, length = 1)
    private String locked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static DecorateCategory create(DecorateGroup group, String categoryKey, String label, String icon, int sortOrder, boolean locked) {
        DecorateCategory c = new DecorateCategory();
        c.group = group;
        c.categoryKey = categoryKey;
        c.label = label;
        c.icon = blankToNull(icon);
        c.sortOrder = sortOrder;
        c.useYn = "Y";
        c.locked = locked ? "Y" : "N";
        return c;
    }

    public void update(String label, String icon, Integer sortOrder, String useYn) {
        this.label = label;
        this.icon = blankToNull(icon);
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (useYn != null) this.useYn = useYn;
    }

    /** 기존 설치 백필용 — 아이콘이 없을 때만 채운다. */
    public void fillIconIfEmpty(String icon) {
        if (this.icon == null || this.icon.isBlank()) this.icon = blankToNull(icon);
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    public boolean isLocked() {
        return "Y".equals(locked);
    }

    public boolean isActive() {
        return "Y".equals(useYn);
    }
}
