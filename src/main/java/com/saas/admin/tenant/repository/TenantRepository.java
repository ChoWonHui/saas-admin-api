package com.saas.admin.tenant.repository;

import com.saas.admin.tenant.domain.Tenant;
import com.saas.admin.tenant.domain.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsBySlug(String slug);

    boolean existsByCode(String code);

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByCode(String code);

    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);

    // 삭제여부('N'=미삭제) 를 함께 거른 조회. 관리 목록은 기본적으로 미삭제만 본다.
    Page<Tenant> findByDeleted(String deleted, Pageable pageable);

    Page<Tenant> findByStatusAndDeleted(TenantStatus status, String deleted, Pageable pageable);
}
