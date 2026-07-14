package com.saas.admin.tenant.repository;

import com.saas.admin.tenant.domain.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {
}
