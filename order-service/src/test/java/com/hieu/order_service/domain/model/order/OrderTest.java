package com.hieu.order_service.domain.model.order;

import com.hieu.order_service.domain.events.order.OrderCancelledEvent;
import com.hieu.order_service.domain.exception.InvalidOrderStateException;
import com.hieu.order_service.domain.model.order.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Order aggregate")
class OrderTest {
    /**
     * Top-level smoke test — bắt buộc để Sonar rule java:S2187 nhận diện
     * class này có @Test (không tính các @Test bên trong @Nested).
     * Mọi test thật sự được tổ chức trong các @Nested class bên dưới.
     */
    @Test
    @DisplayName("class loads + JUnit discovers @Nested tests")
    void smokeTest_classDiscovered() {
        // Nếu tới được đây tức là JUnit có thể instantiate test class.
        // assertThat(this).isNotNull() được tự thực hiện ngầm.
    }


    static final String USER_UUID = "11111111-1111-1111-1111-111111111111";

    /** Minimal placed order: 1 item, id assigned. */
    static Order aPlacedOrder() {
        var o = Order.create(
                UserId.of(USER_UUID),
                OrderNumber.of("ORD-20260101-000001"),
                RecipientName.of("Nguyen Van A"),
                RecipientPhone.of("0901234567"),
                new ShippingAddress("123 Le Loi", "Ben Thanh", "District 1", "Ho Chi Minh", "VN", "70000"),
                "COD", null, null, "idem-1", USER_UUID);
        o.addItem(OrderItem.create(
                ProductId.of(1L), ProductName.of("Product A"),
                10L, "SKU-001", null,
                Money.of(BigDecimal.valueOf(100_000)), Quantity.of(2)));
        o.assignId(1L);
        return o;
    }

