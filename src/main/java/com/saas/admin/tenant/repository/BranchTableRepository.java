package com.saas.admin.tenant.repository;

import com.saas.admin.tenant.domain.BranchTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchTableRepository extends JpaRepository<BranchTable, Long> {

    List<BranchTable> findByBranchIdOrderByFloorNoAscIdAsc(Long branchId);

    /** 공개 QR 토큰(code)으로 테이블을 찾는다(손님 주문). */
    Optional<BranchTable> findByCode(String code);

    void deleteByBranchId(Long branchId);
}
