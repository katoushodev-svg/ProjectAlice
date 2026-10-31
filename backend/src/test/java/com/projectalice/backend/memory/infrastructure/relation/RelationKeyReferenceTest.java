package com.projectalice.backend.memory.infrastructure.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RelationKeyReferenceTest {
    private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-10-29T12:00:00+09:00");
    private static final RelationKeyReference.Key KEY = new RelationKeyReference.Key(3,
            "8a1f8e2a-4b3c-4d5e-8f60-123456789abc");

    @Test
    void identityIsKeyVersionAndReviewIdOnly() {
        var reference = new RelationKeyReference(KEY, RelationKeyReference.State.PREPARING, 1, CREATED);
        assertEquals(3, reference.identity().keyVersion());
        assertEquals(KEY.reviewId(), reference.identity().reviewId());
        assertThrows(IllegalArgumentException.class, () -> new RelationKeyReference.Key(0, KEY.reviewId()));
        assertThrows(IllegalArgumentException.class, () -> new RelationKeyReference.Key(3, "not-an-id"));
    }

    @Test
    void preparingCanBecomeActiveOnceThenCleanupOnce() {
        var preparing = new RelationKeyReference(KEY, RelationKeyReference.State.PREPARING, 1, CREATED);
        var active = preparing.activate();
        assertEquals(RelationKeyReference.TransitionStatus.APPLIED, active.status());
        assertEquals(RelationKeyReference.State.ACTIVE, active.reference().state());
        assertEquals(RelationKeyReference.TransitionStatus.ALREADY_APPLIED,
                active.reference().activate().status());
        var cleanup = active.reference().beginCleanup();
        assertEquals(RelationKeyReference.TransitionStatus.APPLIED, cleanup.status());
        assertEquals(RelationKeyReference.TransitionStatus.ALREADY_APPLIED,
                cleanup.reference().beginCleanup().status());
    }

    @Test
    void releaseRequiresConfirmedCleanupAndCannotReleaseTwice() {
        var active = new RelationKeyReference(KEY, RelationKeyReference.State.ACTIVE, 1, CREATED);
        assertThrows(IllegalStateException.class, () -> active.releaseAfterCleanup(true));
        var cleanup = active.beginCleanup().reference();
        assertThrows(IllegalStateException.class, () -> cleanup.releaseAfterCleanup(false));
        var released = cleanup.releaseAfterCleanup(true);
        assertEquals(RelationKeyReference.TransitionStatus.APPLIED, released.status());
        assertEquals(RelationKeyReference.State.RELEASED, released.reference().state());
        assertEquals(RelationKeyReference.TransitionStatus.ALREADY_APPLIED,
                released.reference().releaseAfterCleanup(true).status());
    }

    @Test
    void cleanupCannotMoveBackwardToActive() {
        var cleanup = new RelationKeyReference(KEY, RelationKeyReference.State.CLEANUP_PENDING, 1, CREATED);
        assertThrows(IllegalStateException.class, cleanup::activate);
    }
}
