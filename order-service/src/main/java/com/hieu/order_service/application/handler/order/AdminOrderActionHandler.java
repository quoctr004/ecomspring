package com.hieu.order_service.application.handler.order;

import com.hieu.order_service.application.common.DomainEventPublisher;
import com.hieu.order_service.application.dto.OrderDTO;
import com.hieu.order_service.application.mapper.OrderDtoMapper;
import com.hieu.order_service.domain.exception.InvalidOrderStateException;
import com.hieu.order_service.domain.exception.OrderNotFoundException;
import com.hieu.order_service.domain.model.order.valueobject.OrderId;
import com.hieu.order_service.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only manual state transitions on an order. Mirrors the domain methods
 * on {@link com.hieu.order_service.domain.model.order.Order} one-to-one — the
 * Kafka-driven happy path (payment.completed → confirm → ship → deliver) still
 * works, this handler just lets a human nudge the state when needed.
 *
 * <p>Each call goes through the aggregate's own state machine so an admin
 * can't, say, deliver an order that was never shipped. Failures surface as
 * {@link InvalidOrderStateException} → 400.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderActionHandler {

    public enum Action { CONFIRM, SHIP, DELIVER }

    private final OrderRepository orderRepository;
    private final OrderDtoMapper mapper;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public OrderDTO apply(Long orderId, Action action, Long shipmentId) {
        var order = orderRepository.findById(OrderId.of(orderId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        switch (action) {
            case CONFIRM -> {
                // COD orders sit at PAYMENT_PENDING until cash is collected on
                // delivery, so admin "Xác nhận đơn" needs to first promote them
                // to PAYMENT_COMPLETED (the only state from which confirm() is
                // legal). This mirrors what the SePay/MoMo webhook does
                // automatically — admin just performs the same transition
                // manually for COD.
                if (order.getStatus().name().equals("PAYMENT_PENDING")) {
                    order.markPaymentCompleted();
                }
                order.confirm();
            }
            case SHIP -> {
                if (shipmentId == null) {
                    throw new InvalidOrderStateException("Missing shipmentId for SHIP");
                }
                order.markShipped(shipmentId);
            }
            case DELIVER -> order.markDelivered();
        }
        var saved = orderRepository.save(order);
        eventPublisher.publishEventsOf(saved);
        return mapper.toDto(saved);
    }
}
