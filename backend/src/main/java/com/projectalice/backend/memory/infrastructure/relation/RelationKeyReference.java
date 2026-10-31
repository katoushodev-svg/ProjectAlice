package com.projectalice.backend.memory.infrastructure.relation;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Content-free relation-key reference state; identity is exactly (keyVersion, reviewId). */
public record RelationKeyReference(
        Key identity,
        State state,
        long reviewVersion,
        OffsetDateTime createdAt) {
    public enum State { PREPARING, ACTIVE, CLEANUP_PENDING, RELEASED }
    public enum TransitionStatus { APPLIED, ALREADY_APPLIED }
    public record Transition(TransitionStatus status, RelationKeyReference reference) {
        public Transition {
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(reference, "reference must not be null");
        }
    }
    public record Key(int keyVersion, String reviewId) {
        public Key {
            if (keyVersion < 1) throw new IllegalArgumentException("keyVersion must be positive");
            Objects.requireNonNull(reviewId, "reviewId must not be null");
            try { reviewId = UUID.fromString(reviewId).toString(); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("reviewId must be a UUID"); }
        }
    }

    public RelationKeyReference {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(state, "state must not be null");
        if (reviewVersion < 1) throw new IllegalArgumentException("reviewVersion must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public Transition activate() {
        if (state == State.ACTIVE) return new Transition(TransitionStatus.ALREADY_APPLIED, this);
        if (state != State.PREPARING) throw new IllegalStateException("invalid reference transition");
        return new Transition(TransitionStatus.APPLIED,
                new RelationKeyReference(identity, State.ACTIVE, reviewVersion, createdAt));
    }

    public Transition beginCleanup() {
        if (state == State.CLEANUP_PENDING || state == State.RELEASED)
            return new Transition(TransitionStatus.ALREADY_APPLIED, this);
        return new Transition(TransitionStatus.APPLIED,
                new RelationKeyReference(identity, State.CLEANUP_PENDING, reviewVersion, createdAt));
    }

    /** Release follows confirmed ciphertext cleanup; persistence must CAS and decrement counts atomically. */
    public Transition releaseAfterCleanup(boolean ciphertextCleanupConfirmed) {
        if (state == State.RELEASED) return new Transition(TransitionStatus.ALREADY_APPLIED, this);
        if (state != State.CLEANUP_PENDING || !ciphertextCleanupConfirmed)
            throw new IllegalStateException("reference release requires confirmed cleanup");
        return new Transition(TransitionStatus.APPLIED,
                new RelationKeyReference(identity, State.RELEASED, reviewVersion, createdAt));
    }
}
