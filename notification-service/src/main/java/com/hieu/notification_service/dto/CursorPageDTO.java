package com.hieu.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Cursor-based pagination envelope for infinite scroll.
 *
 * <p>{@code nextCursor} is the {@link String} ObjectId hex of the last item on
 * this page — clients pass it back unchanged on the next request to fetch
 * older entries. {@code null} means "end of feed".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CursorPageDTO<T>(
        List<T> items,
        String nextCursor,
        int size,
        boolean hasNext
) {}
