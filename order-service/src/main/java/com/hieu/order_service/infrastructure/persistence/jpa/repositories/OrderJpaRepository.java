package com.hieu.order_service.infrastructure.persistence.jpa.repositories;

import com.hieu.order_service.infrastructure.persistence.jpa.entities.OrderJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    @Query("SELECT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<OrderJpaEntity> findByIdWithItems(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<OrderJpaEntity> findByIdWithLock(@Param("id") Long id);

    Optional<OrderJpaEntity> findByOrderNumber(String orderNumber);

    @Query("SELECT o.id FROM OrderJpaEntity o ORDER BY o.createdAt DESC, o.id DESC")
    List<Long> findFirstPageIds(Pageable pageable);

    @Query("SELECT o.id FROM OrderJpaEntity o WHERE (o.createdAt < :createdAt) OR (o.createdAt = :createdAt AND o.id < :id) ORDER BY o.createdAt DESC, o.id DESC")
    List<Long> findIdsAfterCursor(@Param("createdAt") Instant createdAt, @Param("id") Long id, Pageable pageable);

    @Query("SELECT o.id FROM OrderJpaEntity o WHERE o.status = :status ORDER BY o.createdAt DESC, o.id DESC")
    List<Long> findFirstPageIdsByStatus(@Param("status") String status, Pageable pageable);

    @Query("SELECT o.id FROM OrderJpaEntity o WHERE o.status = :status AND ((o.createdAt < :createdAt) OR (o.createdAt = :createdAt AND o.id < :id)) ORDER BY o.createdAt DESC, o.id DESC")
    List<Long> findIdsAfterCursorByStatus(@Param("status") String status, @Param("createdAt") Instant createdAt, @Param("id") Long id, Pageable pageable);

    @Query("SELECT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items WHERE o.id IN :ids")
    List<OrderJpaEntity> findAllByIdInWithItems(@Param("ids") List<Long> ids);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "items")
    Page<OrderJpaEntity> findByUserId(String userId, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "items")
    Page<OrderJpaEntity> findByUserIdAndStatus(String userId, String status, Pageable pageable);

    @Query("SELECT o.id FROM OrderJpaEntity o WHERE o.userId = :userId ORDER BY o.createdAt DESC, o.id DESC")
    List<Long> findFirstPageIdsByUserId(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT o.id FROM OrderJpaEntity o WHERE o.userId = :userId AND ((o.createdAt < :createdAt) OR (o.createdAt = :createdAt AND o.id < :id)) ORDER BY o.createdAt DESC, o.id DESC")
    List<Long> findIdsAfterCursorByUserId(@Param("userId") String userId, @Param("createdAt") Instant createdAt, @Param("id") Long id, Pageable pageable);

    @Query("SELECT COUNT(o) > 0 FROM OrderJpaEntity o JOIN o.items i WHERE o.userId = :userId AND i.productId = :productId AND o.status IN ('DELIVERED', 'RETURNED')")
    boolean existsByUserIdAndProductId(@Param("userId") String userId, @Param("productId") Long productId);

    /**
     * Per-user aggregation for the admin customers list. Counts only
     * "money-realising" statuses so CANCELLED/FAILED don't pad LTV. Pass null
     * to aggregate the whole table; pass a userIds collection to limit to a
     * known set (e.g. a single auth-service page).
     */
    interface CustomerStatsView {
        String getUserId();
        Long getOrderCount();
        java.math.BigDecimal getLifetimeValue();
        Instant getLastOrderAt();
    }

    @Query("""
        SELECT o.userId                AS userId,
               COUNT(o)                AS orderCount,
               SUM(o.totalAmount)      AS lifetimeValue,
               MAX(o.createdAt)        AS lastOrderAt
          FROM OrderJpaEntity o
         WHERE o.status IN ('PAID','CONFIRMED','SHIPPED','DELIVERED')
           AND (:userIds IS NULL OR o.userId IN :userIds)
         GROUP BY o.userId
        """)
    List<CustomerStatsView> aggregateByUser(@Param("userIds") java.util.Collection<String> userIds);

    @Query("""
           SELECT COUNT(o) FROM OrderJpaEntity o
           WHERE o.userId = :userId
             AND o.status = 'CANCELLED'
             AND o.cancelledAt >= :since
           """)
    long countCancelledByUserSince(@Param("userId") String userId,
                                   @Param("since") java.time.Instant since);
}
