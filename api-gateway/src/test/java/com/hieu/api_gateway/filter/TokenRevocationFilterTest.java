package com.hieu.api_gateway.filter;

import com.hieu.api_gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit test for the global token-revocation filter.
 *
 * <p>The filter's contract:
 * <ul>
 *   <li>No bearer token → pass through (public routes).</li>
 *   <li>Token has no jti → pass through (legacy tokens).</li>
 *   <li>Malformed token → pass through (per-route filter handles 401).</li>
 *   <li>jti present in Redis → 401 (revoked).</li>
 *   <li>jti not in Redis → pass through.</li>
 *   <li>Redis unavailable → <b>fail open</b> — pass through with warning.</li>
 * </ul>
 */
class TokenRevocationFilterTest {

    private static final String TOKEN = "valid.jwt";
    private static final String JTI = "jti-123";
    private static final String BLACKLIST_KEY = "blacklist:" + JTI;

    private ReactiveStringRedisTemplate redisTemplate;
    private JwtUtil jwtUtil;
    private TokenRevocationFilter filter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        jwtUtil = mock(JwtUtil.class);
        filter = new TokenRevocationFilter(redisTemplate, jwtUtil);
    }

    // ── Pass-through scenarios ────────────────────────────────────────────────

    @Test
    @DisplayName("Passes through public requests (no bearer token)")
    void passesThrough_whenNoToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products"));
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.filter(exchange, recordingChain(chainCalled)).block();

        assertThat(chainCalled).isTrue();
        verifyNoInteractions(redisTemplate, jwtUtil);
    }

    @Test
    @DisplayName("Passes through when JwtUtil throws extracting jti (per-route filter will reject)")
    void passesThrough_whenJwtUtilThrows() {
        MockServerWebExchange exchange = exchangeWithBearer(TOKEN);
        when(jwtUtil.extractTokenId(TOKEN)).thenThrow(new RuntimeException("Bad signature"));
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.filter(exchange, recordingChain(chainCalled)).block();

        assertThat(chainCalled).isTrue();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Passes through legacy tokens without a jti claim")
    void passesThrough_whenJtiIsNull() {
        MockServerWebExchange exchange = exchangeWithBearer(TOKEN);
        when(jwtUtil.extractTokenId(TOKEN)).thenReturn(null);
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.filter(exchange, recordingChain(chainCalled)).block();

        assertThat(chainCalled).isTrue();
        verifyNoInteractions(redisTemplate);
    }

    // ── Redis hit / miss ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns 401 when jti is blacklisted in Redis")
    void rejects_whenJtiInBlacklist() {
        MockServerWebExchange exchange = exchangeWithBearer(TOKEN);
        when(jwtUtil.extractTokenId(TOKEN)).thenReturn(JTI);
        when(redisTemplate.hasKey(BLACKLIST_KEY)).thenReturn(Mono.just(true));
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.filter(exchange, recordingChain(chainCalled)).block();

        assertThat(chainCalled).isFalse();   // chain was short-circuited
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(redisTemplate).hasKey(BLACKLIST_KEY);
    }

    @Test
    @DisplayName("Passes through when jti is NOT in blacklist")
    void passesThrough_whenJtiNotInBlacklist() {
        MockServerWebExchange exchange = exchangeWithBearer(TOKEN);
        when(jwtUtil.extractTokenId(TOKEN)).thenReturn(JTI);
        when(redisTemplate.hasKey(BLACKLIST_KEY)).thenReturn(Mono.just(false));
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.filter(exchange, recordingChain(chainCalled)).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Defaults to 'not blacklisted' when Redis returns empty Mono")
    void passesThrough_whenRedisReturnsEmpty() {
        MockServerWebExchange exchange = exchangeWithBearer(TOKEN);
        when(jwtUtil.extractTokenId(TOKEN)).thenReturn(JTI);
        when(redisTemplate.hasKey(BLACKLIST_KEY)).thenReturn(Mono.empty());
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.filter(exchange, recordingChain(chainCalled)).block();

        assertThat(chainCalled).isTrue();
    }

    // ── Fail-open policy ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Fails open when Redis is unavailable (better availability than security)")
    void failsOpen_whenRedisUnavailable() {
        MockServerWebExchange exchange = exchangeWithBearer(TOKEN);
        when(jwtUtil.extractTokenId(TOKEN)).thenReturn(JTI);
        when(redisTemplate.hasKey(BLACKLIST_KEY))
                .thenReturn(Mono.error(new RuntimeException("Connection refused")));
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.filter(exchange, recordingChain(chainCalled)).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ── Token source priority ─────────────────────────────────────────────────

    @Test
    @DisplayName("Reads token from ACCESS_TOKEN cookie before Authorization header")
    void cookieTakesPrecedenceOverHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer from-header")
                        .cookie(new HttpCookie("ACCESS_TOKEN", "from-cookie")));
        when(jwtUtil.extractTokenId("from-cookie")).thenReturn(JTI);
        when(redisTemplate.hasKey(BLACKLIST_KEY)).thenReturn(Mono.just(false));

        filter.filter(exchange, recordingChain(new AtomicBoolean())).block();

        verify(jwtUtil).extractTokenId("from-cookie");
        verify(jwtUtil, never()).extractTokenId("from-header");
    }

    // ── Filter order ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Filter runs early — order = HIGHEST_PRECEDENCE + 1 (right after LoggingFilter)")
    void filterRunsEarly() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static MockServerWebExchange exchangeWithBearer(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    /** Chain that flips a flag when called so tests can assert pass-through behaviour. */
    private static GatewayFilterChain recordingChain(AtomicBoolean called) {
        return (ServerWebExchange exchange) -> {
            called.set(true);
            return Mono.empty();
        };
    }
}
