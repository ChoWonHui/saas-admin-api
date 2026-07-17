package com.saas.admin.org.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 조직(부서). 상위→하위 트리 구조이며, 부서장을 한 명 가질 수 있다.
 * <p>
 * <b>부서장 지정 방식</b>: 각 조직이 {@code leaderEmpNo}(부서장 사번)를 든다. 이 사번을 가진
 * 관리자가 그 조직의 팀장이다 — "누가 팀장인가"를 조직이 알고 있으므로, 관리자 쪽 직책 텍스트에
 * 기대지 않는다. 부서장은 그 조직 소속이어야 한다는 규칙은 서비스에서 강제한다.
 * <p>
 * 깊이 제한은 두지 않는다(메뉴의 2단 제한과 다르다) — 회사 조직은 본부→팀→파트처럼 여러 단이다.
 * 다만 순환(자기 조상을 자식으로)만 서비스에서 막는다.
 * <p>
 * 플랫폼 공통 데이터다(테넌트 데이터가 아니다). 테이블은 Hibernate 가 만든다.
 */
@Entity
@Table(name = "organization")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "org_id")
    private Long id;

    /** 상위 조직. null 이면 최상위(회사/본부). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Organization parent;

    /**
     * 부서코드. 조직을 사람이 읽는 식별자로 부른다(예: DEV, HR). 전사 유니크다 —
     * 유일성은 서비스에서 강제한다(이미 만들어진 테이블이라 Hibernate 로 유니크 제약을 못 붙인다).
     */
    @Column(name = "org_code", length = 20)
    private String orgCode;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 부서장 사번. 이 조직 소속 관리자여야 한다(서비스에서 강제).
     * 부서장이 다른 조직으로 옮기거나 퇴사처리되면 여기를 비운다.
     */
    @Column(name = "leader_emp_no", length = 6)
    private String leaderEmpNo;

    /** 같은 상위 아래 형제 순서. 작을수록 앞. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Organization create(Organization parent, String orgCode, String name, int sortOrder) {
        Organization org = new Organization();
        org.parent = parent;
        org.orgCode = orgCode;
        org.name = name;
        org.sortOrder = sortOrder;
        return org;
    }

    /** 이름과 부서코드를 함께 바꾼다. */
    public void update(String orgCode, String name) {
        this.orgCode = orgCode;
        this.name = name;
    }

    public void moveTo(Organization parent) {
        this.parent = parent;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 부서장 지정. 해제하려면 null. */
    public void assignLeader(String empNo) {
        this.leaderEmpNo = empNo;
    }

    public boolean isLeader(String empNo) {
        return leaderEmpNo != null && leaderEmpNo.equals(empNo);
    }

    public boolean isTopLevel() {
        return parent == null;
    }

    public Long parentId() {
        return parent == null ? null : parent.getId();
    }
}
