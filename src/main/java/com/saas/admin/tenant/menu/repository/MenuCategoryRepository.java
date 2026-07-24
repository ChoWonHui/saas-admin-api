package com.saas.admin.tenant.menu.repository;

import com.saas.admin.tenant.menu.domain.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findByBranchIdOrderBySortOrderAscIdAsc(Long branchId);

    void deleteByBranchId(Long branchId);

    @Query("select coalesce(max(c.sortOrder), 0) from MenuCategory c where c.branchId = :branchId")
    int maxSort(@Param("branchId") Long branchId);
}
