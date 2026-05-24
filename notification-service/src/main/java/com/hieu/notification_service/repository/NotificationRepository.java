package com.hieu.notification_service.repository;

import com.hieu.notification_service.entity.NotificationDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive MongoDB repository for notifications.
 *
 * <p>Replaces the previous {@code JpaRepository} after the WebFlux + Reactive
 * MongoDB migration. {@code Page<>} pagination is dropped in favour of:
 * <ul>
 *   <li>{@link Flux} streams for the feed (caller paginates with {@link Pageable}
 *       skip/limit, or uses the cursor variant for stable scrolling).</li>
 *   <li>{@link Mono Mono<Long>} counts so the front-end can compose
 *       {@code (items, total)} without round-trip blocking.</li>
 * </ul>
 *
 * <p>Bulk operations (mark-all-as-read) live in a dedicated service method
 * using {@code ReactiveMongoTemplate} because reactive Mongo doesn't surface
 * derived-update keywords the way JPA does.
 */
public interface NotificationRepository extends ReactiveMongoRepository<NotificationDocument, String> {

    /** Newest-first feed for a user. Backed by {@code (userId, createdAt DESC)} index. */
    Flux<NotificationDocument> findByUserIdOrderByCreatedAtDescIdDesc(String userId, Pageable pageable);

    /**
     * Cursor pagination — return items whose Mongo {@code _id} is lexicographically
     * less than the cursor, newest first. Stable under concurrent inserts (unlike
     * skip/limit, which can skip items added since the previous page).
     *
     * <p>Mongo ObjectId comparison gives "newest first" implicitly because the
     * first 4 bytes of an ObjectId are a Unix timestamp.
     */
    @Query("{ 'userId': ?0, '_id': { $lt: ?1 } }")
    Flux<NotificationDocument> findByUserIdAfterCursor(String userId, String cursor, Pageable pageable);

    /** Unread-count badge — backed by the {@code (userId, isRead)} index. */
    Mono<Long> countByUserIdAndIsRead(String userId, boolean isRead);

    /**
     * Idempotency lookup — same (user, reference, type) tuple should never produce
     * a second notification. Backed by the unique sparse compound index.
     */
    Mono<NotificationDocument> findByUserIdAndReferenceTypeAndReferenceIdAndType(
            String userId, String referenceType, String referenceId, String type);
}
