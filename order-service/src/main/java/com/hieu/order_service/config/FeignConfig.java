package com.hieu.order_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.Response;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import com.hieu.order_service.domain.exception.ServiceUnavailableException;
import com.hieu.order_service.infrastructure.rest.client.exception.VoucherInvalidException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * Shared Feign configuration — beans that Spring Cloud OpenFeign cannot declare
 * via properties: retry policy, JWT propagation interceptor, and the custom
 * {@link ErrorDecoder} that promotes domain-specific 4xx to typed exceptions.
 *
 * <p>Timeouts and log level live in {@code application.yaml} under
 * {@code spring.cloud.openfeign.client.config.*} so ops can tune them without
 * rebuilding the image.
 *
 * <h2>Why each bean exists</h2>
 * <ul>
 *   <li><b>{@link Retryer#NEVER_RETRY}</b> — saga steps are NOT all idempotent;
 *       silent retry on POST {@code /reserve} could double-debit stock. Components
 *       that need retry opt in via {@code @Retryable} with explicit backoff.</li>
 *   <li><b>JWT propagation</b> — payment/voucher endpoints are JWT-protected;
 *       the interceptor lifts the bearer from the current servlet request and
 *       re-attaches it on outbound Feign calls. No-op outside an HTTP request
 *       (Kafka consumers, scheduled jobs) — downstream returns 401, which is
 *       the correct signal.</li>
 *   <li><b>{@link OrderFeignErrorDecoder}</b> — voucher {@code /validate} 4xx
 *       becomes {@link VoucherInvalidException}; everything else (5xx, IO, other
 *       4xx) becomes {@link ServiceUnavailableException} so saga picks the right
 *       compensation path.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class FeignConfig {

    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }

    /** Cookie name carrying the access token when callers use cookie-based auth. */
    private static final String ACCESS_TOKEN_COOKIE = "ACCESS_TOKEN";
    private static final String BEARER_PREFIX       = "Bearer ";

    /**
     * Forward the caller's identity to downstream Feign calls.
     *
     * <p>Two sources are checked in order:
     * <ol>
     *   <li>{@code Authorization: Bearer ...} header — API clients (mobile, CLI).</li>
     *   <li>{@code ACCESS_TOKEN} cookie — browser clients (the gateway does not
     *       rewrite the cookie into an Authorization header for downstream services).</li>
     * </ol>
     *
     * <p>If both are absent (Kafka consumer, scheduled job), the interceptor is
     * a no-op — downstream services that require auth will respond with 401.
     */
    @Bean
    public RequestInterceptor jwtPropagationInterceptor() {
        return template -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return;

            HttpServletRequest req = attrs.getRequest();

            String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && !auth.isBlank()) {
                template.header(HttpHeaders.AUTHORIZATION, auth);
                return;
            }

            Cookie[] cookies = req.getCookies();
            if (cookies == null) return;
            for (Cookie c : cookies) {
                if (ACCESS_TOKEN_COOKIE.equals(c.getName())
                        && c.getValue() != null && !c.getValue().isBlank()) {
                    template.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + c.getValue());
                    return;
                }
            }
        };
    }

    @Bean
    public ErrorDecoder feignErrorDecoder(ObjectMapper objectMapper) {
        return new OrderFeignErrorDecoder(objectMapper);
    }

    /**
     * Maps downstream errors to domain exceptions so saga code can branch cleanly:
     * <ul>
     *   <li>voucher-service 4xx → {@link VoucherInvalidException}
     *       (saga marks order FAILED with the reason)</li>
     *   <li>Any other 4xx/5xx → {@link ServiceUnavailableException}
     *       (saga triggers compensation: release stock, refund, etc.)</li>
     * </ul>
     *
     * <p>Avoid logging the response body — it may contain PII (orderId, userId,
     * amount, voucher code).
     */
    static class OrderFeignErrorDecoder implements ErrorDecoder {

        private final ObjectMapper objectMapper;

        OrderFeignErrorDecoder(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public Exception decode(String methodKey, Response response) {
            int status = response.status();
            String message = extractMessage(response);

            // voucher-service rejections on /validate are business-level (expired, min-order,
            // limit-reached) — bubble up so saga maps to FAILED with a clean reason.
            // Other VoucherClient methods (release, etc.) fall through to the transport-style
            // mapping below so callers can decide to swallow + rely on Kafka eventual cleanup.
            if (methodKey.startsWith("VoucherClient#validate") && status >= 400 && status < 500) {
                log.warn("voucher-service rejected validate ({}): {}", status, methodKey);
                return new VoucherInvalidException(message != null ? message : "voucher rejected", null);
            }

            // Anything else (4xx, 5xx, IO) is treated as a transport-style failure
            String serviceName = extractServiceName(methodKey);
            log.warn("Feign call {} failed with HTTP {}", methodKey, status);
            return new ServiceUnavailableException(serviceName);
        }

        /**
         * Best-effort extraction of {@code message} from the standard ApiResponse envelope.
         * Returns {@code null} when the body is unreadable or missing — caller picks a
         * fallback message.
         */
        private String extractMessage(Response response) {
            if (response.body() == null) return null;
            try (var is = response.body().asInputStream()) {
                byte[] bytes = is.readAllBytes();
                if (bytes.length == 0) return null;
                var node = objectMapper.readTree(bytes);
                String msg = node.path("message").asText(null);
                return (msg == null || msg.isBlank()) ? null : msg;
            } catch (IOException e) {
                return null;
            }
        }

        /** {@code "VoucherClient#validate(...)"} → {@code "voucher-service"}. */
        private static String extractServiceName(String methodKey) {
            int hash = methodKey.indexOf('#');
            String clientName = hash > 0 ? methodKey.substring(0, hash) : methodKey;
            return clientName.replace("Client", "-service").toLowerCase();
        }
    }
}
