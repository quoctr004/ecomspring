package com.hieu.notification_service.service;

import com.hieu.notification_service.dto.NotificationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reactive in-app push: per-user {@link Sinks.Many} multicast sinks for SSE.
 *
 * <p>Migrated from the MVC {@code SseEmitter} version. Reactor's sink-based
 * model removes the manual emitter lifecycle bookkeeping entirely — when a
 * subscriber disconnects, the {@link Flux} terminates and Spring WebFlux
 * cleans up the underlying connection.
 *
 * <h2>Why {@code multicast().directBestEffort()}</h2>
 * <ul>
 *   <li>One sink per user; multiple browser tabs / devices all subscribe.</li>
 *   <li>{@code directBestEffort} drops events for slow subscribers instead of
 *       back-pressuring the publisher — perfect for "live notifications" where
 *       missing a few events is fine, but blocking the writer is not.</li>
 * </ul>
 *
 * <p>Keep-alive comments are merged into the stream every 15s so reverse
 * proxies (Nginx, ALB) don't time out the long-lived HTTP connection.
 */
@Service
@Slf4j
public class InAppPushService {

    private static final Duration KEEP_ALIVE_INTERVAL = Duration.ofSeconds(15);

    /** userId → multicast sink of notification events. Created lazily on first subscribe. */
    private final Map<String, Sinks.Many<NotificationDTO>> sinks = new ConcurrentHashMap<>();

    /**
     * Open an SSE stream for {@code userId}. The returned {@link Flux} terminates
     * when the HTTP client disconnects — Spring WebFlux handles the lifecycle.
     */
    public Flux<ServerSentEvent<?>> subscribe(String userId) {
        Sinks.Many<NotificationDTO> sink = sinkFor(userId);

        Flux<ServerSentEvent<?>> events = sink.asFlux()
                .map(dto -> ServerSentEvent.builder().event("notification").data((Object) dto).build());

        // Keep-alive comment merged inline keeps proxies from killing idle connections.
        Flux<ServerSentEvent<?>> keepAlive = Flux.interval(KEEP_ALIVE_INTERVAL)
                .map(tick -> ServerSentEvent.builder().comment("keep-alive").build());

        return Flux.merge(events, keepAlive)
                .doOnSubscribe(s -> log.debug("SSE subscribed userId={}", userId))
                .doFinally(signal -> log.debug("SSE closed userId={} signal={}", userId, signal));
    }

    /**
     * Push a notification to every active SSE subscriber of {@code userId}.
     * Returns immediately — {@code tryEmitNext} is non-blocking. On a missing
     * sink (no live subscribers) the event is silently dropped, which is the
     * correct behaviour for a "live feed" channel.
     */
    public void push(String userId, NotificationDTO dto) {
        Sinks.Many<NotificationDTO> sink = sinks.get(userId);
        if (sink == null) return;

        Sinks.EmitResult result = sink.tryEmitNext(dto);
        if (result.isFailure()) {
            log.debug("SSE push dropped userId={} reason={}", userId, result);
        }
    }

    /** Lazily allocate per-user sinks; multiple subscribers per user are supported. */
    private Sinks.Many<NotificationDTO> sinkFor(String userId) {
        return sinks.computeIfAbsent(userId,
                k -> Sinks.many().multicast().directBestEffort());
    }
}
