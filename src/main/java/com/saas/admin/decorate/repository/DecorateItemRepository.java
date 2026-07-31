package com.saas.admin.decorate.repository;

import com.saas.admin.decorate.domain.DecorateItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecorateItemRepository extends JpaRepository<DecorateItem, Long> {

    List<DecorateItem> findAllByOrderBySortOrderAscIdAsc();

    List<DecorateItem> findByCategory_IdOrderBySortOrderAscIdAsc(Long categoryId);

    boolean existsByCategory_IdAndItemKey(Long categoryId, String itemKey);

    void deleteByCategory_Id(Long categoryId);
}
