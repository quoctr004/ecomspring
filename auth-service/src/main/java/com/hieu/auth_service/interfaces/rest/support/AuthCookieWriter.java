package com.hieu.auth_service.interfaces.rest.support;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.hieu.auth_service.application.dto.AuthResponseDTO;

/**
 * Writes access + refresh tokens onto HttpOnly cookies and returns a sanitised body.
 *
 * <p>Flow:
 * <ul>
 *   <li>{@code ACCESS_TOKEN} cookie — {@code HttpOnly}, {@code Secure}, {@code SameSite=Lax},
 *       path=/, max-age = JWT access TTL.</li>
 *   <li>{@code REFRESH_TOKEN} cookie — same flags, path=/api/auth (only sent on auth-specific
 *       calls) to reduce unnecessary exposure on every request.</li>
 *   <li>Response body strips the raw tokens; only the {@code user} profile remains.</li>
 * </ul>
 *
 * <p>{@code Secure} is toggled via {@code auth.cookie.secure} property (default true);
 * local dev running plain HTTP sets it to false.
 */
@Component
public class AuthCookieWriter {

    public static final String ACCESS_COOKIE = "ACCESS_TOKEN";
    public static final String REFRESH_COOKIE = "REFRESH_TOKEN";
    private static final String REFRESH_PATH = "/api/auth";

    @Value("${auth.cookie.secure:true}")
    private boolean secure;

    @Value("${auth.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${jwt.refresh-expiration-days:7}")
    private int refreshExpirationDays;

    /**
     * Builds a 200 response with Set-Cookie headers and a body containing only the
     * user profile. Token strings are zeroed out so an accidental client-side log
     * never captures them.
     */
    public ResponseEntity<AuthResponseDTO> writeTokens(AuthResponseDTO source) {
        ResponseCookie access = baseCookie(ACCESS_COOKIE, source.accessToken(),
                Duration.ofSeconds(source.expiresInSeconds()))
                .path("/")
                .build();
        ResponseCookie refresh = baseCookie(REFRESH_COOKIE, source.refreshToken(),
                Duration.ofDays(refreshExpirationDays))
                .path(REFRESH_PATH)
                .build();

        AuthResponseDTO body = new AuthResponseDTO(null, null, source.tokenType(),
                source.expiresInSeconds(), source.user());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, access.toString())
                .header(HttpHeaders.SET_COOKIE, refresh.toString())
                .body(body);
    }

    /** Emits the expired-cookie headers used on logout. */
    public void expire(org.springframework.http.HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, expiringCookie(ACCESS_COOKIE, "/").toString());
        headers.add(HttpHeaders.SET_COOKIE, expiringCookie(REFRESH_COOKIE, REFRESH_PATH).toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(maxAge);
    }

    private ResponseCookie expiringCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
    }
}
