package com.saas.admin.calendar.repository;

import com.saas.admin.calendar.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, LocalDate> {

    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    /** 그 해 공휴일을 이미 가져왔는가(하나라도 있으면 가져온 것으로 본다). */
    boolean existsByDateBetween(LocalDate from, LocalDate to);
}
