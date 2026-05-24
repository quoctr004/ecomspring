package com.hieu.notification_service.config;

import com.hieu.common.security.AuthenticatedUser;
import com.hieu.common.security.JwtTokenValidator;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Stream;

/**
 * Reactive WebFlux JWT filter.
 *
 * <p>Migrated from {@code OncePerRequestFilter} (servlet) to {@link WebFilter}
 * (reactive). Token sources unchanged: {@code ACCESS_TOKEN} cookie first, then
 * {@code Authorization: Bearer ...} header.
 *
 * <p>The principal is published into the reactive {@link ReactiveSecurityContextHolder}
 * — controllers can then resolve it via {@code @AuthenticationPrincipal}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements WebFilter {

    private static final String ACCESS_COOKIE = "ACCESS_TOKEN";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenValidator validator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return chain.filter(exchange);
        }

        Authentication auth = tryAuthenticate(token);
        if (auth == null) {
            // Invalid / expired token — proceed unauthenticated; protected routes
            // will return 401 via the SecurityWebFilterChain matchers.
            return chain.filter(exchange);
        }

        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(new SecurityContextImpl(auth))));
    }

    /** Returns {@code null} when the token is missing, expired, or unparseable. */
    private Authentication tryAuthenticate(String token) {
        try {
            if (validator.isExpired(token)) {
                log.debug("Rejected expired token");
                return null;
            }
            Claims claims = validator.parseClaims(token);
            var user = new AuthenticatedUser(
                    claims.get("userId", String.class),
                    claims.getSubject(),
                    extractList(claims, "roles"),
                    extractList(claims, "permissions"));
            var authorities = Stream.concat(user.roles().stream(), user.permissions().stream())
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            return new UsernamePasswordAuthenticationToken(user, token, authorities);
        } catch (Exception e) {
            log.debug("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractList(Claims claims, String key) {
        Object raw = claims.get(key);
        return raw instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of();
    }

    private static String extractToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(ACCESS_COOKIE);
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) return null;
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
