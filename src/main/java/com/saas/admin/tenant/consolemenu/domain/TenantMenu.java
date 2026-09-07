package com.saas.admin.tenant.consolemenu.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 사장님 콘솔의 상단 메뉴 한 건. <b>서비스 전역 정책</b>이다. 우리 서비스를 쓰는 모든 업체가
 * 동일한 메뉴 구성을 공유한다(업체별로 다르지 않다).
 * <p>
 * 노출은 <b>요금제마다 역할별로</b> 정한다({@link TenantMenuPlanAccess}).
 * 같은 "주문" 메뉴라도 베이직에서는 홀까지, 프로에서는 주방까지 열 수 있다.
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

    /**
     * 요금제별 역할 노출. 연결표(tenant_menu_plan)의 한 행이 한 칸이고, 키가 요금제 id 다.
     *
     * "무료는 메뉴판만" 처럼 요금제마다 자유롭게 조합할 수 있어야 해서 다대다로 뒀다.
     * 어느 요금제에도 칸이 없으면 그 메뉴는 아무 데서도 보이지 않는다.
     *
     * <p><b>Map 인 이유</b>: Set 으로 두면 Hibernate 가 값이 바뀔 때 행을 지우고 다시 넣는데,
     * 그 과정에서 행이 사라지는 사고를 겪었다. Map 은 키가 연결표의 기본키와 같아
     * 값만 바뀌면 그 행을 UPDATE 한다.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_menu_plan",
            joinColumns = @JoinColumn(name = "tenant_menu_id"))
    @MapKeyColumn(name = "plan_id")
    private java.util.Map<Long, TenantMenuRoles> planAccess = new java.util.LinkedHashMap<>();

    /**
     * 요금제별 노출을 통째로 바꾼다. 화면의 체크 상태가 그대로 들어온다.
     * 셋 다 꺼진 칸은 저장하지 않는다 — 그 요금제엔 이 메뉴가 없다는 뜻이라 행을 남길 이유가 없다.
     */
    public void replacePlanAccess(java.util.Map<Long, TenantMenuRoles> access) {
        planAccess.clear();
        if (access != null) {
            access.forEach((planId, roles) -> {
                if (planId != null && roles != null && !roles.isEmpty()) planAccess.put(planId, roles);
            });
        }
    }

    /** 이 요금제에서의 노출 설정. 없으면 그 요금제에 이 메뉴가 없다는 뜻이다. */
    public java.util.Optional<TenantMenuRoles> accessOf(Long planId) {
        return planId == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(planAccess.get(planId));
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static TenantMenu create(String name, String url, String icon, int sortOrder) {
        TenantMenu m = new TenantMenu();
        m.name = name;
        m.url = url;
        m.icon = blankToNull(icon);
        m.sortOrder = sortOrder;
        return m;
    }

    public void update(String name, String url, String icon) {
        this.name = name;
        this.url = url;
        this.icon = blankToNull(icon);
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
