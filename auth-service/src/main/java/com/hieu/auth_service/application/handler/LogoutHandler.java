package com.hieu.auth_service.application.handler;

import com.hieu.auth_service.application.command.LogoutCommand;
import com.hieu.auth_service.application.common.CommandHandler;
import com.hieu.auth_service.application.port.TokenBlacklistPort;
import com.hieu.auth_service.domain.models.refreshtoken.RefreshToken;
import com.hieu.auth_service.domain.models.refreshtoken.vo.RevokedReason;
import com.hieu.auth_service.domain.models.refreshtoken.vo.TokenValue;
import com.hieu.auth_service.domain.repositories.RefreshTokenRepository;
import com.hieu.auth_service.domain.services.TokenProviderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles {@link LogoutCommand}: revokes the refresh token and blacklists the access token
 * so subsequent requests with the same credentials fail immediately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogoutHandler implements CommandHandler<LogoutCommand, Void> {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenProviderPort tokenProvider;
    private final TokenBlacklistPort tokenBlacklist;

    @Override
    @Transactional
    public Void handle(LogoutCommand command) {
        // Step 1: revoke the refresh token (idempotent — missing token simply means the user is already out).
        // Refresh cookie may legitimately be absent (e.g. SameSite=Strict + cross-origin, expired,
        // or never issued for stateless API clients) — skip lookup instead of crashing on TokenValue.of(null).
        if (command.refreshToken() != null && !command.refreshToken().isBlank()) {
            refreshTokenRepository.findByTokenValue(TokenValue.of(command.refreshToken()))
                    .ifPresent(this::revokeRefresh);
        }

        // H5: Blacklist access token in a separate try-catch AFTER refresh revoke has committed.
        // An expired/malformed access token must NOT roll back the already-committed refresh revoke.
        if (command.accessToken() != null && !command.accessToken().isBlank()) {
            try {
                var claims = tokenProvider.parseAccessToken(command.accessToken());
                tokenBlacklist.revoke(claims.tokenId(), claims.userId(), claims.expiresAt(), "LOGOUT");
            } catch (Exception e) {
                log.warn("Logout: could not blacklist access token (expired or malformed) — refresh already revoked: {}",
                        e.getMessage());
            }
        }
        return null;
    }

    private void revokeRefresh(RefreshToken t) {
        t.revoke(RevokedReason.USER_INITIATED);
        refreshTokenRepository.save(t);
    }
}
