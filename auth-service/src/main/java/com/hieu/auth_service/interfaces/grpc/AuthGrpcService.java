package com.hieu.auth_service.interfaces.grpc;

import com.hieu.auth_service.application.common.QueryHandler;
import com.hieu.auth_service.application.dto.UserDTO;
import com.hieu.auth_service.application.port.TokenBlacklistPort;
import com.hieu.auth_service.application.query.CheckPermissionQuery;
import com.hieu.auth_service.application.query.CheckRoleQuery;
import com.hieu.auth_service.application.query.GetUserByIdQuery;
import com.hieu.auth_service.domain.models.user.exceptions.UserNotFoundException;
import com.hieu.auth_service.domain.services.TokenProviderPort;
import com.hieu.auth_service.interfaces.grpc.proto.AuthServiceGrpc;
import com.hieu.auth_service.interfaces.grpc.proto.CheckPermissionRequest;
import com.hieu.auth_service.interfaces.grpc.proto.CheckPermissionResponse;
import com.hieu.auth_service.interfaces.grpc.proto.CheckRoleRequest;
import com.hieu.auth_service.interfaces.grpc.proto.CheckRoleResponse;
import com.hieu.auth_service.interfaces.grpc.proto.GetUserRequest;
import com.hieu.auth_service.interfaces.grpc.proto.GetUserResponse;
import com.hieu.auth_service.interfaces.grpc.proto.VerifyTokenRequest;
import com.hieu.auth_service.interfaces.grpc.proto.VerifyTokenResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC facade over the application layer — enables sibling microservices to
 * verify JWTs and run authorization checks without HTTP round-trips.
 *
 * <p>All heavy lifting is delegated to the same CQRS handlers used by the REST
 * layer, so there's one source of truth per use case. Token validation also
 * consults {@link TokenBlacklistPort} so a token revoked via REST is immediately
 * invisible via gRPC too.
 */
@GrpcService
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AuthGrpcService.class);

    private final TokenProviderPort tokenProvider;
    private final TokenBlacklistPort tokenBlacklist;
    private final QueryHandler<CheckRoleQuery, Boolean> checkRoleHandler;
    private final QueryHandler<CheckPermissionQuery, Boolean> checkPermissionHandler;
    private final QueryHandler<GetUserByIdQuery, UserDTO> getUserByIdHandler;

    /**
     * Parses + validates an access token. Invalid / expired / revoked tokens
     * surface as {@code valid=false} rather than a gRPC error status — the caller
     * gets a single Boolean to branch on.
     */
    @Override
    public void verifyToken(VerifyTokenRequest request,
                            StreamObserver<VerifyTokenResponse> observer) {
        VerifyTokenResponse.Builder reply = VerifyTokenResponse.newBuilder();
        try {
            var claims = tokenProvider.parseAccessToken(request.getAccessToken());
            if (tokenBlacklist.isRevoked(claims.tokenId())) {
                reply.setValid(false);
            } else {
                reply.setValid(true)
                        .setUserId(nullSafe(claims.userId()))
                        .setUsername(nullSafe(claims.username()))
                        .setTokenVersion(claims.tokenVersion())
                        .addAllRoles(claims.roles());
            }
        } catch (Exception e) {
            log.debug("gRPC verifyToken failed: {}", e.getMessage());
            reply.setValid(false);
        }
        observer.onNext(reply.build());
        observer.onCompleted();
    }

    /** Delegates to the {@link CheckRoleQuery} handler. */
    @Override
    public void checkRole(CheckRoleRequest request, StreamObserver<CheckRoleResponse> observer) {
        boolean ok = checkRoleHandler.handle(new CheckRoleQuery(request.getUserId(), request.getRoleName()));
        observer.onNext(CheckRoleResponse.newBuilder().setHasRole(ok).build());
        observer.onCompleted();
    }

    /** Delegates to the {@link CheckPermissionQuery} handler. */
    @Override
    public void checkPermission(CheckPermissionRequest request,
                                StreamObserver<CheckPermissionResponse> observer) {
        boolean allowed = checkPermissionHandler.handle(
                new CheckPermissionQuery(request.getUserId(), request.getPermissionName()));
        observer.onNext(CheckPermissionResponse.newBuilder().setAllowed(allowed).build());
        observer.onCompleted();
    }

    /**
     * Delegates to the {@link GetUserByIdQuery} handler.
     * Missing users return {@code found=false} rather than a NOT_FOUND status — sidesteps
     * gRPC status-code bikeshedding for what is essentially a cached lookup.
     */
    @Override
    public void getUser(GetUserRequest request, StreamObserver<GetUserResponse> observer) {
        GetUserResponse.Builder reply = GetUserResponse.newBuilder();
        try {
            UserDTO dto = getUserByIdHandler.handle(new GetUserByIdQuery(request.getUserId()));
            reply.setFound(true)
                    .setUserId(nullSafe(dto.id()))
                    .setUsername(nullSafe(dto.username()))
                    .setEmail(nullSafe(dto.email()))
                    .setActive(dto.enabled() && dto.accountNonLocked()
                            && dto.accountNonExpired() && dto.credentialsNonExpired());
            if (dto.roles() != null)        reply.addAllRoles(dto.roles());
            if (dto.permissions() != null)  reply.addAllPermissions(dto.permissions());
        } catch (UserNotFoundException | IllegalArgumentException notFound) {
            // Unknown id OR a malformed (non-UUID) id are both "no such user" to callers —
            // honour the found=false contract instead of leaking an error status.
            reply.setFound(false);
        }
        observer.onNext(reply.build());
        observer.onCompleted();
    }

    /** Protobuf strings cannot be null; normalise to empty instead. */
    private static String nullSafe(String s) { return s == null ? "" : s; }
}
