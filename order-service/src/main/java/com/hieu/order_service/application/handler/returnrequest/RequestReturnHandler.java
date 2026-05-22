package com.hieu.order_service.application.handler.returnrequest;

import com.hieu.order_service.application.command.returnrequest.RequestReturnCommand;
import com.hieu.order_service.application.common.CommandHandler;
import com.hieu.order_service.application.common.DomainEventPublisher;
import com.hieu.order_service.application.dto.ReturnRequestDTO;
import com.hieu.order_service.application.mapper.OrderDtoMapper;
import com.hieu.order_service.domain.exception.InvalidOrderStateException;
import com.hieu.order_service.domain.exception.OrderNotFoundException;
import com.hieu.order_service.domain.model.order.ReturnRequest;
import com.hieu.order_service.domain.model.order.valueobject.*;
import com.hieu.order_service.domain.repository.OrderRepository;
import com.hieu.order_service.domain.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RequestReturnHandler implements CommandHandler<RequestReturnCommand, ReturnRequestDTO> {

    /** Storefront return window — 7 days from delivery, after which no returns. */
    private static final Duration RETURN_WINDOW = Duration.ofDays(7);

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final OrderDtoMapper mapper;
    private final DomainEventPublisher eventPublisher;

    @Override
    @Transactional
    public ReturnRequestDTO handle(RequestReturnCommand cmd) {
        var order = orderRepository.findById(OrderId.of(cmd.orderId()))
                .orElseThrow(() -> new OrderNotFoundException(cmd.orderId()));
        if (!order.canBeReturned()) {
            throw new InvalidOrderStateException("Order cannot be returned in state: " + order.getStatus());
        }
        // Enforce the 7-day window measured from delivery. Pre-delivery orders
        // (RETURN_REQUESTED while in transit) fall through with a null
        // deliveredAt — the state machine already gates those.
        if (order.getDeliveredAt() != null) {
            Duration elapsed = Duration.between(order.getDeliveredAt(), Instant.now());
            if (elapsed.compareTo(RETURN_WINDOW) > 0) {
                throw new InvalidOrderStateException(
                        "Đã quá hạn 7 ngày kể từ khi nhận hàng — không thể yêu cầu trả hàng");
            }
        }

        var rr = ReturnRequest.create(
                OrderId.of(cmd.orderId()),
                UserId.of(cmd.userId()),
                ReturnReason.of(cmd.reason()),
                ReturnType.valueOf(cmd.returnType()),
                cmd.images()
        );

        var saved = returnRequestRepository.save(rr);
        saved.raiseCreatedEvent();
        eventPublisher.publishEventsOf(saved);
        return mapper.toReturnDto(saved);
    }
}
