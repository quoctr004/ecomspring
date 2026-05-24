package com.hieu.order_service.application.saga;

import com.hieu.order_service.AbstractIntegrationTest;
import com.hieu.order_service.application.command.order.CreateOrderCommand;
import com.hieu.order_service.application.handler.order.CreateOrderHandler;
import com.hieu.order_service.domain.model.order.Order;
import com.hieu.order_service.domain.model.order.valueobject.OrderStatus;
import com.hieu.order_service.domain.repository.OrderRepository;
import com.hieu.order_service.infrastructure.rest.client.PaymentServiceClient;
import com.hieu.order_service.infrastructure.rest.client.VoucherServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OrderSagaOrchestrator — cancel saga IT")
class OrderSagaOrchestratorIT extends AbstractIntegrationTest {

    @Autowired OrderSagaOrchestrator saga;
    @Autowired CreateOrderHandler   createOrderHandler;
    @Autowired OrderRepository      orderRepository;

    // Mock REST clients so we don't need running payment/voucher services.
    // Inventory is reached via gRPC in the saga — mocked at that layer where needed.
    @MockitoBean PaymentServiceClient paymentServiceClient;
    @MockitoBean VoucherServiceClient voucherServiceClient;

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    /** Create a persisted order and return it (stays in PENDING — saga mocked). */
    private Order createPersistedOrder(String userId) {
        int s = SEQ.incrementAndGet();
        var cmd = new CreateOrderCommand(
                userId,
                List.of(new CreateOrderCommand.ItemCmd(
                        (long) s, "Product " + s, (long) s, "SKU-SAGA-" + s, null,
                        BigDecimal.valueOf(100_000), 1)),
                "Nguyen Van A", "0901234567",
                "123 Le Loi", "Ben Thanh", "District 1", "HCMC", "VN", "70000",
                "COD", null, null,
                "idem-saga-" + s + "-" + System.nanoTime(),
                null);
        return createOrderHandler.buildAndSave(cmd);
    }

    @Nested
    @DisplayName("cancelSaga")
    class CancelSaga {

        @Test
        @DisplayName("owner hủy order của mình → CANCELLED")
        void cancelSaga_byOwner_succeeds() {
            String userId = UUID.randomUUID().toString();
            var order = createPersistedOrder(userId);

            var dto = saga.executeCancelOrderSaga(order.getId().value(), "đổi ý", userId, false);

            assertThat(dto.status()).isEqualTo(OrderStatus.CANCELLED.name());
        }

        @Test
        @DisplayName("non-owner hủy order của người khác → AccessDeniedException")
        void cancelSaga_byNonOwner_throwsAccessDenied() {
            String ownerId    = UUID.randomUUID().toString();
            String strangerId = UUID.randomUUID().toString();
            var order = createPersistedOrder(ownerId);

            assertThatThrownBy(() ->
                    saga.executeCancelOrderSaga(order.getId().value(), "hack", strangerId, false))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("admin hủy bất kỳ order → CANCELLED")
        void cancelSaga_byAdmin_succeeds() {
            String ownerId  = UUID.randomUUID().toString();
            String adminId  = UUID.randomUUID().toString();
            var order = createPersistedOrder(ownerId);

            var dto = saga.executeCancelOrderSaga(order.getId().value(), "vi phạm", adminId, true);

            assertThat(dto.status()).isEqualTo(OrderStatus.CANCELLED.name());
        }
    }
}
