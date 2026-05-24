package com.hieu.shipping_service.rest.client;

import com.hieu.shipping_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Low-level Feign binding for GHTK (Giao Hàng Tiết Kiệm) shipping API.
 *
 * <p>Resolved by URL, not Eureka (GHTK is a 3rd party). The {@code url} value
 * reads from {@code ghtk.base-url}; setting it to a missing property would let
 * Feign explode at startup — we keep a safe default ({@code https://services.ghtknet.com})
 * so dev/test environments without GHTK config still start.
 *
 * <p>GHTK quirks:
 * <ul>
 *   <li>{@code Token} header is a raw API key (no {@code Bearer} prefix).</li>
 *   <li>{@code weight} is in <b>grams</b>, integer.</li>
 *   <li>Province / district / ward names must match GHTK's directory
 *       (Vietnamese, with diacritics).</li>
 *   <li>Returns {@code success=true|false} envelope — caller maps to fallback
 *       when {@code success=false}.</li>
 * </ul>
 *
 * <p>This is a thin transport layer. Business decisions (use response vs fallback,
 * coerce {@code fee}/{@code insurance_fee}, etc.) live in the {@code GhtkClient}
 * wrapper service.
 */
@FeignClient(
        name = "ghtk",
        url = "${ghtk.base-url:https://services.ghtknet.com}",
        configuration = FeignConfig.class)
public interface GhtkFeignClient {

    /**
     * Calculate shipping fee. GHTK declares all parameters as required query
     * strings — Feign URL-encodes Vietnamese diacritics automatically (no manual
     * {@code URLEncoder} needed).
     */
    @GetMapping("/services/shipment/fee")
    Map<String, Object> calculateFee(
            @RequestHeader("Token") String token,
            @RequestParam("pick_province") String pickProvince,
            @RequestParam("pick_district") String pickDistrict,
            @RequestParam("pick_ward")     String pickWard,
            @RequestParam("pick_address")  String pickAddress,
            @RequestParam("province")      String province,
            @RequestParam("district")      String district,
            @RequestParam("ward")          String ward,
            @RequestParam("address")       String address,
            @RequestParam("weight")        int weightGrams,
            @RequestParam("value")         long value,
            @RequestParam("transport")     String transport,
            @RequestParam("deliver_option") String deliverOption
    );
}
