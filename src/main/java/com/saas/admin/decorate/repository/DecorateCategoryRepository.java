package com.saas.admin.decorate.repository;

import com.saas.admin.decorate.domain.DecorateCategory;
import com.saas.admin.decorate.domain.DecorateGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DecorateCategoryRepository extends JpaRepository<DecorateCategory, Long> {

    List<DecorateCategory> findAllByOrderByGroupAscSortOrderAscIdAsc();

    boolean existsByGroupAndCategoryKey(DecorateGroup group, String categoryKey);

    Optional<DecorateCategory> findByGroupAndCategoryKey(DecorateGroup group, String categoryKey);
}
