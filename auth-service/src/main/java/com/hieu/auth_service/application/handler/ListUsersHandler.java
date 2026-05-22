package com.hieu.auth_service.application.handler;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hieu.auth_service.application.common.CursorCodec;
import com.hieu.auth_service.application.common.QueryHandler;
import com.hieu.auth_service.application.dto.PageDTO;
import com.hieu.auth_service.application.dto.UserDTO;
import com.hieu.auth_service.application.mapper.UserDtoMapper;
import com.hieu.auth_service.application.query.ListUsersQuery;
import com.hieu.auth_service.domain.models.user.User;
import com.hieu.auth_service.domain.repositories.RoleRepository;
import com.hieu.auth_service.domain.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Lists users with keyset (cursor) pagination.
 *
 * <p>Decodes the opaque {@code cursor} (base64 of {@code createdAtMicros|id}) via
 * {@link CursorCodec}, runs a single-branch keyset query, and re-encodes the anchor
 * of the last returned row as {@code nextCursor}. {@code null nextCursor} signals
 * "end of results" — clients must stop paging.
 *
 * <p>Over-fetching by one row ({@code limit + 1}) is the standard trick to detect
 * {@code hasNext} without a separate count query.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListUsersHandler implements QueryHandler<ListUsersQuery, PageDTO<UserDTO>> {

    private static final int MAX_LIMIT = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserDtoMapper userDtoMapper;

    @Override
    public PageDTO<UserDTO> handle(ListUsersQuery query) {
        int pageSize = Math.clamp(query.limit(), 1, MAX_LIMIT);

        CursorCodec.Cursor cursor = CursorCodec.decode(query.cursor());
        Instant cursorCreatedAt = cursor == null ? null : cursor.createdAt();
        String  cursorId        = cursor == null ? null : cursor.id();

        // Over-fetch by 1 row so we can build nextCursor without a second query.
        List<User> rows = userRepository.findAfterCursor(cursorCreatedAt, cursorId, pageSize + 1);

        boolean hasNext = rows.size() > pageSize;
        List<User> pageRows = hasNext ? rows.subList(0, pageSize) : rows;
        
        List<UserDTO> items = pageRows.stream()
                .map(u -> userDtoMapper.toDto(u, roleRepository.findByIdIn(u.getRoles())))
                .toList();

        String nextCursor = null;
        if (hasNext && !pageRows.isEmpty()) {
            User last = pageRows.getLast();
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId().value());
        }

        return PageDTO.of(items, nextCursor, pageSize, -1);
    }
}
