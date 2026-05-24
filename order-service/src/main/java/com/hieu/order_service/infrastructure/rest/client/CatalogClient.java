package com.hieu.order_service.infrastructure.rest.client;

import com.hieu.common.api.ApiResponse;
import com.hieu.order_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

/**
 * Read-only catalog lookups. Public endpoints — no JWT required.
 * Counterpart to {@link com.hieu.order_service.infrastructure.grpc.client.CatalogGrpcClient}
 * for callers that prefer REST (debugging, fallback when gRPC unavailable).
 */
@FeignClient(
        name = "catalog-service",
        configuration = FeignConfig.class)
public interface CatalogClient {

    @GetMapping("/api/v1/variants/by-sku/{sku}")
    ApiResponse<VariantResponse> getVariantBySku(@PathVariable String sku);

    record VariantResponse(
            Long id, Long productId, String sku,
            BigDecimal price, int quantity, String status) {}
}
