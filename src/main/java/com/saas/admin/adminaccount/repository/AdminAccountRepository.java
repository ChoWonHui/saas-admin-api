package com.saas.admin.adminaccount.repository;

import com.saas.admin.adminaccount.domain.AdminAccount;
import com.saas.admin.adminaccount.domain.AdminStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** PK 가 사번(String)이다. ERP 관례대로 사번이 곧 사람의 키다. */
public interface AdminAccountRepository extends JpaRepository<AdminAccount, String> {

    /** 로그인·조회용. 삭제된 계정은 애초에 조회되지 않아야 한다. */
    Optional<AdminAccount> findByEmpNoAndDeleted(String empNo, String deleted);

    Page<AdminAccount> findByDeleted(String deleted, Pageable pageable);

    /** 조직도 조립용 — 살아있는 관리자 전체를 사번 순으로. */
    List<AdminAccount> findByDeletedOrderByEmpNoAsc(String deleted);

    /** 이 조직에 배치된 살아있는 관리자 수. 조직 삭제 가능 여부 판정에 쓴다. */
    long countByOrgIdAndDeleted(Long orgId, String deleted);

    /**
     * 살아있고 로그인 가능한 관리자 수. "마지막 한 명"을 지우거나 정지시키지 못하게 막는 데 쓴다.
     * 이걸 빠뜨리면 아무도 콘솔에 들어올 수 없는 상태를 만들 수 있다.
     */
    long countByDeletedAndStatus(String deleted, AdminStatus status);
}
