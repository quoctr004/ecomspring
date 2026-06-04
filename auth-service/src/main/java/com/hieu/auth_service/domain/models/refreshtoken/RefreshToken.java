package com.hieu.auth_service.domain.models.refreshtoken;

import java.time.Instant;
import java.util.Objects;

import com.hieu.auth_service.domain.models.refreshtoken.events.TokenCreatedEvent;
import com.hieu.auth_service.domain.models.refreshtoken.events.TokenRevokedEvent;
import com.hieu.auth_service.domain.models.refreshtoken.events.TokenRotatedEvent;
import com.hieu.auth_service.domain.models.refreshtoken.exceptions.TokenExpiredException;
import com.hieu.auth_service.domain.models.refreshtoken.exceptions.TokenRevokedException;
import com.hieu.auth_service.domain.models.refreshtoken.vo.GenerationNumber;
import com.hieu.auth_service.domain.models.refreshtoken.vo.RevokedReason;
import com.hieu.auth_service.domain.models.refreshtoken.vo.TokenExpiry;
import com.hieu.auth_service.domain.models.refreshtoken.vo.TokenFamily;
import com.hieu.auth_service.domain.models.refreshtoken.vo.TokenId;
import com.hieu.auth_service.domain.models.refreshtoken.vo.TokenValue;
import com.hieu.auth_service.domain.models.user.vo.UserId;
import com.hieu.auth_service.domain.shared.AggregateRoot;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * RefreshToken aggregate root.
 *
 * <p>Implements the <b>Refresh-Token Rotation + Family Revocation</b> pattern:
 * <ul>
 *   <li>Each login issues a root token (generation 0) with a fresh family id.</li>
 *   <li>On every refresh the presented token is revoked and a new token is issued
 *       in the same family with generation = old + 1.</li>
 *   <li>If an already-revoked token is re-presented, {@link #isReuseAttempt()} flags
 *       theft and the {@code TokenDomainService} cascades a family revocation.</li>
 * </ul>
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(of = {"id", "userId", "family", "generation", "expiry", "revoked"})
public final class RefreshToken extends AggregateRoot {

    @EqualsAndHashCode.Include
    private TokenId id;
    private TokenValue value;
    private UserId userId;
    private TokenFamily family;
    private GenerationNumber generation;
    private TokenExpiry expiry;
    private boolean revoked;
    private RevokedReason reason;
    private Instant createdAt;
    private Instant revokedAt;

    private RefreshToken() {
        this.revoked = false;
    }

    // ── Factories ──────────────────────────────────────────────────────────

    /** Root token for a brand-new login session (generation 0, new family). */
    public static RefreshToken create(UserId userId, int expiryDays) {
        RefreshToken t = new RefreshToken();
        t.id = TokenId.generate();
        t.value = TokenValue.generate();
        t.userId = userId;
        t.family = TokenFamily.generate();
        t.generation = GenerationNumber.root();
        t.expiry = TokenExpiry.fromDaysFromNow(expiryDays);
        t.createdAt = Instant.now();

        t.registerEvent(new TokenCreatedEvent(
                t.id.value(), userId.value(), t.family.value(),
                t.generation.value(), t.expiry.expiryDate()));
        return t;
    }

    /** Next-generation token sharing the old token's family. */
    public static RefreshToken rotate(RefreshToken old, int expiryDays) {
        RefreshToken t = new RefreshToken();
        t.id = TokenId.generate();
        t.value = TokenValue.generate();
        t.userId = old.userId;
        t.family = old.family;
        t.generation = old.generation.next();
        t.expiry = TokenExpiry.fromDaysFromNow(expiryDays);
        t.createdAt = Instant.now();

        t.registerEvent(new TokenRotatedEvent(
                old.userId.value(), old.family.value(),
                old.id.value(), t.id.value(), t.generation.value()));
        return t;
    }

    /** Rebuilds aggregate state from persistence. Domain events are intentionally NOT replayed. */
    public static RefreshToken reconstitute(TokenId id, TokenValue value, UserId userId,
                                            TokenExpiry expiry, boolean revoked,
                                            Instant createdAt, Instant revokedAt,
                                            TokenFamily family, GenerationNumber generation,
                                            RevokedReason reason) {
        RefreshToken t = new RefreshToken();
        t.id = id;
        t.value = value;
        t.userId = userId;
        t.expiry = expiry;
        t.revoked = revoked;
        t.createdAt = createdAt;
        t.revokedAt = revokedAt;
        t.family = family;
        t.generation = generation;
        t.reason = reason;
        return t;
    }

    // ── Validation ────────────────────────────────────────────────────────

    /** Throws when the token cannot be used — distinct exceptions for revoked vs expired. */
    public void verifyValidity() {
        if (revoked)          throw new TokenRevokedException(id.value(), reason);
        if (expiry.isExpired()) throw new TokenExpiredException(id.value());
    }

    public boolean isValid()          { return !revoked && !expiry.isExpired(); }
    public boolean isRevoked()        { return revoked; }
    // Re-presenting ANY already-revoked token at rotation time is the theft signal in the
    // rotation pattern: a legitimate client only ever holds the latest (non-revoked) token,
    // so a revoked one resurfacing — including one revoked by NORMAL rotation — means it was
    // captured and replayed. TokenDomainService cascades a family revocation in response.
    public boolean isReuseAttempt()   { return revoked; }
    public boolean belongsTo(UserId u){ return userId.equals(u); }
    public boolean willExpireSoon(long seconds) { return expiry.willExpireWithin(seconds); }

    public long getRemainingSeconds() { return isValid() ? expiry.getRemainingSeconds() : 0L; }

    public boolean wasRevokedForSecurity() {
        return reason != null && reason.isSecurityRelated();
    }

    // ── State transitions ─────────────────────────────────────────────────

    /** Idempotent revoke. */
    public void revoke(RevokedReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (revoked) return;
        this.revoked = true;
        this.revokedAt = Instant.now();
        this.reason = reason;

        registerEvent(new TokenRevokedEvent(
                id != null ? id.value() : null,
                userId.value(),
                family != null ? family.value() : null,
                reason.value()));
    }

    /** Shortcut for user-initiated / normal rotation  revocations. */
    public void revoke() {
        revoke(RevokedReason.NORMAL);
    }
}
