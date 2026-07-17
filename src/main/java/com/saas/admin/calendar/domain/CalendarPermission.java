package com.saas.admin.calendar.domain;

import jakarta.persistence.*;

/**
 * 달력 권한 한 건 — "이 주체(부서 or 부서+직책)는 이 권한키를 가진다".
 * <p>
 * permKey = 범위 × 액션: ALL_VIEW / ALL_WRITE / TEAM_VIEW / TEAM_WRITE / PERSONAL_VIEW / PERSONAL_WRITE.
 * 허용 목록(allow-list)이고 Default Deny. 부서가 상한선, 직책이 그 안에서 좁힌다(메뉴 권한과 같은 규칙).
 * <p>
 * subjectType 은 "DEPT"(조직 id) / "DEPT_TITLE"("조직id:직책명") — 문자열로 저장해 enum 컬럼 문제를 피한다.
 */
@Entity
@Table(name = "calendar_permission",
        uniqueConstraints = @UniqueConstraint(name = "uk_cal_perm",
                columnNames = {"subject_type", "subject_key", "perm_key"}))
public class CalendarPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "perm_id")
    private Long id;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "subject_key", nullable = false, length = 100)
    private String subjectKey;

    @Column(name = "perm_key", nullable = false, length = 20)
    private String permKey;

    protected CalendarPermission() {
    }

    public static CalendarPermission of(String subjectType, String subjectKey, String permKey) {
        CalendarPermission p = new CalendarPermission();
        p.subjectType = subjectType;
        p.subjectKey = subjectKey;
        p.permKey = permKey;
        return p;
    }

    public String getPermKey() {
        return permKey;
    }
}
