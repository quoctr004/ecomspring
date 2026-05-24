package com.hieu.cart_service.config;

import feign.RequestInterceptor;
import feign.Retryer;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Shared Feign configuration — beans Spring Cloud OpenFeign cannot declare via
 * properties: retry policy + outbound auth interceptor. Timeouts and log level
 * are externalised to {@code application.yaml} under
 * {@code spring.cloud.openfeign.client.config.*} so ops can tune them without
 * a rebuild.
 *
 * <h2>Why the beans below stay in code</h2>
 * <ul>
 *   <li><b>{@link Retryer#NEVER_RETRY}</b> — opt out of Feign's default retry.
 *       Many calls are NOT idempotent; silent retry on a POST could double-write.
 *       Components that genuinely need retry declare {@code @Retryable} with
 *       explicit backoff + max attempts on the affected method.</li>
 *   <li><b>JWT propagation interceptor</b> — many downstream endpoints are
 *       JWT-protected. The interceptor lifts the {@code Authorization} header
 *       from the current servlet request and re-attaches it on outbound calls.
 *       No-op outside an HTTP request thread (Kafka consumers, scheduled jobs);
 *       downstream returns 401 which is the correct signal.</li>
 * </ul>
 */
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
     * <p>Two sources checked in order:
     * <ol>
     *   <li>{@code Authorization: Bearer ...} header — API clients.</li>
     *   <li>{@code ACCESS_TOKEN} cookie — browser clients. The gateway does NOT
     *       rewrite the cookie into a header for downstream services, so the
     *       interceptor has to do that itself.</li>
     * </ol>
     *
     * <p>If both are absent (Kafka consumer, scheduled job) the interceptor is
     * a no-op — downstream services that require auth respond with 401.
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
}
