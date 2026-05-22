package com.hieu.cart_service.kafka;

import com.hieu.cart_service.service.CartService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka consumer for order-service events.
 *
 * <p>Listens on {@code order.placed} so the cart auto-clears the moment an order
 * has been accepted — the existing OrderSagaOrchestrator comment said this was
 * deferred to {@code payment.completed}, but COD orders never produce that
 * event so the cart would otherwise stay full after a successful checkout.
 *
 * <p>Idempotent: {@link CartService#clearCart} is safe to call on an already
 * empty cart; duplicate Kafka deliveries are no-ops.
 */
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final CartService cartService;

    @KafkaListener(topics = "order.placed", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderPlaced(Map<String, Object> event) {
        var userId = stringField(event, "userId");
        if (userId == null) {
            log.warn("order.placed event missing userId: {}", event);
            return;
        }
        log.info("Auto-clearing cart for userId={} after order.placed", userId);
        cartService.clearCart(userId);
    }

    private static String stringField(Map<String, Object> event, String name) {
        var v = event.get(name);
        return v == null ? null : v.toString();
    }
}
