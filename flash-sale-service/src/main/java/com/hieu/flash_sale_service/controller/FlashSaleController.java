package com.hieu.flash_sale_service.controller;

import com.hieu.common.api.ApiResponse;
import com.hieu.common.security.AuthenticatedUser;
import com.hieu.flash_sale_service.dto.*;
import com.hieu.flash_sale_service.service.FlashSaleApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** REST controller for flash sale operations. */
@Slf4j
@RestController
@RequestMapping("/api/v1/flash-sales")
@RequiredArgsConstructor
@Tag(name = "Flash Sales", description = "Flash sale management and participation")
public class FlashSaleController {

    private final FlashSaleApplicationService service;

    // ---- ADMIN ----

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new flash sale (ADMIN)")
    public ResponseEntity<ApiResponse<FlashSaleDTO>> createSale(
            @Valid @RequestBody CreateFlashSaleRequest request) {
        var dto = service.createSale(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto, "Flash sale created"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all flash sales with pagination (ADMIN)")
    public ResponseEntity<ApiResponse<PageDTO<FlashSaleDTO>>> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.listAllSales(page, size)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate a scheduled flash sale (ADMIN)")
    public ResponseEntity<ApiResponse<FlashSaleDTO>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.activateSale(id), "Flash sale activated"));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "End an active flash sale (ADMIN)")
    public ResponseEntity<ApiResponse<FlashSaleDTO>> end(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.endSale(id), "Flash sale ended"));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cancel a flash sale (ADMIN)")
    public ResponseEntity<ApiResponse<FlashSaleDTO>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.cancelSale(id), "Flash sale cancelled"));
    }

    // ---- PUBLIC ----

    @GetMapping("/active")
    @Operation(summary = "List currently active flash sales (PUBLIC)")
    public ResponseEntity<ApiResponse<PageDTO<FlashSaleDTO>>> listActive(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.listActiveSales(page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a flash sale by id (PUBLIC)")
    public ResponseEntity<ApiResponse<FlashSaleDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getSale(id)));
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Check slot availability for a flash sale (PUBLIC)")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> availability(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.checkAvailability(id)));
    }

    // ---- JWT ----

    @PostMapping("/{id}/participate")
    @Operation(summary = "Participate in a flash sale (JWT)")
    public ResponseEntity<ApiResponse<ParticipateResponse>> participate(
            @PathVariable Long id,
            @Valid @RequestBody ParticipateRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        var response = service.participate(id, currentUser.userId(), request.quantity());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
