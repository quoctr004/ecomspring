package com.hieu.order_service.infrastructure.rest.client;

import com.hieu.common.api.ApiResponse;
import com.hieu.order_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;

/**
 * Payment-service client used by {@code OrderSagaOrchestrator}.
 *
 * <p>JWT propagation is automatic via {@link FeignConfig}'s {@code
 * jwtPropagationInterceptor}. For calls originating from non-HTTP contexts
 * (Kafka consumers running a delayed refund), the caller passes an admin/system
 * token via the explicit {@code Authorization} parameter — Feign sends whichever
 * header value reaches it, with caller's explicit value winning over the
 * interceptor for the same header name.
 */
@FeignClient(
        name = "payment-service",
        configuration = FeignConfig.class)
public interface PaymentClient {

    /**
     * Initiate a payment. Returns paymentId + redirect info (QR for Sepay, URL
     * for Momo). The {@code X-Idempotency-Key} header de-duplicates retries —
     * payment-service silently returns the original response on repeat keys.
     */
    @PostMapping("/api/v1/payments")
    ApiResponse<PaymentInitiated> initiate(
            @RequestBody InitiateRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey);

    /** Lookup payment by order id (used by refund flow). */
    @GetMapping("/api/v1/payments/order/{orderId}")
    ApiResponse<Payment> getPaymentByOrderId(@PathVariable String orderId);

    /** Process a refund for an existing payment. */
    @PostMapping("/api/v1/payments/{paymentId}/process-refund")
    ApiResponse<Void> processRefund(
            @PathVariable Long paymentId,
            @RequestBody RefundRequest request);

    // ── DTOs ──

    record InitiateRequest(
            String orderId, BigDecimal amount, String currency,
            String method, String idempotencyKey) {}

    record PaymentInitiated(Long paymentId, String qrCodeUrl, String payUrl) {}

    record Payment(Long id, String orderId, BigDecimal amount, String status) {}

    record RefundRequest(BigDecimal refundAmount, String reason) {}
}
