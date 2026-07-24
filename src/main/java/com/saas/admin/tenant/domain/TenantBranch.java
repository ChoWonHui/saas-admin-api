package com.saas.admin.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 업체의 지점(호점). 한 업체(테넌트) 아래 1호점·2호점… 으로 늘어난다.
 * <p>
 * 호점 번호({@code branchNo})는 업체별로 자동 채번한다(기존 최댓값 + 1). 지점은 자체 slug 를 갖지 않고
 * 이름·주소·연락처만 관리한다. 삭제는 삭제여부('Y')로 한다(업체와 동일한 소프트삭제).
 * tenantId 는 FK 를 걸지 않고 Long 으로 둔다 — 다른 도메인과의 느슨한 결합(org/tenant 패턴).
 */
@Entity
@Table(name = "tenant_branch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 호점 번호. 1, 2, 3 … 업체별로 자동 채번. */
    @Column(name = "branch_no", nullable = false)
    private int branchNo;

    /** 지점명(선택). 예: "강남점". 없으면 화면에서 "N호점"으로만 표기한다. */
    @Column(name = "branch_name", length = 100)
    private String name;

    @Column(name = "manager_name", length = 50)
    private String managerName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    /** 포장 전문점 여부. 'Y' 면 테이블 배치가 없다(편집기에서 입력 막힘). */
    @Column(name = "takeout_only", nullable = false, length = 1)
    private String takeoutOnly;

    /** 영업장 층수. 1 이상. 테이블은 각 층 캔버스에 배치된다. */
    @Column(name = "floor_count", nullable = false)
    private int floorCount;

    /** 영업장 배치 캔버스 크기(px). 평수를 넓히면 이 값이 커진다. */
    @Column(name = "canvas_w", nullable = false)
    private int canvasW;

    @Column(name = "canvas_h", nullable = false)
    private int canvasH;

    @Column(name = "is_deleted", nullable = false, length = 1)
    private String deleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static TenantBranch create(Long tenantId, int branchNo, String name, String managerName,
                                      String contactPhone, String postalCode, String address, String addressDetail) {
        TenantBranch b = new TenantBranch();
        b.tenantId = tenantId;
        b.branchNo = branchNo;
        b.name = blankToNull(name);
        b.managerName = blankToNull(managerName);
        b.contactPhone = blankToNull(contactPhone);
        b.postalCode = blankToNull(postalCode);
        b.address = blankToNull(address);
        b.addressDetail = blankToNull(addressDetail);
        b.takeoutOnly = "N";
        b.floorCount = 1;
        b.canvasW = 760;
        b.canvasH = 460;
        b.deleted = "N";
        return b;
    }

    /** 영업장 배치 메타(포장전문점 여부·층수·캔버스 크기)를 갱신한다. */
    public void updateLayoutMeta(boolean takeoutOnly, int floorCount, int canvasW, int canvasH) {
        this.takeoutOnly = takeoutOnly ? "Y" : "N";
        this.floorCount = Math.max(1, floorCount);
        this.canvasW = clamp(canvasW, 400, 2000, 760);
        this.canvasH = clamp(canvasH, 300, 1600, 460);
    }

    private static int clamp(int v, int min, int max, int fallback) {
        if (v <= 0) return fallback;
        return Math.max(min, Math.min(v, max));
    }

    public boolean isTakeoutOnly() {
        return "Y".equals(takeoutOnly);
    }

    public void update(String name, String managerName, String contactPhone,
                       String postalCode, String address, String addressDetail) {
        this.name = blankToNull(name);
        this.managerName = blankToNull(managerName);
        this.contactPhone = blankToNull(contactPhone);
        this.postalCode = blankToNull(postalCode);
        this.address = blankToNull(address);
        this.addressDetail = blankToNull(addressDetail);
    }

    public void markDeleted() {
        this.deleted = "Y";
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return "Y".equals(deleted);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
