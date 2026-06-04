package com.hieu.auth_service.application.handler;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hieu.auth_service.application.command.RefreshTokenCommand;
import com.hieu.auth_service.application.common.CommandHandler;
import com.hieu.auth_service.application.dto.AuthResponseDTO;
import com.hieu.auth_service.application.mapper.UserDtoMapper;
import com.hieu.auth_service.domain.models.refreshtoken.RefreshToken;
import com.hieu.auth_service.domain.models.refreshtoken.exceptions.TokenReuseDetectedException;
import com.hieu.auth_service.domain.models.refreshtoken.exceptions.TokenRevokedException;
import com.hieu.auth_service.domain.models.refreshtoken.vo.TokenValue;
import com.hieu.auth_service.domain.models.role.Role;
import com.hieu.auth_service.domain.models.user.User;
import com.hieu.auth_service.domain.models.user.exceptions.UserNotFoundException;
import com.hieu.auth_service.domain.repositories.RefreshTokenRepository;
import com.hieu.auth_service.domain.repositories.RoleRepository;
import com.hieu.auth_service.domain.repositories.UserRepository;
import com.hieu.auth_service.domain.services.TokenDomainService;
import com.hieu.auth_service.domain.services.TokenProviderPort;

import lombok.RequiredArgsConstructor;

/**
 * Handles {@link RefreshTokenCommand} via the Rotation + Family Revocation pattern.
 *
 * <p>Rotation is delegated to {@link TokenDomainService#rotate}, which also handles
 * reuse detection: replaying a revoked token revokes the whole family and throws.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenHandler implements CommandHandler<RefreshTokenCommand, AuthResponseDTO> {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenDomainService tokenDomainService;
    private final TokenProviderPort tokenProvider;
    private final UserDtoMapper userDtoMapper;

    @Value("${jwt.refresh-expiration-days:7}")
    private int refreshExpiryDays;

    @Override
    // Reuse detection revokes the whole family as a side effect *and then* throws. That
    // family revocation must survive — so this exception must NOT roll the transaction back,
    // unlike every other failure here (bad/expired token) where rollback is correct.
    @Transactional(noRollbackFor = TokenReuseDetectedException.class)
    public AuthResponseDTO handle(RefreshTokenCommand command) {
        // Pessimistic lock on the token row: two concurrent refreshes of the same
        // token serialize so the second one sees a now-revoked token and triggers
        // family revocation rather than both issuing fresh access tokens.
        RefreshToken presented = refreshTokenRepository.findByTokenValueForUpdate(TokenValue.of(command.refreshToken()))
                .orElseThrow(() -> new TokenRevokedException(null, null));

        // Rotate may throw TokenReuseDetectedException; family revocation already happened inside.
        RefreshToken rotated = tokenDomainService.rotate(presented, refreshExpiryDays, refreshTokenRepository);
        refreshTokenRepository.save(rotated);

        User user = userRepository.findById(presented.getUserId())
                .orElseThrow(() -> new UserNotFoundException(presented.getUserId().value()));
        List<Role> userRoles = roleRepository.findByIdIn(user.getRoles());
        Set<String> roleNames = userRoles.stream()
                .map(r -> r.getName().value())
                .collect(Collectors.toSet());

        var issued = tokenProvider.issueAccessToken(user, roleNames);
        return AuthResponseDTO.bearer(
                issued.token(),
                rotated.getValue().value(),
                issued.expiresInSeconds(),
                userDtoMapper.toDto(user, userRoles));
    }
}
