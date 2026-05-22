package com.hieu.order_service.interfaces.rest;

import com.hieu.common.security.AuthenticatedUser;
import com.hieu.order_service.application.command.order.CancelOrderCommand;
import com.hieu.order_service.application.command.order.CreateOrderCommand;
import com.hieu.order_service.application.command.order.CreateOrderFromCartCommand;
import com.hieu.order_service.application.dto.CursorPageDTO;
import com.hieu.order_service.application.dto.OrderDTO;
import com.hieu.order_service.application.dto.PageDTO;
import com.hieu.order_service.application.handler.order.*;
import com.hieu.order_service.application.query.order.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";


    private final CreateOrderHandler createOrderHandler;
    private final CreateOrderFromCartHandler createOrderFromCartHandler;
    private final CancelOrderHandler cancelOrderHandler;
    private final com.hieu.order_service.application.handler.order.AdminOrderActionHandler adminOrderActionHandler;
    private final GetOrderByIdHandler getOrderByIdHandler;
    private final GetOrderByIdInternalHandler getOrderByIdInternalHandler;
    private final GetOrderByNumberHandler getOrderByNumberHandler;
    private final ListOrdersByUserHandler listOrdersByUserHandler;
    private final ListOrdersByStatusHandler listOrdersByStatusHandler;
    private final ListOrdersCursorHandler listOrdersCursorHandler;
    private final HasUserPurchasedProductHandler hasUserPurchasedProductHandler;
    private final com.hieu.order_service.infrastructure.persistence.jpa.repositories.OrderJpaRepository orderRepo;

    /** Shared secret for service-to-service /internal calls. Configure per environment. */
    @Value("${security.internal-token:}")
    private String internalToken;

    /**
     * Extract bearer token from either the {@code Authorization} header or the
     * {@code ACCESS_TOKEN} cookie — the gateway doesn't rewrite cookies into headers so
     * cookie-based browser clients would otherwise leave the saga without a token to
     * forward to downstream services.
     */
    /**
     * Reject overlong / non-printable idempotency keys before they reach Redis + DB.
     * 128 chars is enough for UUID-shaped keys and short business keys; anything beyond
     * is almost always abuse or a misconfigured client.
     */
    private static String validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) return null;
        if (key.length() > 128) {
            throw new IllegalArgumentException("X-Idempotency-Key exceeds 128 chars");
        }
        if (!key.matches("[A-Za-z0-9_\\-:.]+")) {
            throw new IllegalArgumentException("X-Idempotency-Key contains invalid chars");
        }
        return key;
    }

    private static String resolveToken(String authHeader, HttpServletRequest request) {
        if (authHeader != null && !authHeader.isBlank()) {
            return authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        }
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if ("ACCESS_TOKEN".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDTO createOrder(
            @Valid @RequestBody CreateOrderRequest req,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal AuthenticatedUser user) {
        idempotencyKey = validateIdempotencyKey(idempotencyKey);
        String authToken = resolveToken(authHeader, httpRequest);
        var cmd = new CreateOrderCommand(
                user.userId(), req.items().stream().map(i -> new CreateOrderCommand.ItemCmd(
                        i.productId(), i.productName(), i.variantId(), i.variantSku(),
                        i.variantImage(), i.unitPrice(), i.quantity())).toList(),
                req.recipientName(), req.recipientPhone(),
                req.street(), req.ward(), req.district(), req.city(), req.country(), req.postalCode(),
                req.paymentMethod(), req.notes(), req.voucherCode(), idempotencyKey, authToken
        );
        return createOrderHandler.handle(cmd);
    }

    @PostMapping("/from-cart")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDTO createOrderFromCart(
            @Valid @RequestBody CreateOrderFromCartRequest req,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal AuthenticatedUser user) {
        idempotencyKey = validateIdempotencyKey(idempotencyKey);
        String authToken = resolveToken(authHeader, httpRequest);
        var cmd = new CreateOrderFromCartCommand(
                user.userId(), req.recipientName(), req.recipientPhone(),
                req.street(), req.ward(), req.district(), req.city(), req.country(), req.postalCode(),
                req.paymentMethod(), req.notes(), req.voucherCode(), idempotencyKey, authToken
        );
        return createOrderFromCartHandler.handle(cmd);
    }

    @GetMapping("/{id}")
    public OrderDTO getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        var isAdmin = user.roles().contains(ROLE_ADMIN);
        return getOrderByIdHandler.handle(new GetOrderByIdQuery(id, user.userId(), isAdmin));
    }

    @GetMapping("/by-number/{orderNumber}")
    public OrderDTO getByNumber(@PathVariable String orderNumber, @AuthenticationPrincipal AuthenticatedUser user) {
        var isAdmin = user.roles().contains(ROLE_ADMIN);
        return getOrderByNumberHandler.handle(new GetOrderByNumberQuery(orderNumber, user.userId(), isAdmin));
    }

    /**
     * PUBLIC tracking endpoint — no JWT required. Phone is the verification
     * challenge: caller must supply the {@code recipientPhone} the order was
     * placed with, otherwise we return 403 (a leaked order number alone is
     * not enough to read someone's status).
     *
     * <p>Used by /track on the storefront so guest checkout and pre-shipment
     * orders can still see their state without logging in.
     */
    @GetMapping("/track/{orderNumber}")
    public OrderDTO track(@PathVariable String orderNumber, @RequestParam("phone") String phone) {
        if (phone == null || phone.isBlank()) {
            throw new AccessDeniedException("phone required");
        }
        var raw = orderRepo.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new com.hieu.order_service.domain.exception.OrderNotFoundException(orderNumber));
        if (raw.getRecipientPhone() == null || !raw.getRecipientPhone().equals(phone.trim())) {
            throw new AccessDeniedException("Phone does not match the order");
        }
        // Pass isAdmin=true to bypass ownership inside the handler — the
        // phone check above is the real authentication gate.
        return getOrderByNumberHandler.handle(
                new GetOrderByNumberQuery(orderNumber, raw.getUserId(), true));
    }

    @GetMapping("/{id}/internal")
    public OrderDTO getInternal(@PathVariable Long id,
                                @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (internalToken == null || internalToken.isBlank() || !internalToken.equals(token)) {
            throw new AccessDeniedException("Internal endpoint requires X-Internal-Token");
        }
        return getOrderByIdInternalHandler.handle(new GetOrderByIdInternalQuery(id));
    }

    @DeleteMapping("/{id}")
    public OrderDTO cancelOrder(@PathVariable Long id, @RequestBody Map<String, String> body,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        var isAdmin = user.roles().contains(ROLE_ADMIN);
        return cancelOrderHandler.handle(new CancelOrderCommand(id, body.get("reason"), user.userId(), isAdmin));
    }

    /**
     * Admin-only manual state nudges. Each goes through the aggregate's state
     * machine so illegal transitions (e.g. deliver-without-ship) get rejected
     * with 400 instead of corrupting the order.
     */
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/confirm")
    public OrderDTO adminConfirm(@PathVariable Long id) {
        return adminOrderActionHandler.apply(id,
                com.hieu.order_service.application.handler.order.AdminOrderActionHandler.Action.CONFIRM, null);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/ship")
    public OrderDTO adminShip(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long shipmentId = null;
        if (body != null && body.get("shipmentId") != null) {
            shipmentId = Long.valueOf(body.get("shipmentId").toString());
        }
        // Fallback: synthesize a placeholder shipmentId from the order id so the
        // admin can still mark SHIPPED in dev when shipping-service isn't wired.
        if (shipmentId == null) shipmentId = id * 1_000L + System.currentTimeMillis() % 1000;
        return adminOrderActionHandler.apply(id,
                com.hieu.order_service.application.handler.order.AdminOrderActionHandler.Action.SHIP, shipmentId);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/deliver")
    public OrderDTO adminDeliver(@PathVariable Long id) {
        return adminOrderActionHandler.apply(id,
                com.hieu.order_service.application.handler.order.AdminOrderActionHandler.Action.DELIVER, null);
    }

    @GetMapping("/my")
    public CursorPageDTO<OrderDTO> myOrders(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return listOrdersByUserHandler.handle(new ListOrdersByUserQuery(user.userId(), cursor, limit));
    }

    @GetMapping("/my/by-status/{status}")
    public PageDTO<OrderDTO> myOrdersByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return listOrdersByStatusHandler.handle(new ListOrdersByStatusQuery(user.userId(), status, page, size));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CursorPageDTO<OrderDTO> listAllOrders(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status) {
        return listOrdersCursorHandler.handle(new ListOrdersCursorQuery(cursor, limit, status));
    }

    /**
     * Per-user aggregates for the admin customers list. Optional `userIds`
     * filters to a known page so the admin UI can hit /api/users first and
     * then ask order-service to enrich the row stats — without scanning the
     * whole orders table when only a slice is needed.
     */
    @GetMapping("/customers/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> customerStats(
            @RequestParam(required = false) java.util.List<String> userIds) {
        var rows = orderRepo.aggregateByUser(
                userIds == null || userIds.isEmpty() ? null : userIds);
        return rows.stream().map(r -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("userId", r.getUserId());
            m.put("orderCount", r.getOrderCount());
            m.put("lifetimeValue", r.getLifetimeValue());
            m.put("lastOrderAt", r.getLastOrderAt());
            return m;
        }).toList();
    }

    @GetMapping("/user/{userId}/purchased/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Boolean hasUserPurchased(@PathVariable String userId, @PathVariable Long productId) {
        return hasUserPurchasedProductHandler.handle(new HasUserPurchasedProductQuery(userId, productId));
    }
}
