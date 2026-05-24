package com.hieu.cart_service.rest.client;

import com.hieu.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback used when {@link CatalogClient} fails (Eureka resolution miss, timeout,
 * 5xx, etc.). Returns an empty {@link ApiResponse} so the cart caller treats the
 * variant as "unknown" and renders with stale/placeholder data rather than 500-ing.
 *
 * <p>The fallback is wired via {@code @FeignClient(fallback = ...)} — Spring Cloud
 * LoadBalancer activates it automatically when no instance can serve the request.
 * (Activating a fallback for arbitrary 5xx responses additionally requires
 * Resilience4j circuit breaker; we keep the simpler path here.)
 */
@Slf4j
@Component
class CatalogClientFallback implements CatalogClient {

    @Override
    public ApiResponse<VariantSnapshot> getVariantBySku(String sku) {
        log.debug("catalog-service unavailable — falling back for sku={}", sku);
        return ApiResponse.error("CATALOG-DOWN", "catalog-service unavailable");
    }
}
