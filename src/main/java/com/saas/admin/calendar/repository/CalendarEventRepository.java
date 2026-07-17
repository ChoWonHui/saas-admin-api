package com.saas.admin.calendar.repository;

import com.saas.admin.calendar.domain.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    /** 조회 범위(월)와 겹치는 일정: 시작이 범위 끝 이전이고, 종료가 범위 시작 이후. */
    List<CalendarEvent> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate rangeEnd, LocalDate rangeStart);
}
