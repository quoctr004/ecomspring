package com.hieu.shipping_service.dto;

/**
 * Shipping fee quote returned to checkout page.
 * {@code carrier} is the human-readable provider name; {@code source} reports whether
 * the price came from GHTK live API or the local fallback estimator.
 */
public record CalculateFeeResponse(
        String carrier,
        long fee,
        long insuranceFee,
        long deliveryTimeHours,
        String source
) {
    public static CalculateFeeResponse ghtk(long fee, long insurance, long hours) {
        return new CalculateFeeResponse("GHTK", fee, insurance, hours, "GHTK_LIVE");
    }
    public static CalculateFeeResponse fallback(long fee, long hours) {
        return new CalculateFeeResponse("GHTK", fee, 0L, hours, "LOCAL_FALLBACK");
    }
}
