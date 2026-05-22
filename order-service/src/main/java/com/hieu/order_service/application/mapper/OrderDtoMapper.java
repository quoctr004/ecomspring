package com.hieu.order_service.application.mapper;

import com.hieu.order_service.application.dto.OrderDTO;
import com.hieu.order_service.application.dto.OrderItemDTO;
import com.hieu.order_service.application.dto.ReturnRequestDTO;
import com.hieu.order_service.domain.model.order.Order;
import com.hieu.order_service.domain.model.order.OrderItem;
import com.hieu.order_service.domain.model.order.ReturnRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps domain aggregates to DTOs. No reverse mapping — commands carry raw primitives. */
@Component
public class OrderDtoMapper {

    private final SepayQrHelper sepayQrHelper;

    public OrderDtoMapper(SepayQrHelper sepayQrHelper) {
        this.sepayQrHelper = sepayQrHelper;
    }

    public OrderDTO toDto(Order order) {
        // Re-derive the SePay QR on read so the storefront's resume-payment
        // page (loaded from /api/orders/by-number) still gets a working URL —
        // the saga's create response is the only place the explicit qrCodeUrl
        // is plumbed in.
        String qrCodeUrl = null;
        if ("PAYMENT_PENDING".equals(order.getStatus().name())
                && sepayQrHelper.isBankTransfer(order.getPaymentMethod())) {
            qrCodeUrl = sepayQrHelper.generate(
                    order.getOrderNumber().value(),
                    order.getTotalAmount().amount());
        }
        return toDto(order, null, qrCodeUrl);
    }

    public OrderDTO toDto(Order order, String payUrl, String qrCodeUrl) {
        var addr = order.getShippingAddress();
        return new OrderDTO(
                order.getId() == null ? null : order.getId().value(),
                order.getOrderNumber().value(),
                order.getUserId().value(),
                order.getStatus().name(),
                order.getItems().stream().map(this::toItemDto).toList(),
                order.getSubtotalAmount().amount(),
                order.getDiscountAmount().amount(),
                order.getShippingFee().amount(),
                order.getTotalAmount().amount(),
                order.getVoucherCode(),
                order.getRecipientName().value(),
                order.getRecipientPhone().value(),
                addr.street(), addr.ward(), addr.district(), addr.city(), addr.country(), addr.postalCode(),
                order.getNotes(),
                order.getPaymentMethod(),
                order.getPaymentId(),
                order.getReservationId() == null ? null : order.getReservationId().value(),
                order.getShipmentId(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getCompletedAt(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                payUrl, qrCodeUrl
        );
    }

    public OrderItemDTO toItemDto(OrderItem item) {
        return new OrderItemDTO(
                item.getId(),
                item.getProductId().value(),
                item.getProductName().value(),
                item.getVariantId(),
                item.getVariantSku(),
                item.getVariantImage(),
                item.getUnitPrice().amount(),
                item.getQuantity().value(),
                item.subtotal().amount()
        );
    }

    public ReturnRequestDTO toReturnDto(ReturnRequest rr) {
        return new ReturnRequestDTO(
                rr.getId() == null ? null : rr.getId().value(),
                rr.getOrderId().value(),
                rr.getUserId().value(),
                rr.getReason().value(),
                rr.getReturnType().name(),
                rr.getStatus().name(),
                rr.getRefundAmount() == null ? null : rr.getRefundAmount().amount(),
                rr.getAdminNote(),
                rr.getImages(),
                rr.getCreatedAt(),
                rr.getUpdatedAt()
        );
    }
}
