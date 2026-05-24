package com.hieu.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Immutable read model for notification data.
 *
 * <p>{@code id} is a {@link String} (MongoDB ObjectId hex). Pre-MongoDB
 * persistence used {@code Long} primary keys — frontend / other services
 * treat the value as opaque, so the swap is contained here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationDTO(
        String id,
        String userId,
        String type,
        String channel,
        String title,
        String content,
        String status,
        boolean isRead,
        String errorMessage,
        String referenceType,
        String referenceId,
        Instant createdAt,
        Instant sentAt,
        Instant readAt
) {}
