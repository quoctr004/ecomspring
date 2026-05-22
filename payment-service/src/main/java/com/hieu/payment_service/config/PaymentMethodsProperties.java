package com.hieu.payment_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Storefront payment-method catalog. Driven by config so adding a provider
 * (e.g. VNPay) is a yaml edit instead of an FE deploy. The storefront pulls
 * this list via {@code GET /api/payments/methods}; admin UI later wires the
 * same property to a CRUD-backed table when we need runtime toggles.
 *
 * <p>Keys mirror the internal enum after normalization: SEPAY/MOMO/COD.
 */
@ConfigurationProperties(prefix = "payment")
public record PaymentMethodsProperties(List<Method> methods) {
    public record Method(
            String code,
            String name,
            String description,
            String icon,
            boolean enabled
    ) {}
}
