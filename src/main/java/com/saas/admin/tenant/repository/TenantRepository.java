package com.saas.admin.tenant.repository;

import com.saas.admin.tenant.domain.Tenant;
import com.saas.admin.tenant.domain.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsBySlug(String slug);

    Optional<Tenant> findBySlug(String slug);

    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);

    /**
     * 다음 업체 코드 일련번호. SHOP0001 형식에서 숫자부의 최댓값 + 1 을 구한다.
     * 동시 등록 시 같은 값이 나올 수 있으나, uk_tenant__code 가 중복을 막고
     * 서비스가 재시도한다. (TenantService 참조)
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(tenant_code, 5) AS UNSIGNED)), 0) + 1
              FROM tenant
             WHERE tenant_code REGEXP '^SHOP[0-9]+$'
            """, nativeQuery = true)
    long nextTenantCodeSequence();
}
