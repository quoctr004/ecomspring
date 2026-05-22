package com.hieu.shipping_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GHTK (Giao Hang Tiet Kiem) API config. Token comes from .env (gitignored).
 * Pickup address is the shop's origin used to compute fees.
 */
@ConfigurationProperties(prefix = "ghtk")
public record GhtkProperties(
        String baseUrl,
        String token,
        String transport,
        Pick pick
) {
    public record Pick(String province, String district, String ward, String address) {}
}
