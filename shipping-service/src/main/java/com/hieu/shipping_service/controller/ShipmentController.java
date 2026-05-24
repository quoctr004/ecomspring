package com.hieu.shipping_service.controller;

import com.hieu.common.api.ApiResponse;
import com.hieu.common.security.AuthenticatedUser;
import com.hieu.shipping_service.dto.*;
import com.hieu.shipping_service.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** REST endpoints for shipment management. */
@Slf4j
@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    /** ADMIN: create shipment explicitly. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentDTO>> createShipment(
            @Valid @RequestBody CreateShipmentRequest req) {
        var dto = shipmentService.createShipment(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto, "Shipment created"));
    }

    /** Internal (no JWT required): used by payment-service after payment confirmed. */
    @PostMapping("/internal")
    public ResponseEntity<ApiResponse<ShipmentDTO>> createShipmentInternal(
            @Valid @RequestBody CreateShipmentRequest req) {
        var dto = shipmentService.createShipment(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto, "Shipment created (internal)"));
    }

    /** JWT: own shipment or admin all. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentDTO>> getShipment(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        var dto = shipmentService.getShipmentForUser(id, user.userId(), user.hasRole("ROLE_ADMIN"));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /** JWT: own or admin. */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<ShipmentDTO>> getByOrder(
            @PathVariable String orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        var dto = shipmentService.getShipmentByOrderForUser(orderId, user.userId(), user.hasRole("ROLE_ADMIN"));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /** PUBLIC: tracking info only. */
    @GetMapping("/tracking/{trackingNumber}")
    public ResponseEntity<ApiResponse<TrackingInfoDTO>> getTracking(
            @PathVariable String trackingNumber) {
        return ResponseEntity.ok(ApiResponse.ok(shipmentService.getTrackingInfo(trackingNumber)));
    }

    /** JWT: current user's shipments. */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ShipmentDTO>>> getMyShipments(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(shipmentService.getMyShipments(user.userId())));
    }

    /** ADMIN: update status with state machine validation. */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentDTO>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest req) {
        var dto = shipmentService.updateStatus(id, req.status(), req.notes());
        return ResponseEntity.ok(ApiResponse.ok(dto, "Status updated"));
    }

    /** ADMIN: assign carrier + tracking number. */
    @PutMapping("/{id}/tracking")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentDTO>> assignTracking(
            @PathVariable Long id,
            @Valid @RequestBody AssignTrackingRequest req) {
        var dto = shipmentService.assignTracking(id, req.carrier(), req.trackingNumber());
        return ResponseEntity.ok(ApiResponse.ok(dto, "Tracking assigned"));
    }

    /** ADMIN: set estimated delivery date. */
    @PutMapping("/{id}/estimated-delivery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentDTO>> setEstimatedDelivery(
            @PathVariable Long id,
            @Valid @RequestBody SetEstimatedDeliveryRequest req) {
        var dto = shipmentService.setEstimatedDelivery(id, req.estimatedDeliveryDate());
        return ResponseEntity.ok(ApiResponse.ok(dto, "Estimated delivery date set"));
    }

    /** ADMIN: OUT_FOR_DELIVERY → DELIVERED. */
    @PostMapping("/{id}/delivered")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentDTO>> markDelivered(@PathVariable Long id) {
        var dto = shipmentService.markDelivered(id);
        return ResponseEntity.ok(ApiResponse.ok(dto, "Shipment marked as delivered"));
    }

    /** ADMIN: paginated list by status. */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ShipmentDTO>>> listByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var result = shipmentService.listByStatus(status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
