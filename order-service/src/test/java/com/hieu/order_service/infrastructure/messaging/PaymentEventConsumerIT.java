package com.hieu.order_service.infrastructure.messaging;

import com.hieu.order_service.AbstractIntegrationTest;
import com.hieu.order_service.application.command.order.CreateOrderCommand;
import com.hieu.order_service.application.handler.order.CreateOrderHandler;
import com.hieu.order_service.domain.model.order.Order;
import com.hieu.order_service.domain.model.order.valueobject.OrderStatus;
import com.hieu.order_service.domain.model.order.valueobject.ReservationId;
import com.hieu.order_service.domain.repository.OrderRepository;
import com.hieu.order_service.infrastructure.rest.client.PaymentServiceClient;
import com.hieu.order_service.infrastructure.rest.client.VoucherServiceClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PaymentEventConsumer — IT")
class PaymentEventConsumerIT extends AbstractIntegrationTest {

    @Autowired CreateOrderHandler  createOrderHandler;
    @Autowired OrderRepository     orderRepository;
    @Autowired PaymentEventConsumer consumer;

    // Inventory uses gRPC in production code — no REST mock needed here.
    @MockitoBean PaymentServiceClient paymentServiceClient;
    @MockitoBean VoucherServiceClient voucherServiceClient;

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private Order createPendingOrder() {
        int s = SEQ.incrementAndGet();
        var cmd = new CreateOrderCommand(
                UUID.randomUUID().toString(),
                List.of(new CreateOrderCommand.ItemCmd(
                        (long) s, "Item " + s, (long) s, "SKU-PAY-" + s, null,
                        BigDecimal.valueOf(50_000), 2)),
                "Tran Thi B", "0912345678",
                "45 Nguyen Hue", "Ben Nghe", "District 1", "HCMC", "VN", "70000",
                "BANK_TRANSFER", null, null,
                "idem-pay-" + s + "-" + System.nanoTime(),
                null);
        var order = createOrderHandler.buildAndSave(cmd);
        order.markInventoryReserved(ReservationId.of("res-pay-" + s));
        order.markPaymentPending();
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("payment.completed → order transitions to CONFIRMED")
    void paymentCompleted_event_transitionsOrder() {
        var order = createPendingOrder();
        var orderNumber = order.getOrderNumber().value();

        var payload = Map.<String, Object>of("orderId", orderNumber);
        var record = new ConsumerRecord<String, Object>("payment.completed", 0, 0L, null, payload);

        consumer.onPaymentEvent(payload, record);

        var updated = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("payment event thiếu orderId → dropped, không exception")
    void paymentEvent_missingOrderId_dropped() {
        var payload = Map.<String, Object>of("reason", "no-id-here");
        var record = new ConsumerRecord<String, Object>("payment.completed", 0, 0L, null, payload);

        // Consumer logs a warning and returns — must NOT throw
        assertThatCode(() -> consumer.onPaymentEvent(payload, record))
                .doesNotThrowAnyException();
    }
}
