package com.saas.admin.tenant.menu.repository;

import com.saas.admin.tenant.menu.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByBranchIdOrderBySortOrderAscIdAsc(Long branchId);

    List<MenuItem> findByCategoryIdOrderBySortOrderAscIdAsc(Long categoryId);

    void deleteByBranchId(Long branchId);

    void deleteByCategoryId(Long categoryId);

    @Query("select coalesce(max(i.sortOrder), 0) from MenuItem i where i.categoryId = :categoryId")
    int maxSort(@Param("categoryId") Long categoryId);
}
