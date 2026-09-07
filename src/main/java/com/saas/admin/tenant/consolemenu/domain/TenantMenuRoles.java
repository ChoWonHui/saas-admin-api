package com.saas.admin.tenant.consolemenu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 요금제 한 칸에서의 역할별 노출.
 *
 * <p>메뉴 × 요금제 × 역할 3차원이라 메뉴에 플래그 두 개로는 표현할 수 없다.
 * 같은 "주문" 메뉴라도 베이직에서는 홀까지, 프로에서는 주방까지 열 수 있어야 한다.
 *
 * <p>이 값은 {@link TenantMenu} 안에서 <b>요금제 id 를 키로 하는 Map</b> 으로 들고 있다.
 * Set 으로 두고 equals 를 요금제 id 만으로 정의했더니, Hibernate 가 컬렉션을 다시 쓸 때
 * 행을 지워 버리는 사고가 있었다. Map 은 키(plan_id)가 곧 연결표의 기본키라
 * 값이 바뀌면 그 행만 UPDATE 된다. 되돌리지 말 것.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantMenuRoles {

    /** 대표(TENANT_OWNER)에게 노출. */
    @Column(name = "allow_owner", nullable = false)
    private boolean allowOwner;

    /** 홀(TENANT_MANAGER)에게 노출. */
    @Column(name = "allow_hall", nullable = false)
    private boolean allowHall;

    /** 주방(TENANT_STAFF)에게 노출. */
    @Column(name = "allow_kitchen", nullable = false)
    private boolean allowKitchen;

    public static TenantMenuRoles of(boolean owner, boolean hall, boolean kitchen) {
        TenantMenuRoles r = new TenantMenuRoles();
        r.allowOwner = owner;
        r.allowHall = hall;
        r.allowKitchen = kitchen;
        return r;
    }

    /** 아무 역할에게도 안 보이면 그 요금제엔 이 메뉴가 없는 것이라 행을 남기지 않는다. */
    public boolean isEmpty() {
        return !allowOwner && !allowHall && !allowKitchen;
    }

    public boolean visibleTo(String roleCode) {
        return switch (roleCode == null ? "" : roleCode) {
            case "TENANT_OWNER" -> allowOwner;
            case "TENANT_MANAGER" -> allowHall;
            case "TENANT_STAFF" -> allowKitchen;
            // 알 수 없는 역할이면 대표 전용이 아닌 것만 보여 준다.
            default -> allowHall || allowKitchen;
        };
    }
}
