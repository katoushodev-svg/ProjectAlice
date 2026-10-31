package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryContent;
import com.projectalice.backend.memory.domain.MemoryCategory;
import com.projectalice.backend.memory.domain.MemoryId;
import com.projectalice.backend.memory.domain.MemoryState;
import com.projectalice.backend.memory.domain.CaptureType;
import com.projectalice.backend.memory.domain.SensitivityLevel;
import java.util.List;
import java.util.Objects;

/** Authoritatively validated Relation candidates; Content remains inside the application flow. */
public record RelatedMemorySearchResult(SearchCompleteness completeness, List<Candidate> candidates) {
    public RelatedMemorySearchResult {
        Objects.requireNonNull(completeness, "completeness must not be null");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        if (completeness == SearchCompleteness.UNAVAILABLE && !candidates.isEmpty()) {
            throw new IllegalArgumentException("unavailable relation result must not contain candidates");
        }
    }

    public record Candidate(MemoryId memoryId, long version, MemoryContent content,
            MemoryCategory category, CaptureType captureType, SensitivityLevel sensitivityLevel,
            MemoryState state) {
        public Candidate {
            Objects.requireNonNull(memoryId, "memoryId must not be null");
            if (version < 1) throw new IllegalArgumentException("version must be >= 1");
            Objects.requireNonNull(content, "content must not be null");
            Objects.requireNonNull(category, "category must not be null");
            Objects.requireNonNull(captureType, "captureType must not be null");
            Objects.requireNonNull(sensitivityLevel, "sensitivityLevel must not be null");
            Objects.requireNonNull(state, "state must not be null");
        }
    }
}
