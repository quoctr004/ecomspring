package com.hieu.shipping_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Shipping fee quote request from checkout page.
 * weightGrams comes from cart aggregation (product weight × qty) — FE may pass a
 * conservative default (e.g. 500g/item) when product weight is missing.
 */
public record CalculateFeeRequest(
        @NotBlank String province,
        @NotBlank String district,
        @NotBlank String ward,
        @NotBlank String address,
        @Min(1) int weightGrams,
        @Min(0) long totalValue,
        String transport
) {}
