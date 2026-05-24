package com.hieu.order_service.infrastructure.rest.client;

import com.hieu.common.api.ApiResponse;
import com.hieu.order_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Internal inventory orchestration calls. These endpoints live inside the
 * service-to-service trust zone (no JWT required) — saga uses them to reserve,
 * confirm, and release stock.
 *
 * <p>Counterpart to {@link com.hieu.order_service.infrastructure.grpc.client.InventoryGrpcClient}.
 */
@FeignClient(
        name = "inventory-service",
        configuration = FeignConfig.class)
public interface InventoryClient {

    @PostMapping("/api/v1/inventory/reserve")
    ApiResponse<ReservationResult> reserveStock(@RequestBody ReserveRequest request);

    @PostMapping("/api/v1/inventory/confirm")
    ApiResponse<Void> confirmReservation(@RequestParam String orderId);

    @PostMapping("/api/v1/inventory/release")
    ApiResponse<Void> releaseReservation(@RequestParam String orderId);

    record ReserveRequest(String orderId, List<ReserveItem> items) {}
    record ReserveItem(Long productId, int quantity) {}

    record ReservationResult(boolean success, String reservationId, String errorMessage) {}
}
