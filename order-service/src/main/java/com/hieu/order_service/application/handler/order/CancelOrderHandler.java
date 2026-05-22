package com.hieu.order_service.application.handler.order;

import com.hieu.order_service.application.command.order.CancelOrderCommand;
import com.hieu.order_service.application.common.CommandHandler;
import com.hieu.order_service.application.dto.OrderDTO;
import com.hieu.order_service.application.saga.OrderSagaOrchestrator;
import com.hieu.order_service.domain.exception.CancelNotAllowedException;
import com.hieu.order_service.domain.exception.CancelRateLimitedException;
import com.hieu.order_service.domain.exception.OrderNotFoundException;
import com.hieu.order_service.domain.model.order.valueobject.OrderId;
import com.hieu.order_service.domain.model.order.valueobject.OrderStatus;
import com.hieu.order_service.domain.model.order.valueobject.UserId;
import com.hieu.order_service.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Customer-side cancel guardrails:
 * <ul>
 *   <li>Reason required, ≥ 5 chars trimmed — prevents "asd" / empty-cancel spam
 *       and gives ops a paper trail.</li>
 *   <li>Status must be in the early-flow set (pre-CONFIRMED). Admins are
 *       exempt because they may legitimately void CONFIRMED orders for fraud
 *       or operational reasons.</li>
 *   <li>Per-user rate limit of {@link #CANCEL_LIMIT} cancellations every
 *       {@link #WINDOW_HOURS}h — buy-then-cancel churns inventory + vouchers
 *       and is the classic way malicious users grief flash sales.</li>
 * </ul>
 *
 * <p>The saga itself ({@link OrderSagaOrchestrator#executeCancelOrderSaga}) is
 * {@code @Transactional(REQUIRES_NEW)} so we deliberately do not wrap it in
 * an outer transaction — see the existing comment for context.
 */
@Service
@RequiredArgsConstructor
public class CancelOrderHandler implements CommandHandler<CancelOrderCommand, OrderDTO> {

    private static final int CANCEL_LIMIT = 3;
    private static final int WINDOW_HOURS = 24;

    /** Statuses where the customer is still allowed to self-cancel. */
    private static final Set<OrderStatus> CUSTOMER_CANCELLABLE = Set.of(
            OrderStatus.PENDING,
            OrderStatus.INVENTORY_RESERVED,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PAYMENT_FAILED
    );

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator saga;
    private final StringRedisTemplate redis;

    @Override
    public OrderDTO handle(CancelOrderCommand cmd) {
        var order = orderRepository.findById(OrderId.of(cmd.orderId()))
                .orElseThrow(() -> new OrderNotFoundException(cmd.orderId()));

        if (!cmd.isAdmin()) {
            // Owner-only — same check the saga does, but raise it here so the
            // rate-limit and policy errors win over a 403 the saga would emit.
            if (!order.getUserId().value().equals(cmd.requestingUserId())) {
                throw new AccessDeniedException("Order " + cmd.orderId() + " not owned by caller");
            }
            if (!CUSTOMER_CANCELLABLE.contains(order.getStatus())) {
                throw new CancelNotAllowedException(order.getStatus().name());
            }
            String reason = cmd.reason() == null ? "" : cmd.reason().trim();
            if (reason.length() < 5) {
                throw new IllegalArgumentException("Cancel reason must be at least 5 characters");
            }
            assertWithinRateLimit(cmd.requestingUserId());
        }

        return saga.executeCancelOrderSaga(cmd.orderId(), cmd.reason(), cmd.requestingUserId(), cmd.isAdmin());
    }

    /**
     * Atomic per-user cancel counter via Redis INCR + EXPIRE.
     *
     * <p>The previous DB COUNT(*) path was race-prone: two concurrent cancel
     * clicks both saw the pre-increment count and both passed the gate, so a
     * user could exceed the limit by burst-clicking. INCR returns the new
     * value atomically, so the second click sees {@code count > limit} and
     * gets rejected. EXPIRE is only set on the first INCR of the window
     * (when the returned value is 1), giving a true sliding 24h window per
     * user without touching the DB.
     *
     * <p>Fallback to the legacy DB count when Redis is unreachable — the race
     * window during a partial outage is acceptable.
     */
    private void assertWithinRateLimit(String userId) {
        String key = "cancel-rl:" + userId;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofHours(WINDOW_HOURS));
            }
            if (count != null && count > CANCEL_LIMIT) {
                throw new CancelRateLimitedException(CANCEL_LIMIT, WINDOW_HOURS);
            }
            return;
        } catch (CancelRateLimitedException e) {
            throw e;
        } catch (Exception redisDown) {
            // Best-effort fallback — Redis is the primary, DB is the safety net.
            Instant since = Instant.now().minus(WINDOW_HOURS, ChronoUnit.HOURS);
            long recent = orderRepository.countCancelledByUserSince(UserId.of(userId), since);
            if (recent >= CANCEL_LIMIT) {
                throw new CancelRateLimitedException(CANCEL_LIMIT, WINDOW_HOURS);
            }
        }
    }
}
