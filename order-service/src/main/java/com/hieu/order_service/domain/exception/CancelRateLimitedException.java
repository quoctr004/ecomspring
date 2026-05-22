package com.hieu.order_service.domain.exception;

/**
 * Thrown when a customer has cancelled too many orders in the recent window.
 * Prevents buy/cancel spam that would churn inventory reservations + voucher
 * usage counts. Admins are exempt — staff legitimately need to mass-cancel.
 */
public class CancelRateLimitedException extends RuntimeException {

    private final int limit;
    private final int windowHours;

    public CancelRateLimitedException(int limit, int windowHours) {
        super("Cancellation rate limit reached: max " + limit + " orders per "
                + windowHours + "h. Try again later.");
        this.limit = limit;
        this.windowHours = windowHours;
    }

    public int limit()       { return limit; }
    public int windowHours() { return windowHours; }
}
