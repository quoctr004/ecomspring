package com.hieu.order_service.infrastructure.rest.client;

import com.hieu.common.api.ApiResponse;
import com.hieu.order_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;

/**
 * Voucher-service client. Validation rejections (4xx) are promoted to
 * {@link com.hieu.order_service.infrastructure.rest.client.exception.VoucherInvalidException}
 * by {@link FeignConfig}'s {@code OrderFeignErrorDecoder} — saga can branch on it
 * vs {@link com.hieu.order_service.domain.exception.ServiceUnavailableException}.
 *
 * <p>Reject reasons covered: 404 not-found, 422 min-order/expired, 409 limit-reached.
 */
@FeignClient(
        name = "voucher-service",
        configuration = FeignConfig.class)
public interface VoucherClient {

    /**
     * Validate + atomically reserve a voucher slot. Returns discount amount
     * (subtotal − discount = final amount).
     */
    @PostMapping("/api/v1/vouchers/validate")
    ApiResponse<ValidateResponse> validate(@RequestBody ValidateRequest request);

    /**
     * Idempotent release of a previously-reserved voucher slot. Voucher-service
     * handles double-release silently. Compensation step in the saga — must not
     * throw on transport errors (caller logs + relies on Kafka eventual cleanup).
     */
    @PostMapping("/api/v1/vouchers/release")
    ApiResponse<Void> release(@RequestBody ReleaseRequest request);

    // ── DTOs ──
    // Server side declares orderId:String + productIds:List<String> — encode here
    // so server-side Jackson coercion config doesn't matter.

    record ValidateRequest(
            String code, BigDecimal orderAmount, String userId,
            String orderId, List<String> productIds) {}

    record ValidateResponse(BigDecimal discountAmount, String voucherType) {}

    record ReleaseRequest(String code, String orderId) {}
}
