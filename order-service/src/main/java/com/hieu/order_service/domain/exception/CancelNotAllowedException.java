package com.hieu.order_service.domain.exception;

/**
 * Customer attempted to cancel an order that is already past the "customer
 * can self-service" window (e.g. shipped, delivered, or admin-confirmed in a
 * stricter tenant policy). Admins bypass this — they can still cancel via
 * the admin endpoints.
 */
public class CancelNotAllowedException extends RuntimeException {

    private final String status;

    public CancelNotAllowedException(String status) {
        super("Order in status " + status + " can no longer be cancelled by the customer.");
        this.status = status;
    }

    public String status() { return status; }
}
