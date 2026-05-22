package com.hieu.shipping_service.service;

import com.hieu.shipping_service.config.GhtkProperties;
import com.hieu.shipping_service.dto.CalculateFeeRequest;
import com.hieu.shipping_service.dto.CalculateFeeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Thin wrapper over GHTK's {@code /services/shipment/fee} endpoint.
 *
 * <p>Falls back to a local estimator when the token is missing or the call fails,
 * so the checkout page never breaks even if GHTK is down. The fallback uses a
 * fixed per-kg rate scheme — coarse, but deterministic for dev/staging.
 *
 * <p>GHTK quirks:
 * <ul>
 *   <li>Authorization header is just the raw token (no "Bearer" prefix).</li>
 *   <li>weight is in <b>grams</b> as integer.</li>
 *   <li>Province / district names must match GHTK's directory (Vietnamese, with
 *       diacritics) — bad names return {@code success=false, message="..."}.</li>
 * </ul>
 */
@Slf4j
@Component
public class GhtkClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final WebClient webClient;
    private final GhtkProperties props;

    public GhtkClient(GhtkProperties props) {
        this.props = props;
        // URI_COMPONENT: percent-encode every component (path/query value) so
        // Vietnamese diacritics + spaces survive without manual URLEncoder calls.
        var factory = new DefaultUriBuilderFactory(props.baseUrl());
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);
        this.webClient = WebClient.builder()
                .uriBuilderFactory(factory)
                .build();
    }

    public CalculateFeeResponse calculateFee(CalculateFeeRequest req) {
        if (props.token() == null || props.token().isBlank()) {
            log.warn("GHTK token missing — using local fallback estimator");
            return localFallback(req);
        }
        try {
            String transport = req.transport() == null ? props.transport() : req.transport();
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = webClient.get()
                    .uri(b -> b.path("/services/shipment/fee")
                            .queryParam("pick_province", props.pick().province())
                            .queryParam("pick_district", props.pick().district())
                            .queryParam("pick_ward",     props.pick().ward())
                            .queryParam("pick_address",  props.pick().address())
                            .queryParam("province",      req.province())
                            .queryParam("district",      req.district())
                            .queryParam("ward",          req.ward())
                            .queryParam("address",       req.address())
                            .queryParam("weight",        req.weightGrams())
                            .queryParam("value",         req.totalValue())
                            .queryParam("transport",     transport)
                            .queryParam("deliver_option","none")
                            .build())
                    .header("Token", props.token())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> {
                        log.warn("GHTK fee call failed: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (resp == null || !Boolean.TRUE.equals(resp.get("success"))) {
                log.warn("GHTK fee returned unsuccess: {}", resp);
                return localFallback(req);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> fee = (Map<String, Object>) resp.get("fee");
            if (fee == null) return localFallback(req);

            long total       = num(fee.get("fee"));
            long insurance   = num(fee.get("insurance_fee"));
            long deliveryHrs = num(fee.get("delivery"));
            return CalculateFeeResponse.ghtk(total, insurance, deliveryHrs);

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
        long base = 22_000;
        int kg = Math.max(1, (int) Math.ceil(req.weightGrams() / 1000.0));
        long perKg = (kg - 1) * 5_000L;
        long surcharge = sameProvince(req.province()) ? 0L : 18_000L;
        long total = base + perKg + surcharge;
        long hours = sameProvince(req.province()) ? 24 : 72;
        return CalculateFeeResponse.fallback(total, hours);
    }

    private boolean sameProvince(String province) {
        return province != null && normalize(province).equals(normalize(props.pick().province()));
    }
    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replace("tỉnh ", "").replace("thành phố ", "");
    }
    private static long num(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        return 0L;
    }
}
