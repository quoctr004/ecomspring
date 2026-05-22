package com.hieu.auth_service.application.handler;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hieu.auth_service.application.common.QueryHandler;
import com.hieu.auth_service.application.dto.UserDTO;
import com.hieu.auth_service.application.mapper.UserDtoMapper;
import com.hieu.auth_service.application.query.GetUserByIdQuery;
import com.hieu.auth_service.domain.models.user.exceptions.UserNotFoundException;
import com.hieu.auth_service.domain.models.user.vo.UserId;
import com.hieu.auth_service.domain.repositories.PermissionRepository;
import com.hieu.auth_service.domain.repositories.RoleRepository;
import com.hieu.auth_service.domain.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Fetches a user's profile by id, including role and effective-permission names.
 *
 * <p>Permissions are pre-resolved in the query so clients don't need to traverse
 * role→permission twice for every request.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserByIdHandler implements QueryHandler<GetUserByIdQuery, UserDTO> {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserDtoMapper userDtoMapper;

    @Override
    public UserDTO handle(GetUserByIdQuery query) {
        var user = userRepository.findByIdWithRoles(UserId.of(query.userId()))
                .orElseThrow(() -> new UserNotFoundException(query.userId()));

        var roles = roleRepository.findByIdIn(user.getRoles());
        var permissionIds = roles.stream()
                .flatMap(r -> r.getPermissions().stream())
                .collect(Collectors.toSet());
        List<? extends com.hieu.auth_service.domain.models.permission.Permission> permissions =
                permissionIds.isEmpty() ? List.of() : permissionRepository.findByIdIn(permissionIds);

        return userDtoMapper.toDto(user, roles, List.copyOf(permissions));
    }
}
