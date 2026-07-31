package com.saas.admin.tenant.home.repository;

import com.saas.admin.tenant.home.domain.TenantHome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantHomeRepository extends JpaRepository<TenantHome, Long> {
    Optional<TenantHome> findByTenantId(Long tenantId);
}
