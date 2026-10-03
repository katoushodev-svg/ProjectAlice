package com.projectalice.backend.memory.application;

import com.projectalice.backend.memory.domain.MemoryId;
import java.util.Objects;
import java.util.Set;

/** A search hint only; it deliberately carries no memory content. */
public record MemorySearchCandidate(MemoryId memoryId, long version, Set<MemorySearchSignal> signals) {

    public MemorySearchCandidate {
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        signals = signals == null ? Set.of() : Set.copyOf(signals);
    }
}
