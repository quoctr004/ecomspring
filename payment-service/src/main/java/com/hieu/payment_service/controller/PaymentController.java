package com.hieu.payment_service.controller;

import com.hieu.common.api.ApiResponse;
import com.hieu.common.security.AuthenticatedUser;
import com.hieu.payment_service.dto.*;
import com.hieu.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Payment management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final com.hieu.payment_service.config.PaymentMethodsProperties methodsProps;
    private final com.hieu.payment_service.repository.PaymentMethodOverrideRepository overrideRepo;

    /**
     * Storefront catalog of enabled payment providers. Yaml ships the seed list;
     * the override table lets admin toggle / re-order at runtime. Each yaml
     * method is joined with its override (if any) and only the still-enabled
     * ones are returned, sorted by displayOrder ASC.
     */
    @GetMapping("/methods")
    @Operation(summary = "List enabled payment methods (storefront)")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> listMethods() {
        var seed = methodsProps.methods() == null
                ? java.util.List.<com.hieu.payment_service.config.PaymentMethodsProperties.Method>of()
                : methodsProps.methods();
        var overrides = overrideRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(o -> o.getCode(), o -> o));
        var merged = new java.util.ArrayList<java.util.Map<String, Object>>();
        int idx = 0;
        for (var m : seed) {
            var ov = overrides.get(m.code());
            boolean enabled = ov != null ? ov.isEnabled() : m.enabled();
            int order = ov != null ? ov.getDisplayOrder() : idx;
            if (!enabled) { idx++; continue; }
            merged.add(java.util.Map.of(
                    "code", m.code(),
                    "name", m.name(),
                    "description", m.description() == null ? "" : m.description(),
                    "icon", m.icon() == null ? "" : m.icon(),
                    "enabled", true,
                    "displayOrder", order
            ));
            idx++;
        }
        merged.sort(java.util.Comparator.comparingInt(o -> (Integer) o.get("displayOrder")));
        return ResponseEntity.ok(ApiResponse.ok(merged));
    }

    /**
     * Admin endpoint — list ALL methods including disabled, with override status,
     * so the settings page can render the on/off switches.
     */
    @GetMapping("/methods/admin")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List ALL payment methods (admin, includes disabled)")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> listMethodsAdmin() {
        var seed = methodsProps.methods() == null
                ? java.util.List.<com.hieu.payment_service.config.PaymentMethodsProperties.Method>of()
                : methodsProps.methods();
        var overrides = overrideRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(o -> o.getCode(), o -> o));
        var rows = new java.util.ArrayList<java.util.Map<String, Object>>();
        int idx = 0;
        for (var m : seed) {
            var ov = overrides.get(m.code());
            rows.add(java.util.Map.of(
                    "code", m.code(),
                    "name", m.name(),
                    "description", m.description() == null ? "" : m.description(),
                    "icon", m.icon() == null ? "" : m.icon(),
                    "enabled", ov != null ? ov.isEnabled() : m.enabled(),
                    "displayOrder", ov != null ? ov.getDisplayOrder() : idx
            ));
            idx++;
        }
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @PatchMapping("/methods/admin/{code}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Toggle enabled / re-order a payment method (admin)")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> updateMethod(
            @PathVariable String code,
            @RequestBody java.util.Map<String, Object> body) {
        var entity = overrideRepo.findById(code)
                .orElseGet(() -> {
                    var e = new com.hieu.payment_service.entity.PaymentMethodOverrideEntity();
                    e.setCode(code);
                    return e;
                });
        if (body.get("enabled") != null) entity.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.get("displayOrder") != null) {
            entity.setDisplayOrder(((Number) body.get("displayOrder")).intValue());
        }
        entity.setUpdatedAt(java.time.OffsetDateTime.now());
        overrideRepo.save(entity);
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of(
                "code", entity.getCode(),
                "enabled", entity.isEnabled(),
                "displayOrder", entity.getDisplayOrder()
        )));
    }

    @PostMapping
    @Operation(summary = "Initiate a new payment")
    public ResponseEntity<ApiResponse<PaymentDTO>> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        PaymentDTO dto = paymentService.initiatePayment(user.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto, "Payment initiated"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<ApiResponse<PaymentDTO>> getPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        boolean isAdmin = user.hasRole("ROLE_ADMIN") || user.hasRole("ADMIN");
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getPayment(id, user.userId(), isAdmin)));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payment by order ID")
    public ResponseEntity<ApiResponse<PaymentDTO>> getPaymentByOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getPaymentByOrder(orderId)));
    }

    @GetMapping("/my")
    @Operation(summary = "List my payments (paginated)")
    public ResponseEntity<ApiResponse<PageDTO<PaymentDTO>>> getMyPayments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getMyPayments(user.userId(), page, size)));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm payment (PENDING → PAID)")
    public ResponseEntity<ApiResponse<PaymentDTO>> confirmPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.confirmPayment(id, user.userId(), request.getTransactionId()), "Payment confirmed"));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel payment (PENDING/FAILED → CANCELLED)")
    public ResponseEntity<ApiResponse<PaymentDTO>> cancelPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.cancelPayment(id, user.userId()), "Payment cancelled"));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Request refund (PAID → REFUND_REQUESTED)")
    public ResponseEntity<ApiResponse<PaymentDTO>> requestRefund(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.requestRefund(id, user.userId(), request.getReason()), "Refund requested"));
    }

    @PostMapping("/{id}/process-refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Process refund (REFUND_REQUESTED → REFUNDED) — Admin only")
    public ResponseEntity<ApiResponse<PaymentDTO>> processRefund(
            @PathVariable Long id,
            @RequestBody(required = false) RefundRequest request) {
        java.math.BigDecimal amount = (request != null) ? request.getRefundAmount() : null;
        String reason = (request != null) ? request.getReason() : null;
        return ResponseEntity.ok(ApiResponse.ok(paymentService.processRefund(id, amount, reason), "Refund processed"));
    }
}
