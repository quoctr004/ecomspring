package com.hieu.order_service.application.handler.order;

import com.hieu.order_service.application.command.order.CreateOrderCommand;
import com.hieu.order_service.application.command.order.CreateOrderFromCartCommand;
import com.hieu.order_service.application.common.CommandHandler;
import com.hieu.order_service.application.dto.OrderDTO;
import com.hieu.order_service.domain.exception.EmptyCartException;
import com.hieu.order_service.infrastructure.grpc.client.CartGrpcClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** Reads cart via gRPC, builds CreateOrderCommand, delegates to CreateOrderHandler. */
@Service
@RequiredArgsConstructor
public class CreateOrderFromCartHandler implements CommandHandler<CreateOrderFromCartCommand, OrderDTO> {

    private final CartGrpcClient cartGrpcClient;
    private final CreateOrderHandler createOrderHandler;

    @Override
    public OrderDTO handle(CreateOrderFromCartCommand cmd) {
        var cartItems = cartGrpcClient.getCartItems(cmd.userId());
        if (cartItems.isEmpty()) throw new EmptyCartException(cmd.userId());

        var items = cartItems.stream().map(ci -> new CreateOrderCommand.ItemCmd(
                ci.productId(), ci.productName(), ci.variantId(),
                ci.variantSku(), ci.variantImage(),
                new BigDecimal(ci.unitPrice()), ci.quantity()
        )).toList();

        var orderCmd = new CreateOrderCommand(
                cmd.userId(), items, cmd.recipientName(), cmd.recipientPhone(),
                cmd.street(), cmd.ward(), cmd.district(), cmd.city(), cmd.country(), cmd.postalCode(),
                cmd.paymentMethod(), cmd.notes(), cmd.voucherCode(), cmd.idempotencyKey(), cmd.authToken()
        );
        OrderDTO dto = createOrderHandler.handle(orderCmd);

        // Order placed successfully → empty the cart immediately. For SePay/MoMo
        // the PaymentEventConsumer also clears on payment.completed, so this is
        // a no-op then; for COD the payment flow may never fire so this is the
        // sole place that wipes the cart. clearCart() swallows transport errors.
        cartGrpcClient.clearCart(cmd.userId());

        return dto;
    }
}
