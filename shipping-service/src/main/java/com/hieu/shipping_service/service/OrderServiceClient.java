package com.hieu.shipping_service.service;

import com.hieu.common.api.ApiResponse;
import com.hieu.shipping_service.rest.client.OrderClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Domain-shaped facade over {@link OrderClient}.
 *
 * <p>Used by the Kafka {@code payment.completed} consumer to fetch the shipping
 * address. Failures degrade to {@link Optional#empty()} so the consumer can
 * decide whether to retry the Kafka message or skip the order — taking the
 * whole consumer thread down because order-service blipped would be worse than
 * losing one shipment-record creation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderServiceClient {

    private final OrderClient orderClient;

    /**
     * Fetches the order response map. Returns empty if order-service is
     * unreachable, the response is empty, or the orderId doesn't exist.
     */
    public Optional<Map<String, Object>> fetchOrder(String orderId) {
        try {
            ApiResponse<Map<String, Object>> resp = orderClient.getOrder(orderId);
            if (resp == null || resp.data() == null) {
                log.debug("order-service returned empty payload for orderId={}", orderId);
                return Optional.empty();
            }
            return Optional.of(resp.data());
        } catch (FeignException e) {
            log.warn("order-service call failed for orderId={} (HTTP {}): {}",
                    orderId, e.status(), e.getMessage());
            return Optional.empty();
        }
    }
}
