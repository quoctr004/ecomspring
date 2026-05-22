package com.hieu.order_service.infrastructure.persistence.impl;

import com.hieu.order_service.domain.model.order.Order;
import com.hieu.order_service.domain.model.order.valueobject.OrderId;
import com.hieu.order_service.domain.model.order.valueobject.OrderNumber;
import com.hieu.order_service.domain.model.order.valueobject.OrderStatus;
import com.hieu.order_service.domain.model.order.valueobject.UserId;
import com.hieu.order_service.domain.repository.OrderRepository;
import com.hieu.order_service.infrastructure.persistence.jpa.entities.OrderJpaEntity;
import com.hieu.order_service.infrastructure.persistence.jpa.repositories.OrderJpaRepository;
import com.hieu.order_service.infrastructure.persistence.mapper.OrderJpaMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

/**
 * JPA adapter for {@link OrderRepository}. Uses syncGeneratedIds pattern to ensure
 * DB-generated IDs flow back to the in-memory aggregate after save.
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpa;
    private final OrderJpaMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * For UPDATEs, prefer the persistence-context cache: if the saga loaded the same
     * order earlier in this transaction (e.g. {@code findByIdWithLock} in
     * {@code OrderStateTransitioner}), {@link EntityManager#find} returns the managed
     * instance without a new SELECT — items are already initialized on it. Falls back
     * to {@code findByIdWithItems} only on a true cache miss (e.g. {@code REQUIRES_NEW}
     * crossed from a different transaction). For INSERTs ({@code order.getId() == null})
     * we skip the lookup entirely.
     */
    @Override
    public Order save(Order order) {
        OrderJpaEntity existing = null;
        if (order.getId() != null) {
            existing = entityManager.find(OrderJpaEntity.class, order.getId().value());
            if (existing == null) {
                existing = jpa.findByIdWithItems(order.getId().value()).orElse(null);
            }
        }
        var saved = jpa.saveAndFlush(mapper.toJpa(order, existing));
        mapper.syncGeneratedIds(order, saved);
        return order;
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return jpa.findByIdWithItems(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Order> findByIdWithLock(OrderId id) {
        return jpa.findByIdWithLock(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Order> findByOrderNumber(OrderNumber number) {
        return jpa.findByOrderNumber(number.value()).map(mapper::toDomain);
    }

    @Override
    public List<OrderId> findFirstPageIds(int limit) {
        return jpa.findFirstPageIds(PageRequest.of(0, limit)).stream().map(OrderId::of).toList();
    }

    @Override
    public List<OrderId> findIdsAfterCursor(Instant createdAt, Long id, int limit) {
        return jpa.findIdsAfterCursor(createdAt, id, PageRequest.of(0, limit)).stream().map(OrderId::of).toList();
    }

    @Override
    public List<OrderId> findFirstPageIdsByStatus(OrderStatus status, int limit) {
        return jpa.findFirstPageIdsByStatus(status.name(), PageRequest.of(0, limit)).stream().map(OrderId::of).toList();
    }

    @Override
    public List<OrderId> findIdsAfterCursorByStatus(OrderStatus status, Instant createdAt, Long id, int limit) {
        return jpa.findIdsAfterCursorByStatus(status.name(), createdAt, id, PageRequest.of(0, limit)).stream().map(OrderId::of).toList();
    }

    @Override
    public List<Order> findAllByIdsWithItems(List<OrderId> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        var raw = ids.stream().map(OrderId::value).toList();
        var rows = jpa.findAllByIdInWithItems(raw);
        var order = new HashMap<Long, Integer>();
        for (int i = 0; i < raw.size(); i++) order.put(raw.get(i), i);
        return rows.stream()
                .sorted(Comparator.comparingInt(e -> order.getOrDefault(e.getId(), Integer.MAX_VALUE)))
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Page<Order> findByUserId(UserId userId, Pageable pageable) {
        return jpa.findByUserId(userId.value(), pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Order> findByUserIdAndStatus(UserId userId, OrderStatus status, Pageable pageable) {
        return jpa.findByUserIdAndStatus(userId.value(), status.name(), pageable).map(mapper::toDomain);
    }

    @Override
    public List<OrderId> findFirstPageIdsByUserId(UserId userId, int limit) {
        return jpa.findFirstPageIdsByUserId(userId.value(), PageRequest.of(0, limit)).stream().map(OrderId::of).toList();
    }

    @Override
    public List<OrderId> findIdsAfterCursorByUserId(UserId userId, Instant createdAt, Long id, int limit) {
        return jpa.findIdsAfterCursorByUserId(userId.value(), createdAt, id, PageRequest.of(0, limit)).stream().map(OrderId::of).toList();
    }

    @Override
    public boolean existsByUserIdAndProductId(String userId, Long productId) {
        return jpa.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public long countCancelledByUserSince(UserId userId, Instant since) {
        return jpa.countCancelledByUserSince(userId.value(), since);
    }
}
