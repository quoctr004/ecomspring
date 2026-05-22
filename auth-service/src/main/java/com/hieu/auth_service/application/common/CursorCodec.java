package com.hieu.auth_service.application.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Opaque cursor codec for keyset pagination.
 *
 * <p>Encodes {@code (createdAt, id)} as a URL-safe base64 string so clients treat it
 * as opaque state. Format on the wire is {@code base64("<epochMicros>|<id>")} — backend
 * decoder rejects anything that doesn't parse cleanly, so clients cannot forge cursors
 * that skip ahead in the dataset.
 *
 * <p>Using epoch micros (not millis) preserves ordering even when entities are created
 * in the same millisecond — Postgres stores timestamps at microsecond resolution.
 */
public final class CursorCodec {

    private static final String DELIM = "|";

    private CursorCodec() {}

    /**
     * Decoded pair — both fields are non-null and validated.
     *
     * @param createdAt anchor timestamp of the last row on the previous page
     * @param id        anchor id (UUID String) for tie-breaking within the same timestamp
     */
    public record Cursor(Instant createdAt, String id) {
        public Cursor {
            Objects.requireNonNull(createdAt, "createdAt");
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("cursor id must be non-blank");
            }
        }
    }

    /**
     * Encodes a cursor tuple into a URL-safe base64 string.
     *
     * @param createdAt anchor timestamp
     * @param id        anchor id
     * @return opaque base64 string, or {@code null} when either component is null
     */
    public static String encode(Instant createdAt, String id) {
        if (createdAt == null || id == null) return null;
        long epochMicros = createdAt.getEpochSecond() * 1_000_000L + createdAt.getNano() / 1_000;
        String raw = epochMicros + DELIM + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    } 
    /**
     * Decodes an opaque cursor. Returns {@code null} for blank input (first page).
     *
     * @param opaque base64 cursor produced by {@link #encode}
     * @return parsed cursor, or {@code null} when input is null/blank
     * @throws IllegalArgumentException when the cursor is malformed (prevents forgery)
     */
    public static Cursor decode(String opaque) {
        if (opaque == null || opaque.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(opaque), StandardCharsets.UTF_8);
            int split = raw.indexOf(DELIM);
            if (split < 0) throw new IllegalArgumentException("missing delimiter");

            long epochMicros = Long.parseLong(raw.substring(0, split));
            String id = raw.substring(split + DELIM.length());
            Instant ts = Instant.ofEpochSecond(
                    epochMicros / 1_000_000L,
                    (epochMicros % 1_000_000L) * 1_000L);
            return new Cursor(ts, id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cursor: " + e.getMessage(), e);
        }
    }
}
