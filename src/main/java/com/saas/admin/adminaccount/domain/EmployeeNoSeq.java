package com.saas.admin.adminaccount.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 연도별 사번 채번기. 한 해에 한 행씩 쌓인다.
 * <p>
 * <b>왜 테이블인가:</b> MySQL 5.7 에는 시퀀스가 없고, AUTO_INCREMENT 는 연도별로 되돌릴 수 없다.
 * {@code SELECT MAX(emp_no)+1} 방식은 동시에 두 명을 만들 때 같은 번호를 발급한다.
 * 이 행을 {@code SELECT ... FOR UPDATE} 로 잠그고 올려야 번호가 겹치지 않는다.
 */
@Entity
@Table(name = "employee_no_seq")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeNoSeq {

    /** 연도 두 자리. '26' = 2026년. */
    @Id
    @Column(name = "year_prefix", length = 2)
    private String yearPrefix;

    /** 그 해에 마지막으로 발급한 순번. 0 이면 아직 아무도 발급받지 않았다. */
    @Column(name = "last_seq", nullable = false)
    private int lastSeq;

    public EmployeeNoSeq(String yearPrefix) {
        this.yearPrefix = yearPrefix;
        this.lastSeq = 0;
    }

    /** 다음 순번을 발급한다. 호출 전에 이 행이 잠겨 있어야 한다. */
    public int next() {
        return ++lastSeq;
    }
}
