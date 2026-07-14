package com.saas.admin.tenant.repository;

import com.saas.admin.tenant.domain.ReservedSlug;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservedSlugRepository extends JpaRepository<ReservedSlug, Integer> {

    boolean existsBySlug(String slug);
}
