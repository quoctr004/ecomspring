package com.hieu.notification_service.controller;

import com.hieu.common.security.AuthenticatedUser;
import com.hieu.notification_service.dto.CursorPageDTO;
import com.hieu.notification_service.dto.NotificationDTO;
import com.hieu.notification_service.dto.PageDTO;
import com.hieu.notification_service.dto.SendNotificationRequest;
import com.hieu.notification_service.service.InAppPushService;
import com.hieu.notification_service.service.NotificationApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * WebFlux REST controller for notifications.
 *
 * <p>Migrated from Spring MVC: every endpoint returns {@link Mono}/{@link Flux};
 * SSE is served via {@link ServerSentEvent} (no more {@code SseEmitter} +
 * scheduler bookkeeping). IDs are {@link String} (MongoDB ObjectId hex).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification management API")
public class NotificationController {

    private final NotificationApplicationService notificationService;
    private final InAppPushService inAppPushService;

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or hasAnyAuthority('ROLE_ADMIN', 'ROLE_SYSTEM')")
    @Operation(summary = "Send a notification (ADMIN only)")
    public Mono<ResponseEntity<NotificationDTO>> send(@Valid @RequestBody SendNotificationRequest req) {
        return notificationService.send(req)
                .map(dto -> ResponseEntity.status(HttpStatus.CREATED).body(dto));
    }

    @GetMapping("/my")
    @Operation(summary = "Get my notifications (offset paginated)")
    public Mono<PageDTO<NotificationDTO>> getMyNotifications(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.getMyNotifications(user.userId(), page, size);
    }

    @GetMapping("/my/feed")
    @Operation(summary = "Cursor-based feed for infinite scroll")
    public Mono<CursorPageDTO<NotificationDTO>> getMyFeed(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.getMyFeed(user.userId(), cursor, size);
    }

    @GetMapping("/my/unread-count")
    @Operation(summary = "Unread notification count")
    public Mono<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.getUnreadCount(user.userId())
                .map(count -> Map.of("count", count));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification by ID (own or ADMIN)")
    public Mono<NotificationDTO> getById(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        boolean isAdmin = user.hasAnyRole("ROLE_ADMIN", "ADMIN");
        return notificationService.getById(id, user.userId(), isAdmin);
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    public Mono<NotificationDTO> markAsRead(
            @PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.markAsRead(id, user.userId());
    }

    @PutMapping("/my/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark all notifications as read")
    public Mono<Void> markAllAsRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.markAllReadForUser(user.userId()).then();
    }

    /**
     * SSE stream: client subscribes once and receives real-time notification pushes.
     * The underlying {@link reactor.core.publisher.Sinks.Many} multicasts to all
     * tabs/devices the user has connected. Keep-alive comments are merged inside
     * {@link InAppPushService#subscribe(String)} every 15s.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE stream of real-time notifications")
    public Flux<ServerSentEvent<?>> stream(@AuthenticationPrincipal AuthenticatedUser user) {
        return inAppPushService.subscribe(user.userId());
    }
}
