package com.hieu.cart_service.rest.client;

import com.hieu.cart_service.config.CacheConfig;
import com.hieu.common.api.ApiResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

/**
 * Feign client for catalog-service. Resolved via Eureka — no hard-coded URL.
 *
 * <p>The cart caller treats every transport failure as a soft miss
 * (see {@link CatalogClientFallback}), so the cart still renders with stale prices
 * + a warning when catalog is unavailable. That's intentional — failing the whole
 * cart page because catalog is briefly down is worse UX than showing slightly
 * stale data.
 */
@FeignClient(
        name = "catalog-service",
        fallback = CatalogClientFallback.class,
        configuration = com.hieu.cart_service.config.FeignConfig.class)
public interface CatalogClient {

    /**
     * Look up a variant by SKU. Returns the full {@link ApiResponse} envelope so
     * callers can read {@code success} / {@code message} for richer error UX.
     *
     * <p><b>Cached (Redis, 5 min TTL)</b> — variant prices and stock change rarely
     * relative to how often the cart-render path calls this lookup. Cache key is
     * the SKU. Null returns (fallback path) are NOT cached (see {@link CacheConfig}),
     * so a brief catalog outage doesn't pin "missing" entries.
     */
    @Cacheable(value = CacheConfig.CACHE_CATALOG_VARIANT, key = "#sku", unless = "#result == null || !#result.success()")
    @GetMapping("/api/v1/variants/by-sku/{sku}")
    ApiResponse<VariantSnapshot> getVariantBySku(@PathVariable("sku") String sku);

    /**
     * Snapshot DTO mirroring the catalog response. Keeping it inside the client
     * package keeps the contract co-located with the binding.
     */
    record VariantSnapshot(
            Long id,
            Long productId,
            String sku,
            BigDecimal price,
            String image,
            int quantity,
            String status,
            boolean available
    ) {}
}
