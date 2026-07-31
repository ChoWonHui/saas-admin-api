package com.saas.admin.tenant.consolemenu.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 사장님 콘솔의 상단 메뉴 한 건. <b>서비스 전역 정책</b>이다 — 우리 서비스를 쓰는 모든 업체가
 * 동일한 메뉴 구성을 공유한다(업체별로 다르지 않다). 역할(대표/홀/주방)별 노출을 정한다.
 * <p>
 * 대표(TENANT_OWNER)는 항상 전 메뉴를 본다. 홀(allowHall)·주방(allowKitchen)은 켜진 메뉴만 본다.
 * 플랫폼 관리자가 전역으로 메뉴를 추가·수정하고 권한을 설정한다.
 */
@Entity
@Table(name = "tenant_menu")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_menu_id")
    private Long id;

    /** 메뉴 이름. 예: "주문", "테이블". */
    @Column(name = "name", nullable = false, length = 40)
    private String name;

    /** 이동 경로. 콘솔 내부(/admin/orders) 또는 외부(https://…). */
    @Column(name = "url", nullable = false, length = 200)
    private String url;

    /** Material Symbols 아이콘 이름(선택). 예: "receipt_long". */
    @Column(name = "icon", length = 40)
    private String icon;

    /** 표시 순서. 작을수록 앞. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 홀(TENANT_MANAGER) 역할에게 노출할지. */
    @Column(name = "allow_hall", nullable = false)
    private boolean allowHall;

    /** 주방(TENANT_STAFF) 역할에게 노출할지. */
    @Column(name = "allow_kitchen", nullable = false)
    private boolean allowKitchen;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static TenantMenu create(String name, String url, String icon, int sortOrder,
                                    boolean allowHall, boolean allowKitchen) {
        TenantMenu m = new TenantMenu();
        m.name = name;
        m.url = url;
        m.icon = blankToNull(icon);
        m.sortOrder = sortOrder;
        m.allowHall = allowHall;
        m.allowKitchen = allowKitchen;
        return m;
    }

    public void update(String name, String url, String icon, boolean allowHall, boolean allowKitchen) {
        this.name = name;
        this.url = url;
        this.icon = blankToNull(icon);
        this.allowHall = allowHall;
        this.allowKitchen = allowKitchen;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
