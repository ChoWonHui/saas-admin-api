package com.saas.admin.menu.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 관리자 콘솔 상단(header) 메뉴.
 * <p>
 * 콘솔 화면의 내비게이션을 코드가 아니라 <b>데이터로</b> 관리한다 — 메뉴 관리 화면에서
 * 추가·제거하고 URL 을 바꾸면 콘솔 상단에 바로 반영된다.
 * <p>
 * 구조는 <b>2단까지만</b> 허용한다 (상위 메뉴 → 드롭다운 하위 메뉴). 3단은 서비스에서 막는다.
 * 상위 메뉴는 URL 없이 "묶음 제목" 역할만 할 수 있다.
 * <p>
 * 플랫폼 공통 데이터다(테넌트 데이터가 아니다) — 격리 기반(BaseTenantEntity) 대상이 아니다.
 * 이 테이블은 Hibernate 가 새로 만든다. (CLAUDE.md §1 — @ManyToOne 이라 FK 도 함께 생긴다)
 */
@Entity
@Table(name = "admin_menu")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "menu_id")
    private Long id;

    /**
     * 상위 메뉴. null 이면 최상위다.
     * 지연 로딩이면 트리 조립 때마다 N+1 이 나므로, 메뉴는 수십 건을 넘지 않는다는 전제로
     * 전체를 한 번에 읽어 메모리에서 조립한다. (MenuService.tree)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private AdminMenu parent;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 이동할 경로. 콘솔 내부 경로(/tenants)나 외부 주소(https://…)를 그대로 담는다.
     * 하위 메뉴를 거느리는 상위 메뉴는 URL 이 없어도 된다(드롭다운 제목 역할).
     */
    @Column(name = "url", length = 200)
    private String url;

    /** 같은 위치(형제) 안에서의 표시 순서. 작을수록 앞. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static AdminMenu create(AdminMenu parent, String name, String url, int sortOrder) {
        AdminMenu menu = new AdminMenu();
        menu.parent = parent;
        menu.name = name;
        menu.url = normalize(url);
        menu.sortOrder = sortOrder;
        return menu;
    }

    public void update(AdminMenu parent, String name, String url, int sortOrder) {
        this.parent = parent;
        this.name = name;
        this.url = normalize(url);
        this.sortOrder = sortOrder;
    }

    /** 드래그앤드랍 이동 — 위치만 바꾼다. 이름·URL 은 건드리지 않는다. */
    public void moveTo(AdminMenu parent) {
        this.parent = parent;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isTopLevel() {
        return parent == null;
    }

    public Long parentId() {
        return parent == null ? null : parent.getId();
    }

    private static String normalize(String url) {
        return (url == null || url.isBlank()) ? null : url.trim();
    }
}
