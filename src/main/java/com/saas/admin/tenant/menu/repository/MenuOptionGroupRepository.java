package com.saas.admin.tenant.menu.repository;

import com.saas.admin.tenant.menu.domain.MenuOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuOptionGroupRepository extends JpaRepository<MenuOptionGroup, Long> {

    List<MenuOptionGroup> findByItemIdOrderBySortOrderAscIdAsc(Long itemId);

    List<MenuOptionGroup> findByItemIdInOrderBySortOrderAscIdAsc(List<Long> itemIds);

    void deleteByItemId(Long itemId);

    void deleteByItemIdIn(List<Long> itemIds);
}
