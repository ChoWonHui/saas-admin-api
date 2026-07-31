package com.saas.admin.order.repository;

import com.saas.admin.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    List<OrderItem> findByOrderIdInOrderByIdAsc(List<Long> orderIds);
}
