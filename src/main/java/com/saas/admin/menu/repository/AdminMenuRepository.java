package com.saas.admin.menu.repository;

import com.saas.admin.menu.domain.AdminMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminMenuRepository extends JpaRepository<AdminMenu, Long> {

    /** 트리 조립용 전체 조회. 메뉴는 수십 건을 넘지 않으므로 한 번에 다 읽는다. */
    List<AdminMenu> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByParentId(Long parentId);
}
