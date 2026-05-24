package com.hieu.auth_service.interfaces.rest.filter;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised rate-limit configuration. Loaded from {@code rate-limit.*}
 * properties so ops can tune limits without recompiling.
 *
 * <p>Defaults match the original hard-coded values (5 req/min for login,
 * 3 req/min for register) so the property block is optional.
 *
 * @param login       per-IP limit for {@code /api/v1/auth/login}
 * @param register    per-IP limit for {@code /api/v1/auth/register}
 * @param whitelist   IPs that bypass rate limiting entirely (eg. monitoring,
 *                    internal load tests). Compared as exact strings — no CIDR.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        Endpoint login,
        Endpoint register,
        List<String> whitelist
) {
    public RateLimitProperties {
        login    = (login    != null) ? login    : new Endpoint(5, Duration.ofMinutes(1));
        register = (register != null) ? register : new Endpoint(3, Duration.ofMinutes(1));
        whitelist = (whitelist != null) ? whitelist : List.of();
    }

    /** Convenience: O(1) lookup instead of scanning the list per request. */
    public Set<String> whitelistSet() {
        return Set.copyOf(whitelist);
    }

    /**
     * @param limit   max requests per window
     * @param window  time window (eg. {@code PT1M} = 1 minute)
     */
    public record Endpoint(int limit, Duration window) {
        public Endpoint {
            if (limit  <= 0)      throw new IllegalArgumentException("limit must be > 0");
            if (window == null || window.isNegative() || window.isZero())
                throw new IllegalArgumentException("window must be positive");
        }
    }
}
