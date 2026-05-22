package com.hieu.auth_service.interfaces.rest.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hieu.auth_service.application.port.TokenBlacklistPort;
import com.hieu.auth_service.domain.services.TokenProviderPort;
import com.hieu.auth_service.infrastructure.security.AuthUserDetails;
import com.hieu.auth_service.infrastructure.security.CustomUserDetailsService;
import com.hieu.auth_service.interfaces.rest.support.AuthCookieWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless JWT authentication filter that integrates cleanly with Spring Security.
 *
 * <p>On every request the filter:
 * <ol>
 *   <li>Extracts the bearer token from the {@code Authorization} header.</li>
 *   <li>Parses + signature-verifies the access token via {@link TokenProviderPort}.</li>
 *   <li>Rejects the request if the {@code jti} has been blacklisted
 *       (see {@link TokenBlacklistPort}).</li>
 *   <li>Loads the full {@link AuthUserDetails} via {@link CustomUserDetailsService#loadUserById(String)}
 *       so the principal carries the current account-status flags + rehydrated authorities.</li>
 *   <li>Compares the JWT's {@code tokenVersion} against the user's current version —
 *       tokens minted before a password change are rejected even if still unexpired.</li>
 *   <li>Populates {@link SecurityContextHolder} with a fully authenticated token whose
 *       principal is the {@link AuthUserDetails} (so controllers can use
 *       {@code @AuthenticationPrincipal AuthUserDetails}).</li>
 * </ol>
 *
 * <p>Missing/invalid tokens silently leave the context empty — endpoint matchers
 * (configured in {@code SecurityConfig}) decide which routes require authentication.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final TokenProviderPort tokenProvider;
    private final TokenBlacklistPort tokenBlacklist;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(TokenProviderPort tokenProvider,
                                   TokenBlacklistPort tokenBlacklist,
                                   CustomUserDetailsService userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.tokenBlacklist = tokenBlacklist;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractBearer(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                TokenProviderPort.AccessClaims claims = tokenProvider.parseAccessToken(token);

                if (tokenBlacklist.isRevoked(claims.tokenId())) {
                    log.debug("Rejected revoked token jti={}", claims.tokenId());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
                    return;
                }

                UserDetails userDetails = userDetailsService.loadUserById(claims.userId());

                // tokenVersion guard: reject tokens minted before password change / admin revoke.
                if (userDetails instanceof AuthUserDetails aud && aud.tokenVersion() != claims.tokenVersion()) {
                    log.debug("tokenVersion mismatch for userId={} (jwt={}, current={})",
                            claims.userId(), claims.tokenVersion(), aud.tokenVersion());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token superseded");
                    return;
                }

                var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, token, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (UsernameNotFoundException notFound) {
                log.debug("JWT principal not found in repository: {}", notFound.getMessage());
                SecurityContextHolder.clearContext();
            } catch (Exception parseError) {
                // Expired/malformed tokens are a normal part of public-endpoint traffic.
                log.debug("JWT parse failed: {}", parseError.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Extracts the access token from the {@code ACCESS_TOKEN} HttpOnly cookie or,
     * as a fallback, from the {@code Authorization: Bearer …} header.
     *
     * <p>Cookie-first ordering lets browser clients skip the Authorization header entirely;
     * API clients (mobile, CLI, gRPC gateways) keep using Bearer.
     *
     * @param request servlet request
     * @return raw token string, or {@code null} when neither source carries a usable value
     */
    private static String extractBearer(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (AuthCookieWriter.ACCESS_COOKIE.equals(c.getName())) {
                    String value = c.getValue();
                    if (value != null && !value.isBlank()) return value;
                }
            }
        }
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) return null;
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
