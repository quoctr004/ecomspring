package com.hieu.catalog_service.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Lightweight projection for list endpoints. Carries every variant image URL so
 * the storefront product card can hover-cycle through real variant photos
 * instead of duplicating the thumbnail (which it did before the BE shipped
 * variantImages). Full variant attributes still load from the detail endpoint.
 */
public record ProductSummaryDTO(
        Long id,
        String name,
        String slug,
        Long categoryId,
        String brand,
        String thumbnail,
        String status,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int totalStock,
        boolean available,
        List<String> variantImages,
        Instant createdAt
) {}
