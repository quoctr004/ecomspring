package com.hieu.notification_service.service;

import com.hieu.notification_service.dto.CursorPageDTO;
import com.hieu.notification_service.dto.NotificationDTO;
import com.hieu.notification_service.dto.PageDTO;
import com.hieu.notification_service.dto.SendNotificationRequest;
import com.hieu.notification_service.entity.NotificationDocument;
import com.hieu.notification_service.entity.NotificationStatus;
import com.hieu.notification_service.exception.AccessDeniedException;
import com.hieu.notification_service.exception.NotificationNotFoundException;
import com.hieu.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

/**
 * Reactive application service for notifications.
 *
 * <p>Migrated from MVC + JPA: everything returns {@link Mono}/{@link Flux}, IDs
 * are MongoDB {@link ObjectId} hex strings, and bulk update uses
 * {@link ReactiveMongoTemplate} (reactive Mongo doesn't surface JPA-style
 * derived-update keywords).
 *
 * <h2>Concurrency model</h2>
 * <ul>
 *   <li>Persistence + lookup: native reactive (non-blocking Mongo driver).</li>
 *   <li>Email send: {@link EmailService} is blocking JavaMail → wrap with
 *       {@code Mono.fromRunnable(...).subscribeOn(Schedulers.boundedElastic())}.
 *       Caller doesn't wait — fire-and-forget that updates status later.</li>
 *   <li>In-app push: {@link InAppPushService} uses Reactor {@code Sinks.Many},
 *       non-blocking; emit inside the main reactive chain.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationApplicationService {

    private final NotificationRepository repository;
    private final ReactiveMongoTemplate mongoTemplate;
    private final EmailService emailService;
    private final InAppPushService inAppPushService;

    /**
     * Persists the notification, dispatches delivery by type, returns the final DTO.
     * Duplicate-key violations (idempotency via unique compound index) surface as
     * a lookup of the existing row — same semantics as the JPA version.
     */
    public Mono<NotificationDTO> send(SendNotificationRequest req) {
        var newDoc = NotificationDocument.builder()
                .userId(req.getUserId())
                .type(req.getType().name())
                .channel(req.getChannel())
                .title(req.getTitle())
                .content(req.getContent())
                .status(NotificationStatus.PENDING.name())
                .referenceType(req.getReferenceType())
                .referenceId(req.getReferenceId())
                .build();

        return repository.save(newDoc)
                .onErrorResume(DuplicateKeyException.class, ex -> {
                    log.info("duplicate notification suppressed userId={} refType={} refId={} type={}",
                            req.getUserId(), req.getReferenceType(), req.getReferenceId(), req.getType());
                    return repository.findByUserIdAndReferenceTypeAndReferenceIdAndType(
                            req.getUserId(), req.getReferenceType(), req.getReferenceId(),
                            req.getType().name());
                })
                .flatMap(saved -> dispatch(saved, req));
    }

    /** Route to the right delivery mechanism after the record is persisted. */
    private Mono<NotificationDTO> dispatch(NotificationDocument doc, SendNotificationRequest req) {
        return switch (req.getType()) {
            case EMAIL -> {
                triggerEmailAsync(doc, req.getChannel(), req.getTitle(), req.getContent());
                yield Mono.just(toDTO(doc));
            }
            case IN_APP -> markSent(doc)
                    .doOnNext(saved -> inAppPushService.push(req.getUserId(), toDTO(saved)))
                    .map(this::toDTO);
            default -> {
                // SMS / PUSH — mocked. Mark SENT immediately.
                log.info("{} notification not yet implemented, marking SENT for userId={}",
                        req.getType(), req.getUserId());
                yield markSent(doc).map(this::toDTO);
            }
        };
    }

    /**
     * Trigger email send + status update on the bounded-elastic scheduler so the
     * caller's reactive chain isn't blocked by SMTP latency. Errors are logged
     * and persisted as FAILED on the document.
     */
    private void triggerEmailAsync(NotificationDocument doc, String channel, String title, String content) {
        Mono.fromRunnable(() -> emailService.send(channel, title, content))
                .subscribeOn(Schedulers.boundedElastic())
                .then(updateEmailStatus(doc.getId(), NotificationStatus.SENT, null))
                .onErrorResume(ex -> {
                    log.error("Email notification id={} failed: {}", doc.getId(), ex.getMessage());
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    return updateEmailStatus(doc.getId(), NotificationStatus.FAILED, msg);
                })
                .subscribe();
    }

    private Mono<NotificationDocument> markSent(NotificationDocument doc) {
        doc.setStatus(NotificationStatus.SENT.name());
        doc.setSentAt(Instant.now());
        return repository.save(doc);
    }

    private Mono<Void> updateEmailStatus(String id, NotificationStatus status, String errorMsg) {
        return repository.findById(id)
                .flatMap(n -> {
                    n.setStatus(status.name());
                    if (status == NotificationStatus.SENT) n.setSentAt(Instant.now());
                    if (errorMsg != null) n.setErrorMessage(errorMsg);
                    return repository.save(n);
                })
                .then();
    }

    // ── reads ────────────────────────────────────────────────────────────────

    public Mono<PageDTO<NotificationDTO>> getMyNotifications(String userId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "_id"));
        Flux<NotificationDocument> items = repository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        Mono<Long> total = repository.countByUserIdAndIsRead(userId, true)
                .zipWith(repository.countByUserIdAndIsRead(userId, false), Long::sum);

        return Mono.zip(items.map(this::toDTO).collectList(), total,
                (list, count) -> {
                    int totalPages = (int) ((count + size - 1) / size);
                    return new PageDTO<>(list, page, size, count, totalPages,
                            (long) (page + 1) * size >= count);
                });
    }

    /**
     * Cursor pagination: items with {@code _id} less than the cursor, newest first.
     * First call: pass {@code cursor=null} (resolved to the max possible ObjectId).
     */
    public Mono<CursorPageDTO<NotificationDTO>> getMyFeed(String userId, String cursor, int size) {
        int clamped = Math.max(1, Math.min(size, 100));
        // ObjectId "ffffffffffffffffffffffff" is the highest possible — acts as "no upper bound".
        String safeCursor = (cursor == null || cursor.isBlank())
                ? "ffffffffffffffffffffffff" : cursor;

        return repository.findByUserIdAfterCursor(userId, safeCursor,
                        PageRequest.of(0, clamped + 1,
                                Sort.by(Sort.Direction.DESC, "_id")))
                .collectList()
                .map(fetched -> {
                    boolean hasNext = fetched.size() > clamped;
                    List<NotificationDocument> page = hasNext ? fetched.subList(0, clamped) : fetched;
                    List<NotificationDTO> items = page.stream().map(this::toDTO).toList();
                    String nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
                    return new CursorPageDTO<>(items, nextCursor, items.size(), hasNext);
                });
    }

    public Mono<Long> getUnreadCount(String userId) {
        return repository.countByUserIdAndIsRead(userId, false);
    }

    public Mono<NotificationDTO> getById(String id, String userId, boolean isAdmin) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(
                        "Notification not found: " + id)))
                .flatMap(doc -> (!isAdmin && !doc.getUserId().equals(userId))
                        ? Mono.error(new AccessDeniedException(
                                "Notification does not belong to this user"))
                        : Mono.just(doc))
                .map(this::toDTO);
    }

    public Mono<NotificationDTO> markAsRead(String id, String userId) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotificationNotFoundException(
                        "Notification not found: " + id)))
                .flatMap(doc -> {
                    if (!doc.getUserId().equals(userId)) {
                        return Mono.error(new AccessDeniedException(
                                "Notification does not belong to this user"));
                    }
                    doc.setRead(true);
                    doc.setReadAt(Instant.now());
                    if (NotificationStatus.SENT.name().equals(doc.getStatus())) {
                        doc.setStatus(NotificationStatus.READ.name());
                    }
                    return repository.save(doc).map(this::toDTO);
                });
    }

    /**
     * Bulk mark-all-as-read using {@link ReactiveMongoTemplate}. Reactive Mongo
     * doesn't have a {@code @Modifying} equivalent, so this is the canonical
     * way to do a server-side update on many documents in one round trip.
     */
    public Mono<Long> markAllReadForUser(String userId) {
        var now = Instant.now();
        var query = new Query(Criteria.where("userId").is(userId).and("isRead").is(false));
        var update = new Update()
                .set("isRead", true)
                .set("readAt", now)
                .set("updatedAt", now)
                .set("status", NotificationStatus.READ.name());

        return mongoTemplate.updateMulti(query, update, NotificationDocument.class)
                .map(result -> result.getModifiedCount());
    }

    // ── mapping ──────────────────────────────────────────────────────────────

    private NotificationDTO toDTO(NotificationDocument e) {
        return new NotificationDTO(
                e.getId(), e.getUserId(), e.getType(), e.getChannel(),
                e.getTitle(), e.getContent(), e.getStatus(), e.isRead(),
                e.getErrorMessage(), e.getReferenceType(), e.getReferenceId(),
                e.getCreatedAt(), e.getSentAt(), e.getReadAt());
    }
}
