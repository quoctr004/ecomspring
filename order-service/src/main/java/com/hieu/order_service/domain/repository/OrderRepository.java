package com.hieu.order_service.domain.repository;

import com.hieu.order_service.domain.model.order.Order;
import com.hieu.order_service.domain.model.order.valueobject.OrderId;
import com.hieu.order_service.domain.model.order.valueobject.OrderNumber;
import com.hieu.order_service.domain.model.order.valueobject.OrderStatus;
import com.hieu.order_service.domain.model.order.valueobject.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(OrderId id);
    Optional<Order> findByIdWithLock(OrderId id);
    Optional<Order> findByOrderNumber(OrderNumber number);
    List<OrderId> findFirstPageIds(int limit);
    List<OrderId> findIdsAfterCursor(Instant createdAt, Long id, int limit);
    List<OrderId> findFirstPageIdsByStatus(OrderStatus status, int limit);
    List<OrderId> findIdsAfterCursorByStatus(OrderStatus status, Instant createdAt, Long id, int limit);
    List<Order> findAllByIdsWithItems(List<OrderId> ids);
    Page<Order> findByUserId(UserId userId, Pageable pageable);
    Page<Order> findByUserIdAndStatus(UserId userId, OrderStatus status, Pageable pageable);
    List<OrderId> findFirstPageIdsByUserId(UserId userId, int limit);
    List<OrderId> findIdsAfterCursorByUserId(UserId userId, Instant createdAt, Long id, int limit);
    boolean existsByUserIdAndProductId(String userId, Long productId);

    /**
     * Counts orders cancelled by the given user since the cutoff. Used to
     * rate-limit customer-initiated cancellations (e.g. cap at 3 per 24h) so
     * the inventory reservation churn doesn't become a DoS vector.
     */
    long countCancelledByUserSince(UserId userId, Instant since);
}
