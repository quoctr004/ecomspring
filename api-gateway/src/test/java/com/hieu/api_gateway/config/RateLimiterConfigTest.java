package com.hieu.api_gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the rate-limiter key resolvers.
 *
 * <p>Both resolvers are pure functions of the incoming request — no Spring context
 * required. {@code MockServerWebExchange} stands in for a real {@code ServerWebExchange}
 * and {@code Mono.block()} drives the result synchronously (safe here because the
 * resolver returns immediately — no I/O involved).
 */
class RateLimiterConfigTest {

    private final RateLimiterConfig config = new RateLimiterConfig();

    // ── ipKeyResolver ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("ipKeyResolver uses remote address when present")
    void ipKeyResolver_returnsIpKey_whenRemoteAddressPresent() {
        KeyResolver resolver = config.ipKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/anything")
                        .remoteAddress(new InetSocketAddress("203.0.113.42", 12345)));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("ip:203.0.113.42");
    }

    @Test
    @DisplayName("ipKeyResolver falls back to 'unknown' when remote address missing")
    void ipKeyResolver_returnsUnknown_whenRemoteAddressMissing() {
        KeyResolver resolver = config.ipKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/anything"));   // no remoteAddress

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("ip:unknown");
    }

    // ── userKeyResolver ───────────────────────────────────────────────────────

    @Test
    @DisplayName("userKeyResolver prefers X-User-Id header over IP")
    void userKeyResolver_returnsUserKey_whenUserIdHeaderPresent() {
        KeyResolver resolver = config.userKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header("X-User-Id", "user-123")
                        .remoteAddress(new InetSocketAddress("203.0.113.42", 12345)));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("user:user-123");
    }

    @Test
    @DisplayName("userKeyResolver falls back to IP when X-User-Id is missing")
    void userKeyResolver_fallsBackToIp_whenNoUserId() {
        KeyResolver resolver = config.userKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .remoteAddress(new InetSocketAddress("203.0.113.42", 12345)));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("ip:203.0.113.42");
    }

    @Test
    @DisplayName("userKeyResolver falls back to IP when X-User-Id is blank")
    void userKeyResolver_fallsBackToIp_whenUserIdBlank() {
        KeyResolver resolver = config.userKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header("X-User-Id", "   ")
                        .remoteAddress(new InetSocketAddress("198.51.100.7", 12345)));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("ip:198.51.100.7");
    }

    @Test
    @DisplayName("userKeyResolver returns 'ip:unknown' when neither user nor IP present")
    void userKeyResolver_returnsUnknown_whenNothingPresent() {
        KeyResolver resolver = config.userKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("ip:unknown");
    }
}
