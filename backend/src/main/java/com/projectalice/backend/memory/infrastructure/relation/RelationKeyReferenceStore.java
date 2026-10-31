package com.projectalice.backend.memory.infrastructure.relation;

/**
 * Persistence boundary for idempotent Relation Key Reference transitions.
 * Implementations must atomically update reference state and Key Control counts; none is wired yet.
 */
public interface RelationKeyReferenceStore {
    enum CreateResult { CREATED, SAME_REFERENCE_EXISTS, IDENTITY_CONFLICT }
    enum TransitionResult { UPDATED, ALREADY_AT_TARGET, NOT_FOUND, VERSION_OR_STATE_CONFLICT,
        REVIEW_LIFECYCLE_TRANSACTION_REQUIRED }
    enum ReleaseResult { RELEASED, ALREADY_RELEASED, NOT_FOUND, VERSION_OR_STATE_CONFLICT,
        REVIEW_LIFECYCLE_TRANSACTION_REQUIRED }

    /** Conditional Put on (keyVersion, reviewId); same identity with different metadata is conflict. */
    CreateResult createPreparingIfAbsent(RelationKeyReference reference);

    /** CAS only PREPARING→ACTIVE or PREPARING/ACTIVE→CLEANUP_PENDING with matching version. */
    TransitionResult transition(RelationKeyReference.Key identity, long expectedReviewVersion,
            RelationKeyReference.State expectedState, RelationKeyReference.State targetState);

    /**
     * Atomically requires the Review's cleanup-confirmed and unreleased conditions, decrements
     * Global/Key counts once, and deletes this reference. Implementations must return
     * ALREADY_RELEASED when the same Review lifecycle has already released its slot.
     */
    ReleaseResult releaseAfterCleanup(RelationKeyReference.Key identity, long expectedReviewVersion);
}
