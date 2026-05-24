package com.hieu.order_service.infrastructure.rest.client.exception;

/**
 * Thrown when voucher-service explicitly rejects a validate/apply call (4xx response).
 *
 * <p>Distinct from {@link com.hieu.order_service.domain.exception.ServiceUnavailableException}
 * — this is a <em>business</em> rejection (expired voucher, min-order not met, usage
 * limit reached), not a transport failure. The saga maps it to order FAILED with a
 * user-friendly reason rather than triggering retry/compensation paths.
 */
public class VoucherInvalidException extends RuntimeException {

    public VoucherInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
