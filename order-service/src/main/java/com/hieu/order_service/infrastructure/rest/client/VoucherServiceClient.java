package com.hieu.order_service.infrastructure.rest.client;

import com.hieu.order_service.domain.exception.ServiceUnavailableException;
import com.hieu.order_service.infrastructure.rest.client.exception.VoucherInvalidException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Domain-shaped facade over {@link VoucherClient}.
 *
 * <p>Translates the HTTP-shaped Feign contract into the business semantics
 * expected by the saga: {@code validateAndApply(...)} either returns a discount
 * amount or throws {@link VoucherInvalidException} (rejected) /
 * {@link ServiceUnavailableException} (transport failure). The error-decoder in
 * {@code FeignConfig} already does this mapping; the catch-all here covers any
 * residual {@link FeignException} (e.g. decode errors).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VoucherServiceClient {

    private static final String SERVICE_NAME = "voucher-service";

    private final VoucherClient voucherClient;

    /**
     * Validate + atomically reserve a voucher slot.
     *
     * @param code        voucher code from order request
     * @param orderAmount subtotal BEFORE discount (so voucher-service can check minOrder)
     * @param userId      order owner — for per-user usage limits
     * @param orderId     order id (numeric) — used as idempotency key for release later
     * @param productIds  variant/product ids in the cart — for product-restricted vouchers
     * @param authToken   ignored when called from an HTTP request (interceptor forwards it).
     *                    Kept in the signature to preserve the existing saga API.
     * @return the discount amount to subtract from the subtotal
     * @throws VoucherInvalidException     when voucher-service rejects (4xx)
     * @throws ServiceUnavailableException on transport failures
     */
    public BigDecimal validateAndApply(String code, BigDecimal orderAmount, String userId,
                                       Long orderId, List<Long> productIds, String authToken) {
        try {
            // Server-side DTO uses String types for orderId + productIds — encode here so
            // we don't depend on the server's Jackson coercion config.
            List<String> productIdStrings = (productIds == null || productIds.isEmpty())
                    ? List.of()
                    : productIds.stream().map(String::valueOf).toList();

            var req = new VoucherClient.ValidateRequest(
                    code, orderAmount, userId, String.valueOf(orderId), productIdStrings);

            var resp = voucherClient.validate(req);
            if (resp == null || resp.data() == null || resp.data().discountAmount() == null) {
                log.warn("voucher.validate({}) returned empty discountAmount", code);
                throw new ServiceUnavailableException(SERVICE_NAME);
            }
            return resp.data().discountAmount();

        } catch (VoucherInvalidException | ServiceUnavailableException e) {
            throw e;
        } catch (FeignException e) {
            log.error("voucher.validate({}) failed: {}", code, e.getMessage());
            throw new ServiceUnavailableException(SERVICE_NAME);
        }
    }

    /**
     * Idempotent release. Compensation step in the saga — voucher-service
     * accepts repeated releases silently. Never throws: a transient failure here
     * is recoverable because voucher-service also consumes {@code order.cancelled}
     * from Kafka for eventual cleanup.
     */
    public void release(String code, Long orderId) {
        try {
            voucherClient.release(new VoucherClient.ReleaseRequest(code, String.valueOf(orderId)));
            log.info("Released voucher {} for order {}", code, orderId);
        } catch (ServiceUnavailableException | FeignException e) {
            // Compensation must NOT throw — both the ErrorDecoder's wrapped exception
            // and any raw Feign IO error are swallowed. order.cancelled on Kafka will
            // trigger voucher-service's own cleanup as a backstop.
            log.warn("voucher.release({}, {}) failed (will rely on Kafka): {}",
                    code, orderId, e.getMessage());
        }
    }
}
