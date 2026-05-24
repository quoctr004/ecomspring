package com.hieu.auth_service.interfaces.rest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hieu.auth_service.application.command.ChangePasswordCommand;
import com.hieu.auth_service.application.command.LoginCommand;
import com.hieu.auth_service.application.command.LoginWithGoogleCommand;
import com.hieu.auth_service.application.command.LogoutCommand;
import com.hieu.auth_service.application.command.RefreshTokenCommand;
import com.hieu.auth_service.application.command.RegisterUserCommand;
import com.hieu.auth_service.application.common.CommandHandler;
import com.hieu.auth_service.application.dto.AuthResponseDTO;
import com.hieu.auth_service.domain.services.TokenProviderPort;
import com.hieu.auth_service.infrastructure.security.AuthUserDetails;
import com.hieu.auth_service.interfaces.rest.dto.AuthMeResponse;
import com.hieu.auth_service.interfaces.rest.dto.ChangePasswordRequest;
import com.hieu.auth_service.interfaces.rest.dto.GoogleLoginRequest;
import com.hieu.auth_service.interfaces.rest.dto.LoginRequest;
import com.hieu.auth_service.interfaces.rest.dto.RegisterRequest;
import com.hieu.auth_service.interfaces.rest.support.AuthCookieWriter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Public authentication endpoints.
 *
 * <p>Cookie-based auth: register / login / refresh set {@code ACCESS_TOKEN} and
 * {@code REFRESH_TOKEN} HttpOnly cookies instead of returning tokens in the body.
 * The {@link com.hieu.auth_service.interfaces.rest.filter.JwtAuthenticationFilter}
 * reads the access token from either the cookie or the {@code Authorization} header —
 * API clients can still pass {@code Bearer} for non-browser use.
 *
 * <p>Controllers stay thin: adapt HTTP → command, delegate, map response.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register, login, token rotation, logout, password change.")
@RequiredArgsConstructor
public class AuthController {

    private final CommandHandler<RegisterUserCommand, AuthResponseDTO> registerHandler;
    private final CommandHandler<LoginCommand, AuthResponseDTO> loginHandler;
    private final CommandHandler<LoginWithGoogleCommand, AuthResponseDTO> googleLoginHandler;
    private final CommandHandler<RefreshTokenCommand, AuthResponseDTO> refreshHandler;
    private final CommandHandler<LogoutCommand, Void> logoutHandler;
    private final CommandHandler<ChangePasswordCommand, Void> changePasswordHandler;
    private final AuthCookieWriter cookieWriter;
    private final TokenProviderPort tokenProvider;

    /**
     * "Who am I" endpoint — reads the principal materialised by the JWT filter.
     *
     * @param principal authenticated principal
     * @return 200 OK with a safe projection of the principal
     */
    @Operation(summary = "Current authenticated principal",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ResponseEntity<AuthMeResponse> me(@AuthenticationPrincipal AuthUserDetails principal) {
        return ResponseEntity.ok(AuthMeResponse.from(principal));
    }

    /**
     * Registers a new user account and sets auth cookies.
     *
     * @param request validated registration payload
     * @return 200 OK with Set-Cookie headers + user profile
     */
    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponseDTO tokens = registerHandler.handle(new RegisterUserCommand(
                request.username(), request.email(), request.password(),
                request.firstName(), request.lastName()));
        return cookieWriter.writeTokens(tokens);
    }

