package com.saas.admin.tenant.repository;

import com.saas.admin.tenant.domain.TenantPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantPlanRepository extends JpaRepository<TenantPlan, Long> {

    List<TenantPlan> findByActiveTrueOrderByDisplayOrderAsc();

    List<TenantPlan> findAllByOrderByDisplayOrderAsc();
}
