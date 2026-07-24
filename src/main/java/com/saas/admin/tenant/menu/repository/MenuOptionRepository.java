package com.saas.admin.tenant.menu.repository;

import com.saas.admin.tenant.menu.domain.MenuOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuOptionRepository extends JpaRepository<MenuOption, Long> {

    List<MenuOption> findByGroupIdInOrderBySortOrderAscIdAsc(List<Long> groupIds);

    void deleteByGroupIdIn(List<Long> groupIds);
}