    /**
     * Username/email + password login → sets HttpOnly cookies.
     *
     * @param request validated credentials payload
     * @return 200 OK with Set-Cookie headers + user profile (no tokens in body)
     */
    @Operation(summary = "Username/email + password login")
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequest request) {
        AuthResponseDTO tokens = loginHandler.handle(new LoginCommand(
                request.usernameOrEmail(), request.password()));
        return cookieWriter.writeTokens(tokens);
    }

    /**
     * "Login with Google" — verifies a Google ID token (the {@code credential}
     * the frontend receives from Google Identity Services), find-or-creates a
     * local user, then sets the same HttpOnly cookies as the password flow.
     *
     * <p>The frontend obtains the token using the public Google client ID; the
     * backend verifies it against the same client ID. We never see the user's
     * Google password.
     *
     * @param request body with {@code idToken}
     * @return 200 OK with Set-Cookie headers + user profile
     */
    @Operation(summary = "Login with Google (ID token from Google Identity Services)")
    @PostMapping("/google")
    public ResponseEntity<AuthResponseDTO> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request) {
        AuthResponseDTO tokens = googleLoginHandler.handle(
                new LoginWithGoogleCommand(request.idToken()));
        return cookieWriter.writeTokens(tokens);
    }

    /**
     * Rotates the refresh token (read from cookie) into a fresh pair.
     *
     * @param request     optional legacy body carrying {@code refreshToken}
     * @param httpRequest servlet request used to read the REFRESH_TOKEN cookie
     * @return 200 OK with new Set-Cookie headers + user profile
     */
    @Operation(summary = "Rotate refresh token (cookie-first, falls back to body)")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(HttpServletRequest httpRequest,
                                                    @RequestBody(required = false) RefreshBody request) {
        String refreshToken = readRefreshToken(httpRequest, request);
        AuthResponseDTO tokens = refreshHandler.handle(new RefreshTokenCommand(refreshToken));
        return cookieWriter.writeTokens(tokens);
    }

    /**
     * Revokes the refresh cookie and blacklists the access JTI, then clears both cookies.
     *
     * @param httpRequest servlet request for reading current cookies
     * @return 204 No Content with cookie-expire headers
     */
    @Operation(summary = "Revoke session", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        String accessToken  = readAccessToken(httpRequest);
        String refreshToken = readCookie(httpRequest, AuthCookieWriter.REFRESH_COOKIE);
        logoutHandler.handle(new LogoutCommand(accessToken, refreshToken));

        HttpHeaders headers = new HttpHeaders();
        cookieWriter.expire(headers);
        return ResponseEntity.noContent().headers(headers).build();
    }

    /**
     * Changes the authenticated user's password — invalidates every outstanding token.
     */
    @Operation(summary = "Change current user's password",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUserDetails principal,
                                               @Valid @RequestBody ChangePasswordRequest request,
                                               HttpServletRequest httpRequest) {
        // C3: Extract JTI from the current access token so the handler can blacklist it immediately.
        String jti = null;
        java.time.Instant exp = null;
        String rawToken = readAccessToken(httpRequest);
        if (rawToken != null) {
            try {
                var claims = tokenProvider.parseAccessToken(rawToken);
                jti = claims.tokenId();
                exp = claims.expiresAt();
            } catch (Exception ignored) {
                // Malformed token — handler will log.warn and skip blacklist.
            }
        }

        changePasswordHandler.handle(new ChangePasswordCommand(
                principal.userId(), request.oldPassword(), request.newPassword(), jti, exp));

        HttpHeaders headers = new HttpHeaders();
        cookieWriter.expire(headers);
        return ResponseEntity.noContent().headers(headers).build();
    }

    /** Legacy body form for non-cookie clients (mobile, CLI). */
    public record RefreshBody(String refreshToken) {}

    // ── Cookie / header helpers ─────────────────────────────────────────

    /** Access token from ACCESS_TOKEN cookie (preferred) or Authorization header. */
    private static String readAccessToken(HttpServletRequest request) {
        String fromCookie = readCookie(request, AuthCookieWriter.ACCESS_COOKIE);
        if (fromCookie != null) return fromCookie;

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /** Refresh token from cookie first, then fallback JSON body. */
    private static String readRefreshToken(HttpServletRequest request, RefreshBody body) {
        String fromCookie = readCookie(request, AuthCookieWriter.REFRESH_COOKIE);
        if (fromCookie != null) return fromCookie;
        return body == null ? null : body.refreshToken();
    }

    /** Returns the value of the named cookie, or null. */
    private static String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) {
                String value = c.getValue();
                return (value == null || value.isBlank()) ? null : value;
            }
        }
        return null;
    }
}
