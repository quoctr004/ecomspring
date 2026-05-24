package com.hieu.auth_service.interfaces.rest;

import com.hieu.auth_service.application.command.AssignRoleCommand;
import com.hieu.auth_service.application.command.ChangeAccountStatusCommand;
import com.hieu.auth_service.application.command.ChangeAccountStatusCommand.Transition;
import com.hieu.auth_service.application.command.UnassignRoleCommand;
import com.hieu.auth_service.application.command.UpdateEmailCommand;
import com.hieu.auth_service.application.common.CommandHandler;
import com.hieu.auth_service.application.common.QueryHandler;
import com.hieu.auth_service.application.dto.PageDTO;
import com.hieu.auth_service.application.dto.UserDTO;
import com.hieu.auth_service.application.query.CheckPermissionQuery;
import com.hieu.auth_service.application.query.CheckRoleQuery;
import com.hieu.auth_service.application.query.GetUserByIdQuery;
import com.hieu.auth_service.application.query.ListUsersQuery;
import com.hieu.auth_service.infrastructure.security.AuthUserDetails;
import com.hieu.auth_service.interfaces.rest.dto.RoleAssignmentRequest;
import com.hieu.auth_service.interfaces.rest.dto.UpdateEmailRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * User-management endpoints.
 *
 * <p>Access control is enforced via {@code @PreAuthorize} using roles coming from
 * {@link AuthUserDetails#getAuthorities()}. Admin-only endpoints require {@code ROLE_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "Profile, authorisation lookups, and admin management.")
@RequiredArgsConstructor
public class UserController {

    private final QueryHandler<GetUserByIdQuery, UserDTO> getUserByIdHandler;
    private final QueryHandler<ListUsersQuery, PageDTO<UserDTO>> listUsersHandler;
    private final QueryHandler<CheckRoleQuery, Boolean> checkRoleHandler;
    private final QueryHandler<CheckPermissionQuery, Boolean> checkPermissionHandler;
    private final CommandHandler<UpdateEmailCommand, UserDTO> updateEmailHandler;
    private final CommandHandler<AssignRoleCommand, Void> assignRoleHandler;
    private final CommandHandler<UnassignRoleCommand, Void> unassignRoleHandler;
    private final CommandHandler<ChangeAccountStatusCommand, Void> changeAccountStatusHandler;

    /** Returns the authenticated user's full profile, including effective permissions. */
    @Operation(summary = "Current user's profile")
    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(@AuthenticationPrincipal AuthUserDetails principal) {
        return ResponseEntity.ok(getUserByIdHandler.handle(new GetUserByIdQuery(principal.userId())));
    }

    /** Updates the authenticated user's email. */
    @Operation(summary = "Update current user's email")
    @PatchMapping("/me/email")
    public ResponseEntity<UserDTO> updateMyEmail(@AuthenticationPrincipal AuthUserDetails principal,
                                                  @Valid @RequestBody UpdateEmailRequest request) {
        return ResponseEntity.ok(updateEmailHandler.handle(
                new UpdateEmailCommand(principal.userId(), request.newEmail())));
    }

    @Operation(summary = "Lookup a user by id (admin)")
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> byId(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(getUserByIdHandler.handle(new GetUserByIdQuery(userId)));
    }

    @Operation(summary = "List users with cursor pagination (admin)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageDTO<UserDTO>> list(
            @Parameter(description = "Opaque cursor from previous response's nextCursor; omit for first page")
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(listUsersHandler.handle(new ListUsersQuery(cursor, limit)));
    }

    @Operation(summary = "Check whether a user has a role")
    @GetMapping("/{userId}/has-role/{roleName}")
    public ResponseEntity<Boolean> hasRole(@PathVariable("userId") String userId,
                                           @PathVariable("roleName") String roleName) {
        return ResponseEntity.ok(checkRoleHandler.handle(new CheckRoleQuery(userId, roleName)));
    }

    @Operation(summary = "Check whether a user has a permission")
    @GetMapping("/{userId}/has-permission/{permissionName}")
    public ResponseEntity<Boolean> hasPermission(@PathVariable("userId") String userId,
                                                  @PathVariable("permissionName") String permissionName) {
        return ResponseEntity.ok(checkPermissionHandler.handle(
                new CheckPermissionQuery(userId, permissionName)));
    }

    @Operation(summary = "Assign a role (admin)")
    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignRole(@PathVariable("userId") String userId,
                                           @Valid @RequestBody RoleAssignmentRequest request) {
        assignRoleHandler.handle(new AssignRoleCommand(userId, request.roleName()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unassign a role (admin)")
    @DeleteMapping("/{userId}/roles/{roleName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unassignRole(@PathVariable("userId") String userId,
                                             @PathVariable("roleName") String roleName) {
        unassignRoleHandler.handle(new UnassignRoleCommand(userId, roleName));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Transition a user's account status (admin)")
    @PostMapping("/{userId}/status/{transition}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changeStatus(@PathVariable("userId") String userId,
                                             @PathVariable("transition") Transition transition) {
        changeAccountStatusHandler.handle(new ChangeAccountStatusCommand(userId, transition));
        return ResponseEntity.noContent().build();
    }
}
