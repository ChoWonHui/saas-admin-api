package com.saas.admin.auth.repository;

import com.saas.admin.auth.domain.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    List<TenantUser> findByUserId(Long userId);

    Optional<TenantUser> findByTenantIdAndUserId(Long tenantId, Long userId);

    // 직원 관리 — 대표/매니저/직원(role 2,3,4) 멤버십.
    List<TenantUser> findByTenantIdAndRoleIdIn(Long tenantId, List<Integer> roleIds);

    boolean existsByTenantIdAndRoleId(Long tenantId, Integer roleId);

    long countByUserId(Long userId);
}
