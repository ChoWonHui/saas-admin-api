package com.saas.admin.auth.repository;

import com.saas.admin.auth.domain.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    List<TenantUser> findByUserId(Long userId);

    Optional<TenantUser> findByTenantIdAndUserId(Long tenantId, Long userId);
}
