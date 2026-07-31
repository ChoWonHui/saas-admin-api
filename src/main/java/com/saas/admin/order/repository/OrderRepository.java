package com.saas.admin.order.repository;

import com.saas.admin.order.domain.Order;
import com.saas.admin.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<Order> findByTenantIdAndStatusInOrderByCreatedAtDesc(Long tenantId, List<OrderStatus> statuses);

    // 날짜별 페이징 조회(정렬은 Pageable 의 Sort 로 준다).
    Page<Order> findByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<Order> findByTenantIdAndStatusInAndCreatedAtBetween(Long tenantId, List<OrderStatus> statuses,
                                                             LocalDateTime from, LocalDateTime to, Pageable pageable);

    long countByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime from, LocalDateTime to);

    // 매출 통계 — 기간 내 전체 주문(결제 여부는 서비스에서 판정). 기간은 createdAt 기준.
    List<Order> findByTenantIdAndCreatedAtBetweenOrderByCreatedAtAsc(Long tenantId, LocalDateTime from, LocalDateTime to);

    // 미결제(종료/취소/결제완료 제외) 주문 — 계산 화면용.
    List<Order> findByTenantIdAndStatusNotInOrderByCreatedAtAsc(Long tenantId, Collection<OrderStatus> statuses);

    List<Order> findByTenantIdAndTableIdAndStatusNotInOrderByCreatedAtAsc(Long tenantId, Long tableId, Collection<OrderStatus> statuses);
}
