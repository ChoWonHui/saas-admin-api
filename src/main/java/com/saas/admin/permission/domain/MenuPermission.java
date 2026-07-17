package com.saas.admin.permission.domain;

import jakarta.persistence.*;

/**
 * 메뉴 권한 한 건 — "이 주체(부서 or 직책)는 이 메뉴에 접근할 수 있다".
 * <p>
 * <b>허용 목록(allow-list)</b>이다. 여기 없는 (주체, 메뉴) 조합은 접근 불가(Default Deny).
 * 사원의 최종 접근 메뉴 = 자기 부서 허용 ∪ 자기 직책 허용 (합집합).
 * <p>
 * 같은 주체·메뉴가 중복되지 않도록 유니크로 막는다.
 */
@Entity
@Table(name = "menu_permission",
        uniqueConstraints = @UniqueConstraint(name = "uk_menu_perm",
                columnNames = {"subject_type", "subject_key", "menu_id"}))
public class MenuPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "perm_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 10)
    private PermissionSubject subjectType;

    /** DEPT 면 조직 id 문자열, DEPT_TITLE 이면 "조직id:직책명". */
    @Column(name = "subject_key", nullable = false, length = 100)
    private String subjectKey;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    protected MenuPermission() {
    }

    public static MenuPermission of(PermissionSubject type, String key, Long menuId) {
        MenuPermission p = new MenuPermission();
        p.subjectType = type;
        p.subjectKey = key;
        p.menuId = menuId;
        return p;
    }

    public Long getMenuId() {
        return menuId;
    }

    public PermissionSubject getSubjectType() {
        return subjectType;
    }

    public String getSubjectKey() {
        return subjectKey;
    }
}
