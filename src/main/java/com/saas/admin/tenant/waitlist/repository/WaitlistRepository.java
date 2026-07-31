package com.saas.admin.tenant.waitlist.repository;

import com.saas.admin.tenant.waitlist.domain.WaitlistEntry;
import com.saas.admin.tenant.waitlist.domain.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByTenantIdAndStatusInOrderByQueueNoAscIdAsc(Long tenantId, Collection<WaitlistStatus> statuses);
}
