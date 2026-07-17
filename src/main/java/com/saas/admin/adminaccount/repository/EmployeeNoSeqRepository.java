package com.saas.admin.adminaccount.repository;

import com.saas.admin.adminaccount.domain.EmployeeNoSeq;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeNoSeqRepository extends JpaRepository<EmployeeNoSeq, String> {

    /**
     * 해당 연도 행을 <b>배타적으로 잠그고</b> 읽는다 (SELECT ... FOR UPDATE).
     * 동시에 관리자 두 명을 생성해도 한쪽은 여기서 대기하므로 사번이 겹치지 않는다.
     * 반드시 트랜잭션 안에서 호출해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EmployeeNoSeq s where s.yearPrefix = :yearPrefix")
    Optional<EmployeeNoSeq> findForUpdate(@Param("yearPrefix") String yearPrefix);
}
