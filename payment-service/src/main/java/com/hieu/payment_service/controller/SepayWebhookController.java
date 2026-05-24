package com.hieu.payment_service.controller;

import com.hieu.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Public webhook endpoint for Sepay payment notifications. No JWT, but every request
 * is authenticated by a shared {@code Apikey} header; signature verification is NOT
 * optional — missing / empty config fails startup so prod can never accept unsigned
 * callbacks (the old fail-open default auto-confirmed any POST).
 */
@RestController
@RequestMapping("/api/v1/payments/sepay")
@Tag(name = "Sepay Webhook", description = "Public Sepay payment webhook")
public class SepayWebhookController {

    private static final String FIELD_STATUS = "status";


    private static final Logger log = LoggerFactory.getLogger(SepayWebhookController.class);
    /** Order number pattern — Sepay puts it in `description`. Keep strict to prevent
     *  arbitrary string passing downstream. */
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("^ORD-\\d{8}-\\d{6}$");

    private final PaymentService paymentService;
    private final String expectedApiKey;
    private final boolean allowInsecure;

    public SepayWebhookController(PaymentService paymentService,
                                   @Value("${sepay.webhook.api-key:}") String expectedApiKey,
                                   @Value("${sepay.webhook.allow-insecure:false}") boolean allowInsecure) {
        this.paymentService = paymentService;
        this.expectedApiKey = expectedApiKey == null ? "" : expectedApiKey.trim();
        this.allowInsecure = allowInsecure;
        if (this.expectedApiKey.isBlank() && !allowInsecure) {
            throw new IllegalStateException(
                "sepay.webhook.api-key must be set (or sepay.webhook.allow-insecure=true for local dev)");
        }
        if (allowInsecure) {
            log.warn("Sepay webhook running in INSECURE mode — Apikey verification DISABLED");
        }
    }

    @PostMapping("/webhook")
    @Operation(summary = "Sepay payment webhook (public — verifies Apikey header)")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload) {

        if (!allowInsecure) {
            String provided = authHeader != null && authHeader.startsWith("Apikey ")
                    ? authHeader.substring("Apikey ".length()).trim()
                    : "";
            // Constant-time compare — String.equals short-circuits on the first differing
            // byte, leaking the matching prefix length through response timing.
            byte[] expected = expectedApiKey.getBytes(StandardCharsets.UTF_8);
            byte[] actual = provided.getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, actual)) {
                log.warn("Sepay webhook rejected — invalid Apikey");
                return ResponseEntity.status(401).body(Map.of(FIELD_STATUS, "unauthorized"));
            }
        }

        // C3: Log only non-PII fields — full payload may contain account/amount details.
        log.debug("Sepay webhook received: id={} description={}", payload.get("id"), payload.get("description"));

        Object description = payload.get("description");
        Object transferId = payload.get("id");
        if (description == null) {
            log.warn("Sepay webhook payload missing 'description' field");
            return ResponseEntity.ok(Map.of(FIELD_STATUS, "skipped", "reason", "missing description"));
        }

        String orderId = description.toString().trim();
        if (!ORDER_ID_PATTERN.matcher(orderId).matches()) {
            // Don't propagate arbitrary strings — protects downstream lookups from
            // injection via malformed webhook payloads.
            log.warn("Sepay webhook orderId does not match expected format: {}", orderId);
            return ResponseEntity.ok(Map.of(FIELD_STATUS, "skipped", "reason", "invalid orderId format"));
        }

        String transactionId = transferId != null
                ? transferId.toString()
                : "SEPAY-" + System.currentTimeMillis();
        try {
            paymentService.autoConfirmByOrderId(orderId, transactionId);
        } catch (Exception e) {
            log.warn("Sepay webhook auto-confirm failed for orderId={}: {}", orderId, e.getMessage());
        }
        return ResponseEntity.ok(Map.of(FIELD_STATUS, "received"));
    }
}
