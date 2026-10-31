package com.projectalice.backend.memory.application;

/** Signals that a previously validated relation target is missing or has changed. */
public final class RelationReviewCandidateChangedException extends RuntimeException {
    public RelationReviewCandidateChangedException() {
        super("Relation candidate changed during Review preparation");
    }
}
