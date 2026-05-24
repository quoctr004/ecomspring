package com.hieu.shipping_service.controller;

import com.hieu.common.api.ApiResponse;
import com.hieu.shipping_service.dto.CalculateFeeRequest;
import com.hieu.shipping_service.dto.CalculateFeeResponse;
import com.hieu.shipping_service.entity.CarrierConfigEntity;
import com.hieu.shipping_service.repository.CarrierConfigRepository;
import com.hieu.shipping_service.service.GhtkClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Shipping fee quote + carrier catalog (DB-backed).
 *
 * <p>Storefront pulls {@link #carriers()} which returns the enabled list
 * sorted by displayOrder; admin uses {@link #adminListCarriers()} +
 * {@link #updateCarrier} to manage the table without redeploying.
 */
@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShippingFeeController {

    private final GhtkClient ghtkClient;
    private final CarrierConfigRepository carrierRepo;

    /** GHTK live fee — falls back to local estimator when token missing or API down. */
    @PostMapping("/calculate-fee")
    public ResponseEntity<ApiResponse<CalculateFeeResponse>> calculateFee(
            @Valid @RequestBody CalculateFeeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(ghtkClient.calculateFee(req)));
    }

    /** Storefront — only enabled carriers, sorted by displayOrder. */
    @GetMapping("/carriers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> carriers() {
        var rows = carrierRepo.findAll().stream()
                .filter(CarrierConfigEntity::isEnabled)
                .sorted(Comparator.comparingInt(CarrierConfigEntity::getDisplayOrder))
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    /** Admin — every carrier including disabled. */
    @GetMapping("/carriers/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> adminListCarriers() {
        var rows = carrierRepo.findAll().stream()
                .sorted(Comparator.comparingInt(CarrierConfigEntity::getDisplayOrder))
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @PatchMapping("/carriers/admin/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCarrier(
            @PathVariable String code,
            @RequestBody Map<String, Object> body) {
        var entity = carrierRepo.findById(code)
                .orElseGet(() -> {
                    var e = new CarrierConfigEntity();
                    e.setCode(code);
                    e.setName(code);
                    return e;
                });
        if (body.get("name") != null)         entity.setName(body.get("name").toString());
        if (body.get("enabled") != null)      entity.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.get("supportsCod") != null)  entity.setSupportsCod(Boolean.TRUE.equals(body.get("supportsCod")));
        if (body.get("etaHours") != null)     entity.setEtaHours(((Number) body.get("etaHours")).intValue());
        if (body.get("displayOrder") != null) entity.setDisplayOrder(((Number) body.get("displayOrder")).intValue());
        entity.setUpdatedAt(OffsetDateTime.now());
        return ResponseEntity.ok(ApiResponse.ok(toMap(carrierRepo.save(entity))));
    }

    private Map<String, Object> toMap(CarrierConfigEntity c) {
        return Map.of(
                "code", c.getCode(),
                "name", c.getName(),
                "enabled", c.isEnabled(),
                "supportsCod", c.isSupportsCod(),
                "etaHours", c.getEtaHours(),
                "displayOrder", c.getDisplayOrder()
        );
    }
}
