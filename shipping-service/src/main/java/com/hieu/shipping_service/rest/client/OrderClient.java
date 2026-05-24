package com.hieu.shipping_service.rest.client;

import com.hieu.common.api.ApiResponse;
import com.hieu.shipping_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Feign binding for order-service, resolved via Eureka.
 *
 * <p>Used by the Kafka {@code payment.completed} consumer to fetch the order's
 * shipping address before creating the shipment record. Runs on a Kafka thread —
 * no servlet request context — so no JWT propagation is attempted; the endpoint
 * must be reachable from the service-to-service trust zone.
 */
@FeignClient(
        name = "order-service",
        configuration = FeignConfig.class)
public interface OrderClient {

    /**
     * Returns the order details. Loose typing ({@code Map}) matches the existing
     * consumer that reads fields by name — keeps the migration risk minimal.
     * Tighten to a record DTO once the consumer stabilises.
     */
    @GetMapping("/api/v1/orders/{orderId}")
    ApiResponse<Map<String, Object>> getOrder(@PathVariable("orderId") String orderId);
}
