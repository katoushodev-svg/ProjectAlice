package com.projectalice.backend.memory.application;

import java.util.Optional;
import java.util.List;

/** Conservative outcome before a durable Relation Review capability is available. */
public record MemoryRelationDecision(Status status, int candidateCount, Optional<RelationType> relationType,
        List<RelatedMemorySearchResult.Candidate> candidates) {
    public enum Status { NO_MATCH, EXACT_DUPLICATE, REVIEW_REQUIRED, SEARCH_INCOMPLETE, SEARCH_UNAVAILABLE }

    public MemoryRelationDecision {
        if (status == null) throw new NullPointerException("status must not be null");
        if (relationType == null) throw new NullPointerException("relationType must not be null");
        candidates = List.copyOf(java.util.Objects.requireNonNull(candidates, "candidates must not be null"));
        if (candidateCount < 0) throw new IllegalArgumentException("candidateCount must be >= 0");
        if (status == Status.NO_MATCH && candidateCount != 0) {
            throw new IllegalArgumentException("NO_MATCH requires zero candidates");
        }
        if (status == Status.EXACT_DUPLICATE && candidateCount < 1) {
            throw new IllegalArgumentException("EXACT_DUPLICATE requires a candidate");
        }
        boolean searchable = status == Status.NO_MATCH || status == Status.EXACT_DUPLICATE
                || status == Status.REVIEW_REQUIRED;
        if (searchable != relationType.isPresent()) {
            throw new IllegalArgumentException("relation type must be present only for complete decisions");
        }
        if (status == Status.NO_MATCH && relationType.orElseThrow() != RelationType.NONE
                || status == Status.EXACT_DUPLICATE && relationType.orElseThrow() != RelationType.EQUIVALENT
                || status == Status.REVIEW_REQUIRED && !requiresReview(relationType.orElseThrow())) {
            throw new IllegalArgumentException("relation type does not match conservative decision status");
        }
    }

    /** Compatibility constructor for status-only decisions; publication requires populated candidates. */
    public MemoryRelationDecision(Status status, int candidateCount, Optional<RelationType> relationType) {
        this(status, candidateCount, relationType, List.of());
    }

    private static boolean requiresReview(RelationType type) {
        return type == RelationType.COMPLEMENTARY_SAME_FACT || type == RelationType.SUPERSEDES
                || type == RelationType.CONFLICT || type == RelationType.UNCERTAIN;
    }
}
