package com.hieu.shipping_service.service;

import com.hieu.shipping_service.config.GhtkProperties;
import com.hieu.shipping_service.dto.CalculateFeeRequest;
import com.hieu.shipping_service.dto.CalculateFeeResponse;
import com.hieu.shipping_service.rest.client.GhtkFeignClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Domain-shaped facade over {@link GhtkFeignClient}.
 *
 * <p>Falls back to a local estimator when the token is missing or the call fails,
 * so the checkout page never breaks even if GHTK is down. The fallback uses a
 * fixed per-kg rate scheme — coarse, but deterministic for dev/staging.
 *
 * <p>GHTK quirks (handled by the underlying Feign client):
 * <ul>
 *   <li>{@code Token} header is the raw API key (no {@code Bearer} prefix).</li>
 *   <li>{@code weight} is in <b>grams</b>, integer.</li>
 *   <li>Province / district names must match GHTK's directory (Vietnamese, with
 *       diacritics) — bad names return {@code success=false, message="..."} which
 *       drops into the local fallback.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GhtkClient {

    private static final long  FALLBACK_BASE_VND          = 22_000L;
    private static final long  FALLBACK_PER_EXTRA_KG_VND  = 5_000L;
    private static final long  FALLBACK_LONG_HAUL_VND     = 18_000L;
    private static final long  FALLBACK_SAME_HOURS        = 24L;
    private static final long  FALLBACK_LONG_HOURS        = 72L;
    private static final String DELIVER_OPTION_NONE       = "none";

    private final GhtkFeignClient ghtk;
    private final GhtkProperties props;

    public CalculateFeeResponse calculateFee(CalculateFeeRequest req) {
        if (props.token() == null || props.token().isBlank()) {
            log.warn("GHTK token missing — using local fallback estimator");
            return localFallback(req);
        }

        String transport = (req.transport() == null) ? props.transport() : req.transport();
        try {
            Map<String, Object> resp = ghtk.calculateFee(
                    props.token(),
                    props.pick().province(), props.pick().district(),
                    props.pick().ward(),     props.pick().address(),
                    req.province(), req.district(), req.ward(), req.address(),
                    req.weightGrams(), req.totalValue(),
                    transport, DELIVER_OPTION_NONE);

            if (resp == null || !Boolean.TRUE.equals(resp.get("success"))) {
                log.warn("GHTK fee returned unsuccess — using fallback: {}", resp);
                return localFallback(req);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> fee = (Map<String, Object>) resp.get("fee");
            if (fee == null) return localFallback(req);

            long total       = num(fee.get("fee"));
            long insurance   = num(fee.get("insurance_fee"));
            long deliveryHrs = num(fee.get("delivery"));
            return CalculateFeeResponse.ghtk(total, insurance, deliveryHrs);

        } catch (FeignException e) {
            log.warn("GHTK call failed (HTTP {}) — falling back: {}", e.status(), e.getMessage());
            return localFallback(req);
        } catch (Exception e) {
            log.warn("GHTK call threw — falling back. error={}", e.getMessage());
            return localFallback(req);
        }
    }

    /**
     * Backup estimator when GHTK is unreachable. Same shop, intra-city ≈ 22k VND
     * base + 5k/kg over 1kg. Long-haul (different province) adds a flat 18k surcharge.
     * Numbers picked to mirror real GHTK pricing within ±20% — never charge users
     * something obviously wrong even when the real API is down.
     */
    private CalculateFeeResponse localFallback(CalculateFeeRequest req) {
        int kg = Math.max(1, (int) Math.ceil(req.weightGrams() / 1000.0));
        long perKg = (kg - 1) * FALLBACK_PER_EXTRA_KG_VND;
        boolean intracity = sameProvince(req.province());
        long surcharge = intracity ? 0L : FALLBACK_LONG_HAUL_VND;
        long total = FALLBACK_BASE_VND + perKg + surcharge;
        long hours = intracity ? FALLBACK_SAME_HOURS : FALLBACK_LONG_HOURS;
        return CalculateFeeResponse.fallback(total, hours);
    }

    private boolean sameProvince(String province) {
        return province != null && normalize(province).equals(normalize(props.pick().province()));
    }

    private static String normalize(String s) {
        return (s == null) ? "" : s.trim().toLowerCase()
                .replace("tỉnh ", "")
                .replace("thành phố ", "");
    }

    private static long num(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) { /* fall through */ }
        }
        return 0L;
    }
}
