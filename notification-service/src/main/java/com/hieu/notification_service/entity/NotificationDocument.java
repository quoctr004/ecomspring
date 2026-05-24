package com.hieu.notification_service.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * MongoDB document representing a single notification (any channel).
 *
 * <p>Replaces the previous {@code NotificationJpaEntity} as part of the
 * notification-service migration to WebFlux + Reactive MongoDB. Mongo fits
 * notifications naturally:
 * <ul>
 *   <li>Schema-flexible — new channel-specific fields can land without ALTER TABLE.</li>
 *   <li>Write-heavy, single-document reads — no joins needed.</li>
 *   <li>Time-series read pattern (newest first by user) maps to a compound index.</li>
 * </ul>
 *
 * <h2>Indexes</h2>
 * <ul>
 *   <li>{@code (userId, createdAt DESC)} — main feed read pattern.</li>
 *   <li>{@code (userId, isRead)} — unread-count badge query.</li>
 *   <li>Unique {@code (userId, referenceType, referenceId, type)} — idempotent
 *       Kafka consumers won't double-insert when a message redelivers.</li>
 * </ul>
 *
 * <p>{@link #id} is a {@code String} (Mongo {@code ObjectId} as hex). The
 * previous JPA model used a Postgres-generated {@code Long}; this is a
 * <b>breaking change for any external persisted ID references</b>. Frontend +
 * other services treat IDs as opaque strings, so the swap is contained here.
 */
@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "userId_createdAt_desc", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "userId_isRead",         def = "{'userId': 1, 'isRead': 1}"),
        // The dedup index also accelerates lookups inside Kafka consumers.
        @CompoundIndex(name = "dedup_unique",
                def = "{'userId': 1, 'referenceType': 1, 'referenceId': 1, 'type': 1}",
                unique = true,
                sparse = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDocument {

    @Id
    private String id;

    /** Auth UUID string for the target user. */
    @Indexed
    private String userId;

    /** EMAIL | SMS | IN_APP | PUSH */
    private String type;

    /** Email address / phone / device token — null for IN_APP. */
    private String channel;

    private String title;

    private String content;

    /** PENDING | SENT | FAILED | READ */
    private String status;

    @Field("is_read")
    @Builder.Default
    private boolean isRead = false;

    @Field("error_message")
    private String errorMessage;

    @Field("reference_type")
    private String referenceType;

    @Field("reference_id")
    private String referenceId;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;

    @Field("sent_at")
    private Instant sentAt;

    @Field("read_at")
    private Instant readAt;
}
