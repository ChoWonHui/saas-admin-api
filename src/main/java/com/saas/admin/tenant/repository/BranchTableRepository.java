package com.saas.admin.tenant.repository;

import com.saas.admin.tenant.domain.BranchTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchTableRepository extends JpaRepository<BranchTable, Long> {

    List<BranchTable> findByBranchIdOrderByFloorNoAscIdAsc(Long branchId);

    void deleteByBranchId(Long branchId);
}
