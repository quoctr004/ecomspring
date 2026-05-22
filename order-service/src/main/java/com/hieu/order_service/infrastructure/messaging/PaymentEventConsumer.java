package com.hieu.order_service.infrastructure.messaging;

import com.hieu.order_service.application.common.DomainEventPublisher;
import com.hieu.order_service.domain.exception.OrderNotFoundException;
import com.hieu.order_service.domain.model.order.valueobject.OrderNumber;
import com.hieu.order_service.domain.repository.OrderRepository;
import com.hieu.order_service.infrastructure.grpc.client.CartGrpcClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/** Consumes payment events from payment-service and transitions order state. */
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final CartGrpcClient cartGrpcClient;

    @KafkaListener(topics = {"payment.completed", "payment.failed"}, groupId = "order-service")
    @Transactional
    public void onPaymentEvent(Map<String, Object> payload,
                               org.apache.kafka.clients.consumer.ConsumerRecord<String, Object> record) {
        var topic = record.topic();
        var orderNumber = payload.get("orderId") != null ? payload.get("orderId").toString() : null;
        // Malformed payload — ack & drop, retry won't help.
        if (orderNumber == null) { log.warn("Payment event missing orderId, dropping"); return; }

        // Let exceptions propagate so Spring Kafka's DefaultErrorHandler
        // performs configured retry → DLT instead of silently committing the offset
        // and losing the state transition.
        var order = orderRepository.findByOrderNumber(OrderNumber.of(orderNumber))
                .orElseThrow(() -> new OrderNotFoundException(orderNumber));

        if ("payment.completed".equals(topic)) {
            order.markPaymentCompleted();
            order.confirm();
        } else {
            var reason = payload.get("reason") != null ? payload.get("reason").toString() : "Payment failed";
            order.markFailed(reason);
        }

        var saved = orderRepository.save(order);
        eventPublisher.publishEventsOf(saved);

        // On a successful payment, drop the ordered items from the user's cart
        // so the next checkout starts clean. Defer to AFTER_COMMIT so we never
        // wipe the cart if the order state transition rolls back. clearCart()
        // already swallows transport errors — cart can be cleared manually if
        // the gRPC call fails.
        if ("payment.completed".equals(topic)) {
            String userId = saved.getUserId().value();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    cartGrpcClient.clearCart(userId);
                }
            });
        }
    }
}
