package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.util.List;
import java.util.Objects;

/** Plaintext, in-process handoff model. It must be AEAD-encrypted before any persistence. */
public record RelationReviewDraft(RelationType relationType, MemoryContent proposedContent,
        MemoryCategory proposedCategory, SensitivityLevel proposedSensitivityLevel, List<Target> targets) {
    public RelationReviewDraft {
        Objects.requireNonNull(relationType, "relationType must not be null");
        Objects.requireNonNull(proposedContent, "proposedContent must not be null");
        Objects.requireNonNull(proposedCategory, "proposedCategory must not be null");
        Objects.requireNonNull(proposedSensitivityLevel, "proposedSensitivityLevel must not be null");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets must not be null"));
        if (targets.isEmpty() || targets.size() > 100) throw new IllegalArgumentException("target count must be 1..100");
        if (targets.stream().map(t -> t.snapshot().memoryId()).distinct().count() != targets.size())
            throw new IllegalArgumentException("Review targets must be unique");
        if (targets.stream().anyMatch(t -> !t.preview().targetMemoryId().equals(t.snapshot().memoryId())
                || t.preview().expectedTargetVersion() != t.snapshot().version()
                || t.preview().relationType() != relationType))
            throw new IllegalArgumentException("Mutation Preview must bind its authoritative target snapshot");
    }

    public record Target(RelatedMemorySearchResult.Candidate snapshot, RelationMutationPreview preview) {
        public Target {
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            Objects.requireNonNull(preview, "preview must not be null");
        }
    }
}
