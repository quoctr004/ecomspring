package com.hieu.order_service.infrastructure.rest.client;

import com.hieu.order_service.domain.exception.ServiceUnavailableException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Domain-shaped facade over {@link PaymentClient}.
 *
 * <p>The Feign interface is intentionally HTTP-shaped (verbs, paths, headers).
 * This facade re-exposes the operations as <em>business</em> methods —
 * {@code initiate(...)}, {@code processRefundForOrder(...)} — so the saga
 * doesn't carry knowledge of payment-service's REST URL structure.
 *
 * <p>Errors from Feign already arrive as
 * {@link ServiceUnavailableException} thanks to the shared {@code ErrorDecoder};
 * we map remaining unchecked {@link FeignException}s (network IO, decode errors)
 * here as a safety net.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceClient {

    private static final String CURRENCY_VND = "VND";

    private final PaymentClient paymentClient;

    /**
     * Initiate a payment. Returns paymentId + redirect info (QR for Sepay, URL for Momo).
     *
     * @param idempotencyKey optional — payment-service de-duplicates retries by this key
     * @param authToken      ignored when called from an HTTP request (interceptor handles it).
     *                       Kept in the signature for callers that pass a system token
     *                       from non-HTTP contexts; currently unused after the migration
     *                       because saga always runs in a request thread.
     */
    public PaymentInitiated initiate(String orderId, BigDecimal amount, String method,
                                     String idempotencyKey, String authToken) {
        try {
            var request = new PaymentClient.InitiateRequest(
                    orderId, amount, CURRENCY_VND, method, idempotencyKey);
            var resp = paymentClient.initiate(request, idempotencyKey);
            var data = unwrap(resp, "initiate", orderId);
            return new PaymentInitiated(data.paymentId(), data.qrCodeUrl(), data.payUrl());
        } catch (FeignException e) {
            log.error("payment.initiate({}) failed: {}", orderId, e.getMessage());
            throw new ServiceUnavailableException("payment-service");
        }
    }

    /**
     * Process a refund for the payment bound to {@code orderId}. Two-step flow:
     * (1) resolve paymentId via /order/{id}, (2) POST process-refund.
     * No-ops (logged) if no payment exists yet (e.g. order was COD or never paid).
     */
    public void processRefundForOrder(String orderId, BigDecimal refundAmount, String adminToken) {
        try {
            var lookup = paymentClient.getPaymentByOrderId(orderId);
            var payment = lookup != null ? lookup.data() : null;
            if (payment == null || payment.id() == null) {
                log.warn("No payment found for orderId={} — skipping refund", orderId);
                return;
            }
            paymentClient.processRefund(payment.id(),
                    new PaymentClient.RefundRequest(refundAmount, "order-refund"));
            log.info("Refund processed for orderId={}, paymentId={}", orderId, payment.id());
        } catch (FeignException e) {
            log.error("payment.processRefund({}) failed: {}", orderId, e.getMessage());
            throw new ServiceUnavailableException("payment-service");
        }
    }

    /** Unwrap ApiResponse envelope; treat null/missing data as a transport failure. */
    private static <T> T unwrap(com.hieu.common.api.ApiResponse<T> resp, String op, String orderId) {
        if (resp == null || resp.data() == null) {
            log.warn("payment.{}({}) returned empty payload", op, orderId);
            throw new ServiceUnavailableException("payment-service");
        }
        return resp.data();
    }

    /** Public return shape — kept identical to the pre-migration record so callers don't change. */
    public record PaymentInitiated(Long paymentId, String qrCodeUrl, String payUrl) {}
}
