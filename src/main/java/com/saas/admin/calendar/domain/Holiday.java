package com.saas.admin.calendar.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * 공휴일 한 건. 공공데이터포털(한국천문연구원 특일정보)에서 가져와 캐싱한다.
 * <p>
 * 공휴일은 확정되면 바뀌지 않으므로, 한 해를 한 번 가져와 저장하면 이후 다시 부르지 않는다.
 * 날짜가 곧 키다(하루에 공휴일 하나로 본다 — 대체공휴일도 날짜가 다르다).
 */
@Entity
@Table(name = "holiday")
public class Holiday {

    @Id
    @Column(name = "holiday_date")
    private LocalDate date;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    protected Holiday() {
    }

    public static Holiday of(LocalDate date, String name) {
        Holiday h = new Holiday();
        h.date = date;
        h.name = name;
        return h;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getName() {
        return name;
    }
}
