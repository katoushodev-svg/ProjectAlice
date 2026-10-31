package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.util.Objects;

/**
 * Immutable proposed replacement for one authoritative target. This is an in-memory contract;
 * callers must persist it only inside the encrypted Relation Review state.
 */
public record RelationMutationPreview(
        Operation operation,
        RelationType relationType,
        MemoryId targetMemoryId,
        long expectedTargetVersion,
        MemoryContent proposedContent,
        MemoryCategory proposedCategory,
        SensitivityLevel proposedSensitivityLevel) {

    public enum Operation { UPDATE_TARGET }

    public RelationMutationPreview {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(relationType, "relationType must not be null");
        Objects.requireNonNull(targetMemoryId, "targetMemoryId must not be null");
        if (expectedTargetVersion < 1) throw new IllegalArgumentException("expectedTargetVersion must be >= 1");
        Objects.requireNonNull(proposedContent, "proposedContent must not be null");
        Objects.requireNonNull(proposedCategory, "proposedCategory must not be null");
        Objects.requireNonNull(proposedSensitivityLevel, "proposedSensitivityLevel must not be null");
        if (!requiresReview(relationType)) {
            throw new IllegalArgumentException("UPDATE_TARGET preview requires a review Relation Type");
        }
    }

    /** Builds a registration preview only from a complete search and a validated target snapshot. */
    public static RelationMutationPreview forRegister(
            SearchCompleteness completeness,
            RelationType relationType,
            RelatedMemorySearchResult.Candidate target,
            RegisterMemoryCommand candidate) {
        if (completeness != SearchCompleteness.COMPLETE) {
            throw new IllegalArgumentException("preview requires a complete Relation search");
        }
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        return new RelationMutationPreview(Operation.UPDATE_TARGET, relationType,
                target.memoryId(), target.version(), candidate.content(), candidate.category(),
                candidate.sensitivityLevel());
    }

    private static boolean requiresReview(RelationType type) {
        return type == RelationType.COMPLEMENTARY_SAME_FACT || type == RelationType.SUPERSEDES
                || type == RelationType.CONFLICT || type == RelationType.UNCERTAIN;
    }
}
