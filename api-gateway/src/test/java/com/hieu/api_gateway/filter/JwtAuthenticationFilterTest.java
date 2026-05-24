package com.hieu.api_gateway.filter;

import com.hieu.api_gateway.util.JwtUtil;
import com.hieu.common.security.AuthHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test for the per-route JWT authentication filter.
 *
 * <p>Strategy: mock {@link JwtUtil} so we don't need a real signing key. The filter's
 * concern is request routing — header propagation and rejection paths — not signature
 * cryptography (that's covered by {@code common-lib}'s validator tests).
 *
 * <p>A capturing {@link GatewayFilterChain} records the mutated exchange so the
 * test can verify which headers were added before downstream services see the
 * request.
 */
class JwtAuthenticationFilterTest {

    private static final String VALID_TOKEN = "valid.jwt.token";

    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter filterFactory;
    private GatewayFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        filterFactory = new JwtAuthenticationFilter(jwtUtil);
        filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
    }

    // ── Reject paths ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns 401 when no token (cookie or Authorization header) is present")
    void rejects_whenNoToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders"));

        filter.filter(exchange, alwaysPassChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtUtil);   // never even tried to validate
    }

    @Test
    @DisplayName("Returns 401 when Authorization header doesn't start with 'Bearer '")
    void rejects_whenAuthorizationHeaderIsNotBearer() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"));

        filter.filter(exchange, alwaysPassChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Returns 401 when signature validation fails")
    void rejects_whenSignatureInvalid() {
        when(jwtUtil.validateSignature(VALID_TOKEN)).thenReturn(false);

        MockServerWebExchange exchange = exchangeWithBearer(VALID_TOKEN);
        filter.filter(exchange, alwaysPassChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(jwtUtil).validateSignature(VALID_TOKEN);
        verify(jwtUtil, never()).extractUserId(anyString());
    }

    @Test
    @DisplayName("Returns 401 when token is expired")
    void rejects_whenTokenExpired() {
        when(jwtUtil.validateSignature(VALID_TOKEN)).thenReturn(true);
        when(jwtUtil.isExpired(VALID_TOKEN)).thenReturn(true);

        MockServerWebExchange exchange = exchangeWithBearer(VALID_TOKEN);
        filter.filter(exchange, alwaysPassChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Returns 401 when JwtUtil throws (malformed token)")
    void rejects_whenJwtUtilThrows() {
        when(jwtUtil.validateSignature(VALID_TOKEN))
                .thenThrow(new RuntimeException("Malformed JWT"));

        MockServerWebExchange exchange = exchangeWithBearer(VALID_TOKEN);
        filter.filter(exchange, alwaysPassChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Happy paths ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Forwards request with identity headers when token is valid (Bearer header)")
    void forwards_withIdentityHeaders_whenBearerTokenValid() {
        stubValidToken();

        MockServerWebExchange exchange = exchangeWithBearer(VALID_TOKEN);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, captureChain(forwarded)).block();

        // Response not touched -> filter passed through
        assertThat(exchange.getResponse().getStatusCode()).isNull();

        // Headers were injected for downstream services
        HttpHeaders downstreamHeaders = forwarded.get().getRequest().getHeaders();
        assertThat(downstreamHeaders.getFirst(AuthHeaders.USER_ID)).isEqualTo("user-123");
        assertThat(downstreamHeaders.getFirst(AuthHeaders.USERNAME)).isEqualTo("hieu");
        assertThat(downstreamHeaders.getFirst(AuthHeaders.TOKEN_ID)).isEqualTo("jti-abc");
        assertThat(downstreamHeaders.getFirst(AuthHeaders.TOKEN_VERSION)).isEqualTo("5");
    }

    @Test
    @DisplayName("Reads token from ACCESS_TOKEN cookie when Authorization header is absent")
    void readsTokenFromCookie() {
        stubValidToken();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .cookie(ResponseCookie.from("ACCESS_TOKEN", VALID_TOKEN).build().toString().split(";")[0]
                                .equals("ACCESS_TOKEN=" + VALID_TOKEN)
                                ? new org.springframework.http.HttpCookie("ACCESS_TOKEN", VALID_TOKEN)
                                : new org.springframework.http.HttpCookie("ACCESS_TOKEN", VALID_TOKEN)));

        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, captureChain(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(AuthHeaders.USER_ID))
                .isEqualTo("user-123");
    }

    @Test
    @DisplayName("Cookie takes precedence over Authorization header when both present")
    void cookieTakesPrecedenceOverHeader() {
        when(jwtUtil.validateSignature("from-cookie")).thenReturn(true);
        when(jwtUtil.isExpired("from-cookie")).thenReturn(false);
        when(jwtUtil.extractUserId("from-cookie")).thenReturn("cookie-user");
        when(jwtUtil.extractUsername("from-cookie")).thenReturn("cookie");
        when(jwtUtil.extractTokenId("from-cookie")).thenReturn("jti-cookie");
        when(jwtUtil.extractTokenVersion("from-cookie")).thenReturn(1);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer from-header")
                        .cookie(new org.springframework.http.HttpCookie("ACCESS_TOKEN", "from-cookie")));

        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, captureChain(forwarded)).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(AuthHeaders.USER_ID))
                .isEqualTo("cookie-user");
        verify(jwtUtil).validateSignature("from-cookie");
        verify(jwtUtil, never()).validateSignature("from-header");
    }

    @Test
    @DisplayName("Null claim values are written as empty strings (not the literal 'null')")
    void nullClaimsAreWrittenAsEmptyStrings() {
        when(jwtUtil.validateSignature(VALID_TOKEN)).thenReturn(true);
        when(jwtUtil.isExpired(VALID_TOKEN)).thenReturn(false);
        when(jwtUtil.extractUserId(VALID_TOKEN)).thenReturn(null);
        when(jwtUtil.extractUsername(VALID_TOKEN)).thenReturn(null);
        when(jwtUtil.extractTokenId(VALID_TOKEN)).thenReturn(null);
        when(jwtUtil.extractTokenVersion(VALID_TOKEN)).thenReturn(0);

        MockServerWebExchange exchange = exchangeWithBearer(VALID_TOKEN);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, captureChain(forwarded)).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(AuthHeaders.USER_ID)).isEqualTo("");
        assertThat(headers.getFirst(AuthHeaders.USERNAME)).isEqualTo("");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubValidToken() {
        when(jwtUtil.validateSignature(VALID_TOKEN)).thenReturn(true);
        when(jwtUtil.isExpired(VALID_TOKEN)).thenReturn(false);
        when(jwtUtil.extractUserId(VALID_TOKEN)).thenReturn("user-123");
        when(jwtUtil.extractUsername(VALID_TOKEN)).thenReturn("hieu");
        when(jwtUtil.extractTokenId(VALID_TOKEN)).thenReturn("jti-abc");
        when(jwtUtil.extractTokenVersion(VALID_TOKEN)).thenReturn(5);
    }

    private static MockServerWebExchange exchangeWithBearer(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    /** Chain that always returns empty Mono (request passed downstream). */
    private static GatewayFilterChain alwaysPassChain() {
        return exchange -> Mono.empty();
    }

    /** Chain that captures the (possibly mutated) exchange for later inspection. */
    private static GatewayFilterChain captureChain(AtomicReference<ServerWebExchange> sink) {
        return exchange -> {
            sink.set(exchange);
            return Mono.empty();
        };
    }
}
