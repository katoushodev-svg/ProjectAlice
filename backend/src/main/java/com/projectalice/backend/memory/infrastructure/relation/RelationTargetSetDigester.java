package com.projectalice.backend.memory.infrastructure.relation;

import com.projectalice.backend.memory.application.RelationReviewDraft;
import java.util.List;

/** Purpose-separated keyed digest over the ordered, content-free target identity/version set. */
public interface RelationTargetSetDigester {
    String digestDraft(RelationReviewDraft draft);
    String digestTargets(List<TargetIdentity> targets);

    record TargetIdentity(int ordinal, String memoryId, long expectedVersion) {
        public TargetIdentity {
            if (ordinal < 0 || ordinal >= 100) throw new IllegalArgumentException("invalid target ordinal");
            if (memoryId == null || memoryId.isBlank()) throw new IllegalArgumentException("memoryId required");
            if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        }
    }
}
