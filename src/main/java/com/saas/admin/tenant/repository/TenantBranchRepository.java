package com.saas.admin.tenant.repository;

import com.saas.admin.tenant.domain.TenantBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TenantBranchRepository extends JpaRepository<TenantBranch, Long> {

    /** 한 업체의 지점(호점 순, 삭제 제외). */
    List<TenantBranch> findByTenantIdAndDeletedOrderByBranchNoAsc(Long tenantId, String deleted);

    long countByTenantIdAndDeleted(Long tenantId, String deleted);

    /** 다음 호점 번호 = 현재 최댓값 + 1. 삭제된 호점 번호는 재사용하지 않으려고 전체에서 구한다. */
    @Query("select coalesce(max(b.branchNo), 0) from TenantBranch b where b.tenantId = :tenantId")
    int maxBranchNo(@Param("tenantId") Long tenantId);

    /** 여러 업체의 지점 수를 한 번에(N+1 회피). 결과: [tenantId, count] (삭제 제외). */
    @Query("select b.tenantId, count(b) from TenantBranch b where b.deleted = 'N' and b.tenantId in :ids group by b.tenantId")
    List<Object[]> countByTenantIdIn(@Param("ids") List<Long> ids);
}
