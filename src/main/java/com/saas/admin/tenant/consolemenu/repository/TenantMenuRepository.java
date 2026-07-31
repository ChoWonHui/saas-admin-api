package com.saas.admin.tenant.consolemenu.repository;

import com.saas.admin.tenant.consolemenu.domain.TenantMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantMenuRepository extends JpaRepository<TenantMenu, Long> {

    List<TenantMenu> findAllByOrderBySortOrderAscIdAsc();
}
