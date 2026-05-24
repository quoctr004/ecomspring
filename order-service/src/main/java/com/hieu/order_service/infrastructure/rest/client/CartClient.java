package com.hieu.order_service.infrastructure.rest.client;

import com.hieu.common.api.ApiResponse;
import com.hieu.order_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.List;

/**
 * JWT-protected cart-service endpoints. The end-user's bearer token is forwarded
 * automatically by {@link FeignConfig}'s {@code jwtPropagationInterceptor} —
 * callers do not pass tokens by hand.
 *
 * <p>Counterpart to {@link com.hieu.order_service.infrastructure.grpc.client.CartGrpcClient};
 * use this when REST tracing is easier (eg. webhooks, debugging).
 */
@FeignClient(
        name = "cart-service",
        configuration = FeignConfig.class)
public interface CartClient {

    @GetMapping("/api/v1/cart")
    ApiResponse<CartResponse> getCart();

    @DeleteMapping("/api/v1/cart")
    void clearCart();

    record CartResponse(List<CartItem> items) {}

    record CartItem(
            Long productId, String productName,
            Long variantId, String variantSku, String variantImage,
            BigDecimal unitPrice, int quantity) {}
}
