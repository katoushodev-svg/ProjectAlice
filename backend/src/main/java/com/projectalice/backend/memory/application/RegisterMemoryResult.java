package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.PersonalMemory;
import java.util.Objects;

public record RegisterMemoryResult(Status status, PersonalMemory memory, int relationCandidateCount,
        java.util.Optional<RelationReviewDraft> reviewDraft) {
    public enum Status {
        SUCCESS,
        NO_CHANGE,
        RELATION_REVIEW_REQUIRED,
        RELATION_SEARCH_INCOMPLETE,
        RELATION_SEARCH_UNAVAILABLE
    }
    public RegisterMemoryResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(reviewDraft, "reviewDraft must not be null");
        if (relationCandidateCount < 0) throw new IllegalArgumentException("relationCandidateCount must be >= 0");
        if (status == Status.SUCCESS) Objects.requireNonNull(memory, "memory must not be null for SUCCESS");
        else if (memory != null) throw new IllegalArgumentException("non-success result must not contain a memory");
        if ((status == Status.RELATION_REVIEW_REQUIRED) != reviewDraft.isPresent())
            throw new IllegalArgumentException("Review-required result must carry its transient draft");
    }
    public RegisterMemoryResult(Status status, PersonalMemory memory, int relationCandidateCount) {
        this(status, memory, relationCandidateCount, java.util.Optional.empty());
    }
    public static RegisterMemoryResult success(PersonalMemory memory) {
        return new RegisterMemoryResult(Status.SUCCESS, Objects.requireNonNull(memory), 0, java.util.Optional.empty());
    }
    public static RegisterMemoryResult relation(Status status, int candidateCount) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("relation status must not be SUCCESS");
        return new RegisterMemoryResult(status, null, candidateCount, java.util.Optional.empty());
    }
    public static RegisterMemoryResult reviewRequired(RelationReviewDraft draft) {
        RelationReviewDraft required = Objects.requireNonNull(draft, "draft must not be null");
        return new RegisterMemoryResult(Status.RELATION_REVIEW_REQUIRED, null, required.targets().size(),
                java.util.Optional.of(required));
    }
}
