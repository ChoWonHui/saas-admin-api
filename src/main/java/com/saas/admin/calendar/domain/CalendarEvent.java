package com.saas.admin.calendar.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 달력 일정.
 * <p>
 * 범위(scope)로 누가 볼 수 있는지가 갈린다:
 * <ul>
 *   <li>ALL — 전체일정. 전사 공개(볼 권한이 있는 사람에게).</li>
 *   <li>TEAM — 팀별일정. {@code orgId} 조직 소속만.</li>
 *   <li>PERSONAL — 개인일정. 작성자 본인만.</li>
 * </ul>
 * scope 는 문자열로 저장한다("ALL"/"TEAM"/"PERSONAL") — enum 컬럼으로 굳어 값 추가가 막히는 것을 피한다.
 */
@Entity
@Table(name = "calendar_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 종료일. 하루짜리면 startDate 와 같다. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 종일 여부. 'Y' 면 시간을 무시한다. */
    @Column(name = "all_day", nullable = false, length = 1)
    private String allDay;

    /** 시작 시각. 종일이면 null. */
    @Column(name = "start_time")
    private java.time.LocalTime startTime;

    /** 종료 시각. 종일이면 null. */
    @Column(name = "end_time")
    private java.time.LocalTime endTime;

    /** "ALL" / "TEAM" / "PERSONAL". */
    @Column(name = "scope", nullable = false, length = 10)
    private String scope;

    /** TEAM 일정일 때 그 팀(조직) id. ALL/PERSONAL 이면 null. */
    @Column(name = "org_id")
    private Long orgId;

    /** 작성자 사번. 개인일정의 주인이자, 수정·삭제 권한의 기준이다. */
    @Column(name = "owner_emp_no", nullable = false, length = 6)
    private String ownerEmpNo;

    @Column(name = "description", length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CalendarEvent create(String title, LocalDate startDate, LocalDate endDate,
                                       boolean allDay, java.time.LocalTime startTime, java.time.LocalTime endTime,
                                       String scope, Long orgId, String ownerEmpNo, String description) {
        CalendarEvent e = new CalendarEvent();
        e.title = title;
        e.startDate = startDate;
        e.endDate = endDate;
        e.applyTime(allDay, startTime, endTime);
        e.scope = scope;
        e.orgId = orgId;
        e.ownerEmpNo = ownerEmpNo;
        e.description = blankToNull(description);
        return e;
    }

    public void update(String title, LocalDate startDate, LocalDate endDate,
                       boolean allDay, java.time.LocalTime startTime, java.time.LocalTime endTime, String description) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        applyTime(allDay, startTime, endTime);
        this.description = blankToNull(description);
    }

    /** 종일이면 시간을 비우고, 아니면 시간을 채운다. */
    private void applyTime(boolean allDay, java.time.LocalTime startTime, java.time.LocalTime endTime) {
        this.allDay = allDay ? "Y" : "N";
        this.startTime = allDay ? null : startTime;
        this.endTime = allDay ? null : endTime;
    }

    public boolean isAllDay() {
        return "Y".equals(allDay);
    }

    public boolean isOwnedBy(String empNo) {
        return ownerEmpNo.equals(empNo);
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
