package com.projectalice.backend.memory.application;

/** Raised when a validated Review draft cannot be atomically published with its idempotency Binding. */
public final class RelationReviewPublicationUnavailableException extends RuntimeException {
    public RelationReviewPublicationUnavailableException() {
        super("Relation Review publication is unavailable");
    }
}