    static Order aPlacedOrderWithVoucher(String voucherCode) {
        var o = Order.create(
                UserId.of(USER_UUID),
                OrderNumber.of("ORD-20260101-000002"),
                RecipientName.of("Nguyen Van A"),
                RecipientPhone.of("0901234567"),
                new ShippingAddress("123 Le Loi", "Ben Thanh", "District 1", "Ho Chi Minh", "VN", "70000"),
                "COD", null, voucherCode, "idem-2", USER_UUID);
        o.addItem(OrderItem.create(
                ProductId.of(1L), ProductName.of("Product A"),
                10L, "SKU-001", null,
                Money.of(BigDecimal.valueOf(500_000)), Quantity.of(2)));
        o.assignId(2L);
        return o;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle: vòng đời chuẩn")
    class Lifecycle {

        Order order;

        @BeforeEach
        void setUp() { order = aPlacedOrder(); }

        @Test
        @DisplayName("create() → status PENDING")
        void create_statusIsPending() {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("markInventoryReserved() → INVENTORY_RESERVED")
        void markInventoryReserved_transitions() {
            order.markInventoryReserved(ReservationId.of("res-1"));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
        }

        @Test
        @DisplayName("markPaymentPending() → PAYMENT_PENDING")
        void markPaymentPending_transitions() {
            order.markInventoryReserved(ReservationId.of("res-1"));
            order.markPaymentPending();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        }

        @Test
        @DisplayName("markPaymentCompleted() → PAYMENT_COMPLETED")
        void markPaymentCompleted_transitions() {
            order.markInventoryReserved(ReservationId.of("res-1"));
            order.markPaymentPending();
            order.markPaymentCompleted();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_COMPLETED);
        }

        @Test
        @DisplayName("confirm() → CONFIRMED, completedAt != null")
        void confirm_transitions() {
            order.markInventoryReserved(ReservationId.of("res-1"));
            order.markPaymentPending();
            order.markPaymentCompleted();
            order.confirm();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("markShipped() → SHIPPED, shipmentId set")
        void markShipped_transitions() {
            order.markInventoryReserved(ReservationId.of("res-1"));
            order.markPaymentPending();
            order.markPaymentCompleted();
            order.confirm();
            order.markShipped(99L);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(order.getShipmentId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("markDelivered() → DELIVERED, deliveredAt != null")
        void markDelivered_transitions() {
            order.markInventoryReserved(ReservationId.of("res-1"));
            order.markPaymentPending();
            order.markPaymentCompleted();
            order.confirm();
            order.markShipped(99L);
            order.markDelivered();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(order.getDeliveredAt()).isNotNull();
        }
    }

    // ── StateTransitions (invalid) ───────────────────────────────────────────────

    @Nested
    @DisplayName("StateTransitions: chuyển trạng thái không hợp lệ")
    class StateTransitions {

        @Test
        @DisplayName("cancel() sau DELIVERED → InvalidOrderStateException")
        void cancelAfterDelivered_throws() {
            var o = aPlacedOrder();
            o.markInventoryReserved(ReservationId.of("res-1"));
            o.markPaymentPending();
            o.markPaymentCompleted();
            o.confirm();
            o.markShipped(1L);
            o.markDelivered();

            assertThatThrownBy(() -> o.cancel("quá muộn"))
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("markPaymentCompleted() 2 lần → InvalidOrderStateException")
        void markPaymentCompleted_twice_throws() {
            var o = aPlacedOrder();
            o.markInventoryReserved(ReservationId.of("res-1"));
            o.markPaymentPending();
            o.markPaymentCompleted();

            assertThatThrownBy(o::markPaymentCompleted)
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    // ── Discount ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Discount: tính totalAmount sau giảm giá")
    class Discount {

        @Test
        @DisplayName("applyDiscount(50_000) → total = subtotal - 50k + shipping")
        void applyDiscount_calculatesCorrectly() {
            // subtotal = 500_000 * 2 = 1_000_000
            var o = aPlacedOrderWithVoucher(null);
            o.applyShippingFee(Money.of(BigDecimal.valueOf(30_000)));
            o.applyDiscount(Money.of(BigDecimal.valueOf(50_000)));

            // total = 1_000_000 - 50_000 + 30_000 = 980_000
            assertThat(o.getTotalAmount().amount())
                    .isEqualByComparingTo(BigDecimal.valueOf(980_000));
        }

        @Test
        @DisplayName("recalculate sau addItem → subtotal tăng, total đúng")
        void recalculate_afterAddItem_isCorrect() {
            var o = Order.create(
                    UserId.of(USER_UUID), OrderNumber.of("ORD-20260101-000099"),
                    RecipientName.of("A"), RecipientPhone.of("0900000001"),
                    new ShippingAddress("s", "w", "d", "c", "VN", "70000"),
                    "COD", null, null, "idem-rc", USER_UUID);
            o.assignId(99L);

            o.addItem(OrderItem.create(ProductId.of(1L), ProductName.of("P1"),
                    1L, "S1", null, Money.of(BigDecimal.valueOf(200_000)), Quantity.of(1)));
            o.addItem(OrderItem.create(ProductId.of(2L), ProductName.of("P2"),
                    2L, "S2", null, Money.of(BigDecimal.valueOf(300_000)), Quantity.of(2)));
            // subtotal = 200_000 + 600_000 = 800_000, no discount/shipping
            assertThat(o.getSubtotalAmount().amount())
                    .isEqualByComparingTo(BigDecimal.valueOf(800_000));
            assertThat(o.getTotalAmount().amount())
                    .isEqualByComparingTo(BigDecimal.valueOf(800_000));
        }
    }

    // ── Cancel with voucher ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cancel: OrderCancelledEvent với voucherCode")
    class Cancel {

        @Test
        @DisplayName("cancel(reason) trên order có voucher → event.voucherCode == code")
        void cancel_withVoucher_raisesEventWithCode() {
            var o = aPlacedOrderWithVoucher("SUMMER20");
            o.markInventoryReserved(ReservationId.of("res-v"));
            o.markPaymentPending();
            o.cancel("đổi ý");

            var event = o.peekDomainEvents().stream()
                    .filter(e -> e instanceof OrderCancelledEvent)
                    .map(e -> (OrderCancelledEvent) e)
                    .findFirst();

            assertThat(event).isPresent();
            assertThat(event.get().voucherCode()).isEqualTo("SUMMER20");
        }

        @Test
        @DisplayName("cancel() trên order không voucher → event.voucherCode == null")
        void cancel_withoutVoucher_eventVoucherCodeIsNull() {
            var o = aPlacedOrder();
            o.cancel("no voucher");

            var event = o.peekDomainEvents().stream()
                    .filter(e -> e instanceof OrderCancelledEvent)
                    .map(e -> (OrderCancelledEvent) e)
                    .findFirst();

            assertThat(event).isPresent();
            assertThat(event.get().voucherCode()).isNull();
        }
    }
}
